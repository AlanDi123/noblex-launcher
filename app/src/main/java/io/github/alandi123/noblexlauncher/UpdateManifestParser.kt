package io.github.alandi123.noblexlauncher

import org.json.JSONObject
import java.io.IOException

object UpdateManifestParser {
    private const val MAX_NOTES_LENGTH = 4_000
    private val SHA256 = Regex("[0-9a-f]{64}")

    fun parse(
        manifest: JSONObject,
        releaseTag: String,
        applicationId: String,
        sdkInt: Int,
        deviceAbis: List<String>,
    ): LauncherRelease {
        if (manifest.getString("applicationId") != applicationId) {
            throw IOException("La publicación no corresponde a esta aplicación.")
        }

        val version = manifest.getString("version")
        val semantic = SemanticVersion.parse(version)
            ?: throw IOException("La versión del manifiesto no cumple el formato semántico.")
        if (releaseTag != "v$version") {
            throw IOException("La versión del manifiesto no coincide con la publicación.")
        }
        val versionCode = manifest.getLong("versionCode")
        if (versionCode != semantic.toVersionCode()) {
            throw IOException("El versionCode del manifiesto no corresponde a la versión.")
        }

        val minSdk = manifest.getInt("minSdk")
        if (sdkInt < minSdk) {
            throw IOException("La actualización requiere Android API $minSdk.")
        }

        val supportedAbis = manifest.optJSONArray("supportedAbis")
            ?.let { array -> (0 until array.length()).map(array::getString) }
            .orEmpty()
        if ("universal" !in supportedAbis && supportedAbis.none { it in deviceAbis }) {
            throw IOException("La actualización no es compatible con la arquitectura de este dispositivo.")
        }

        val apkUrl = manifest.getString("apkUrl")
        if (!ReleaseUrlPolicy.isOfficialAsset(apkUrl, releaseTag, ReleaseUrlPolicy.apkFileName(version))) {
            throw IOException("La URL del APK no pertenece a la publicación oficial.")
        }
        val checksum = manifest.getString("sha256").lowercase()
        if (!SHA256.matches(checksum)) {
            throw IOException("El checksum publicado no es SHA-256 válido.")
        }
        val sizeBytes = manifest.getLong("sizeBytes")
        if (sizeBytes <= 0L || sizeBytes > UpdatePolicy.MAX_APK_BYTES) {
            throw IOException("El tamaño publicado del APK no es válido.")
        }

        return LauncherRelease(
            versionName = version,
            versionCode = versionCode,
            minSdk = minSdk,
            apkUrl = apkUrl,
            sha256 = checksum,
            sizeBytes = sizeBytes,
            releaseDate = manifest.optString("releaseDate"),
            releaseNotes = manifest.optString("releaseNotes").take(MAX_NOTES_LENGTH),
            mandatory = manifest.optBoolean("mandatory", false),
            supportedAbis = supportedAbis,
        )
    }
}
