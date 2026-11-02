package io.github.alandi123.noblexlauncher

import java.io.IOException

enum class UpdateDecision { UP_TO_DATE, AVAILABLE }

object UpdatePolicy {
    const val MAX_APK_BYTES = 80L * 1024 * 1024

    /** Never offers an equal or lower versionCode and rejects releases whose version and code disagree. */
    fun decide(release: LauncherRelease, installedVersionName: String, installedVersionCode: Long): UpdateDecision {
        if (release.versionCode <= installedVersionCode) return UpdateDecision.UP_TO_DATE
        val candidate = SemanticVersion.parse(release.versionName)
            ?: throw IOException("La versión publicada no es válida.")
        val installed = SemanticVersion.parse(installedVersionName)
        if (installed != null && candidate <= installed) {
            throw IOException("La versión publicada no es posterior a la instalada.")
        }
        return UpdateDecision.AVAILABLE
    }
}
