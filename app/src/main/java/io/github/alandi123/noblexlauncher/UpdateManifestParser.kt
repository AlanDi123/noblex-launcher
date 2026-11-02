package io.github.alandi123.noblexlauncher

import org.json.JSONObject
import java.io.IOException

object UpdateManifestParser {
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
        val versionCode = manifest.getLong("versionCode")
        if (version != releaseTag.removePrefix("v") || versionCode <= 0L) {
            throw IOException("La versión del manifiesto no coincide con la publicación.")
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
        requireReleaseUrl(apkUrl, releaseTag, ".apk")
        val checksum = manifest.getString("sha256").lowercase()
        if (!checksum.matches(Regex("[0-9a-f]{64}"))) {
            throw IOException("El checksum publicado no es SHA-256 válido.")
        }

        return LauncherRelease(
            versionName = version,
            versionCode = versionCode,
            minSdk = minSdk,
            apkUrl = apkUrl,
            sha256 = checksum,
            releaseDate = manifest.optString("releaseDate"),
            releaseNotes = manifest.optString("releaseNotes"),
            mandatory = manifest.optBoolean("mandatory", false),
            supportedAbis = supportedAbis,
        )
    }

    private fun requireReleaseUrl(address: String, tag: String, suffix: String) {
        val expected = "https://github.com/AlanDi123/noblex-launcher/releases/download/$tag/"
        if (!address.startsWith(expected) || !address.endsWith(suffix)) {
            throw IOException("La URL del artefacto no pertenece a la publicación esperada.")
        }
    }
}
