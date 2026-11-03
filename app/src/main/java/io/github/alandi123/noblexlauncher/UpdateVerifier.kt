package io.github.alandi123.noblexlauncher

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Pure integrity checks used by [UpdateManager]; every failure throws [IOException] and aborts the install. */
object UpdateVerifier {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return hex(digest.digest())
    }

    fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun requireChecksum(file: File, expected: String) {
        if (!sha256(file).equals(expected, ignoreCase = true)) {
            throw IOException("La verificación SHA-256 de la descarga falló.")
        }
    }

    /** [declaredLength] is the HTTP Content-Length, or <= 0 when unknown. */
    fun requireDeclaredSize(declaredLength: Long, expected: Long) {
        if (declaredLength > 0 && declaredLength != expected) {
            throw IOException("El tamaño de la descarga no coincide con el manifiesto.")
        }
    }

    fun requireDownloadedSize(downloaded: Long, expected: Long) {
        if (downloaded > expected) throw IOException("La descarga supera el tamaño publicado.")
    }

    fun requireComplete(downloaded: Long, expected: Long) {
        if (downloaded != expected) throw IOException("La descarga quedó incompleta.")
    }

    fun requireSameSigner(installedDigests: Set<String>, candidateDigests: Set<String>) {
        if (installedDigests.isEmpty() || installedDigests.intersect(candidateDigests).isEmpty()) {
            throw IOException("El APK no está firmado con la misma clave que la versión instalada.")
        }
    }

    fun requireIdentity(
        packageName: String?, versionCode: Long, versionName: String?,
        applicationId: String, release: LauncherRelease,
    ) {
        if (packageName != applicationId || versionCode != release.versionCode || versionName != release.versionName) {
            throw IOException("La identidad o versión del APK no coincide con el manifiesto.")
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
