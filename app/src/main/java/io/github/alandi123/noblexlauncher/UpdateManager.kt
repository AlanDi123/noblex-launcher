package io.github.alandi123.noblexlauncher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class LauncherRelease(
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val apkUrl: String,
    val sha256: String,
    val releaseDate: String,
    val releaseNotes: String,
    val mandatory: Boolean,
    val supportedAbis: List<String>,
)

object UpdateManager {
    private const val RELEASE_API =
        "https://api.github.com/repos/AlanDi123/noblex-launcher/releases/latest"
    private const val MANIFEST_ASSET_NAME = "update-manifest.json"
    private val executor = Executors.newSingleThreadExecutor()

    fun checkLatest(context: Context, callback: (Result<LauncherRelease>) -> Unit) {
        executor.execute {
            callback(captureResult { fetchLatestBlocking(context.applicationContext) })
        }
    }

    fun fetchLatestBlocking(context: Context): LauncherRelease {
        val release = getJson(RELEASE_API)
        val releaseTag = release.optString("tag_name")
        val manifestAsset = release.optJSONArray("assets")
            ?.let { assets ->
                (0 until assets.length())
                    .map(assets::getJSONObject)
                    .firstOrNull { it.optString("name") == MANIFEST_ASSET_NAME }
            }
            ?: throw IOException("La publicación no incluye $MANIFEST_ASSET_NAME.")
        val manifestUrl = manifestAsset.optString("browser_download_url")
        requireReleaseUrl(manifestUrl, releaseTag, MANIFEST_ASSET_NAME)
        val manifest = getJson(manifestUrl)
        val parsed = UpdateManifestParser.parse(
            manifest = manifest,
            releaseTag = releaseTag,
            applicationId = BuildConfig.APPLICATION_ID,
            sdkInt = Build.VERSION.SDK_INT,
            deviceAbis = Build.SUPPORTED_ABIS.toList(),
        )
        return parsed.copy(releaseNotes = parsed.releaseNotes.ifBlank { release.optString("body") })
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
                val updateDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
                val apk = File(updateDir, "noblex-launcher-${release.versionCode}.apk")
                download(release.apkUrl, apk, onProgress)
                val actualHash = sha256(apk)
                if (!actualHash.equals(release.sha256, ignoreCase = true)) {
                    apk.delete()
                    throw IOException("La verificación SHA-256 de la descarga falló.")
                }

                @Suppress("DEPRECATION")
                val packageInfo = appContext.packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
                    ?: throw IOException("El APK descargado no es válido.")
                if (packageInfo.packageName != BuildConfig.APPLICATION_ID ||
                    packageInfo.longVersionCode != release.versionCode
                ) {
                    apk.delete()
                    throw IOException("La identidad o versión del APK no coincide con el manifiesto.")
                }

                if (!appContext.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${appContext.packageName}"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(permissionIntent)
                    throw IOException("Autoriza instalaciones para NOBLEX LAUNCHER y vuelve a comprobar la actualización.")
                }

                val apkUri = FileProvider.getUriForFile(
                    appContext,
                    "${BuildConfig.APPLICATION_ID}.updates",
                    apk,
                )
                val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = apkUri
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(installIntent)
            })
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "NOBLEX-LAUNCHER")
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
            inputStream.bufferedReader().use { JSONObject(it.readText()) }
        }
    }

    private fun download(address: String, destination: File, onProgress: (Int) -> Unit) {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = NETWORK_TIMEOUT_MS
        connection.readTimeout = NETWORK_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "NOBLEX-LAUNCHER")
        connection.useConnection {
            if (responseCode !in 200..299) throw IOException("No se pudo descargar el APK (HTTP $responseCode).")
            val contentLength = contentLengthLong
            inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(if (contentLength > 0) ((downloaded * 100) / contentLength).toInt() else -1)
                    }
                }
            }
        }
    }

    private fun requireReleaseUrl(address: String, tag: String, suffix: String) {
        val expected = "https://github.com/AlanDi123/noblex-launcher/releases/download/$tag/"
        if (!address.startsWith(expected) || !address.endsWith(suffix)) {
            throw IOException("La URL del artefacto no pertenece a la publicación esperada.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
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
        } catch (error: IOException) {
            Result.failure(error)
        } catch (error: JSONException) {
            Result.failure(IOException("El manifiesto de actualización no tiene un formato válido.", error))
        } catch (error: SecurityException) {
            Result.failure(IOException("Android no autorizó el acceso necesario para instalar la actualización.", error))
        } catch (error: IllegalArgumentException) {
            Result.failure(IOException("La actualización tiene datos de instalación inválidos.", error))
        }

    private const val NETWORK_TIMEOUT_MS = 15_000
}
