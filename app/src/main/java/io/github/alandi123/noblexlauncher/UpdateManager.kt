package io.github.alandi123.noblexlauncher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException
import java.util.concurrent.Executors

data class LauncherRelease(
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val releaseDate: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    val supportedAbis: List<String>,
)

object UpdateManager {
    private const val MANIFEST_ASSET_NAME = "update-manifest.json"
    private const val NETWORK_TIMEOUT_MS = 15_000
    private const val MAX_REDIRECTS = 5
    private const val MAX_JSON_BYTES = 512 * 1024
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var installing = false

    fun cache(context: Context) = UpdateCache(File(context.cacheDir, "updates"))

    @Suppress("DEPRECATION")
    private fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun checkLatest(context: Context, callback: (Result<LauncherRelease>) -> Unit) {
        executor.execute {
            callback(captureResult { fetchLatestBlocking() })
        }
    }

    /** Returns the newest official release, validated against the manifest rules. Blocks the caller. */
    fun fetchLatestBlocking(): LauncherRelease {
        val release = getJson(ReleaseUrlPolicy.LATEST_RELEASE_API) { it == ReleaseUrlPolicy.LATEST_RELEASE_API }
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
            throw IOException("Aún no hay una versión pública disponible.")
        }
        val releaseTag = release.optString("tag_name")
        val assets = release.optJSONArray("assets")
        val manifestAsset = assets
            ?.let { (0 until it.length()).map(it::getJSONObject) }
            ?.firstOrNull { it.optString("name") == MANIFEST_ASSET_NAME }
            ?: throw IOException("La publicación no incluye $MANIFEST_ASSET_NAME.")
        val manifestUrl = manifestAsset.optString("browser_download_url")
        if (!ReleaseUrlPolicy.isOfficialAsset(manifestUrl, releaseTag, MANIFEST_ASSET_NAME)) {
            throw IOException("La URL del manifiesto no pertenece a la publicación oficial.")
        }
        val manifest = getJson(manifestUrl) { ReleaseUrlPolicy.isAllowedRedirect(it) }
        return UpdateManifestParser.parse(
            manifest = manifest,
            releaseTag = releaseTag,
            applicationId = BuildConfig.APPLICATION_ID,
            sdkInt = Build.VERSION.SDK_INT,
            deviceAbis = Build.SUPPORTED_ABIS.toList(),
        )
    }

    fun cleanupCache(context: Context) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                cache(appContext).cleanup(installedVersionCode(appContext))
            } catch (_: PackageManager.NameNotFoundException) {
                // The package manager always knows this app; nothing to clean otherwise.
            }
        }
    }

    fun downloadAndInstall(
        context: Context,
        release: LauncherRelease,
        onProgress: (Int) -> Unit,
        callback: (Result<Unit>) -> Unit,
    ) {
        executor.execute {
            callback(captureResult {
                val appContext = context.applicationContext
                if (installing) throw IOException("Ya hay una actualización en curso.")
                installing = true
                try {
                    prepareAndInstall(appContext, release, onProgress)
                } finally {
                    installing = false
                }
            })
        }
    }

    private fun prepareAndInstall(context: Context, release: LauncherRelease, onProgress: (Int) -> Unit) {
        val installedCode = installedVersionCode(context)
        if (UpdatePolicy.decide(release, BuildConfig.VERSION_NAME, installedCode) != UpdateDecision.AVAILABLE) {
            throw IOException("NOBLEX LAUNCHER ya está actualizado.")
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            throw IOException("Autoriza instalaciones para NOBLEX LAUNCHER y vuelve a comprobar la actualización.")
        }

        val cache = cache(context)
        cache.prepare()
        cache.cleanup(installedCode)
        val part = cache.partFor(release.versionCode)
        val apk = cache.apkFor(release.versionCode)
        var verified = false
        try {
            download(release, part, onProgress)
            UpdateVerifier.requireChecksum(part, release.sha256)
            if (apk.exists() && !apk.delete()) throw IOException("No se pudo reemplazar el APK en caché.")
            if (!part.renameTo(apk)) throw IOException("No se pudo guardar el APK descargado.")
            verifyArchive(context, apk, release)
            verified = true
            commitInstall(context, apk)
        } finally {
            part.delete()
            if (!verified) apk.delete()
        }
    }

    @Suppress("DEPRECATION")
    private fun verifyArchive(context: Context, apk: File, release: LauncherRelease) {
        val packageManager = context.packageManager
        val archive = packageManager.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("El APK descargado no es válido.")
        UpdateVerifier.requireIdentity(
            archive.packageName, archive.longVersionCode, archive.versionName, BuildConfig.APPLICATION_ID, release,
        )
        val installed = packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        UpdateVerifier.requireSameSigner(signerDigests(installed), candidateSignerDigests(archive))
    }

    private fun signerDigests(info: PackageInfo): Set<String> =
        info.signingInfo?.apkContentsSigners.orEmpty().map { UpdateVerifier.digest(it.toByteArray()) }.toSet()

    private fun candidateSignerDigests(info: PackageInfo): Set<String> =
        info.signingInfo?.signingCertificateHistory.orEmpty().map { UpdateVerifier.digest(it.toByteArray()) }.toSet()

    private fun commitInstall(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(BuildConfig.APPLICATION_ID)
            setSize(apk.length())
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val statusIntent = Intent(context, InstallStatusReceiver::class.java)
                    .setAction(InstallStatusReceiver.ACTION)
                val pending = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (error: IOException) {
            installer.abandonSession(sessionId)
            throw error
        } catch (error: SecurityException) {
            installer.abandonSession(sessionId)
            throw error
        }
    }

    private fun getJson(address: String, allowed: (String) -> Boolean): JSONObject {
        val connection = open(address, allowed, "application/vnd.github+json")
        return connection.useConnection {
            val status = responseCode
            if (status !in 200..299) {
                throw IOException(
                    if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                        "Aún no hay una versión pública disponible."
                    } else {
                        "GitHub respondió con HTTP $status."
                    },
                )
            }
            val bytes = inputStream.use { it.readNBytesLimited(MAX_JSON_BYTES) }
            JSONObject(String(bytes, Charsets.UTF_8))
        }
    }

    private fun download(release: LauncherRelease, destination: File, onProgress: (Int) -> Unit) {
        val connection = open(release.apkUrl, { ReleaseUrlPolicy.isAllowedRedirect(it) }, "application/octet-stream")
        connection.useConnection {
            if (responseCode !in 200..299) throw IOException("No se pudo descargar el APK (HTTP $responseCode).")
            val declared = contentLengthLong
            UpdateVerifier.requireDeclaredSize(declared, release.sizeBytes)
            inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    var lastPercent = -1
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        UpdateVerifier.requireDownloadedSize(downloaded, release.sizeBytes)
                        output.write(buffer, 0, read)
                        val percent = ((downloaded * 100) / release.sizeBytes).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                    UpdateVerifier.requireComplete(downloaded, release.sizeBytes)
                }
            }
        }
    }

    /** Follows redirects manually so that every hop can be checked against the allow-list. */
    private fun open(address: String, allowed: (String) -> Boolean, accept: String): HttpURLConnection {
        var current = address
        repeat(MAX_REDIRECTS + 1) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.connectTimeout = NETWORK_TIMEOUT_MS
            connection.readTimeout = NETWORK_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", accept)
            connection.setRequestProperty("User-Agent", "NOBLEX-LAUNCHER/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status in 301..303 || status == 307 || status == 308) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                val next = location?.let { URI(current).resolve(it).toString() }
                if (next == null || !ReleaseUrlPolicy.isAllowedRedirect(next)) {
                    throw IOException("GitHub redirigió a un destino no permitido.")
                }
                current = next
            } else {
                return connection
            }
        }
        throw IOException("Demasiadas redirecciones.")
    }

    private fun java.io.InputStream.readNBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (out.size() + read > limit) throw IOException("La respuesta de GitHub es demasiado grande.")
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    private inline fun <T> HttpURLConnection.useConnection(block: HttpURLConnection.() -> T): T =
        try {
            block()
        } finally {
            disconnect()
        }

    private inline fun <T> captureResult(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: UnknownHostException) {
            Result.failure(IOException("Sin conexión a Internet.", error))
        } catch (error: SocketTimeoutException) {
            Result.failure(IOException("GitHub tardó demasiado en responder.", error))
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: JSONException) {
            Result.failure(IOException("El manifiesto de actualización no tiene un formato válido.", error))
        } catch (error: SecurityException) {
            Result.failure(IOException("Android no autorizó el acceso necesario para instalar la actualización.", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IOException("La actualización tiene datos de instalación inválidos.", error))
        }
}
