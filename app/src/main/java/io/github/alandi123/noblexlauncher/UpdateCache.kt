package io.github.alandi123.noblexlauncher

import java.io.File

/** Owns the directory used for downloaded APKs and removes anything that is no longer useful. */
class UpdateCache(val directory: File) {
    fun apkFor(versionCode: Long) = File(directory, "noblex-launcher-$versionCode.apk")

    fun partFor(versionCode: Long) = File(directory, "noblex-launcher-$versionCode.apk.part")

    fun prepare(): File = directory.apply { mkdirs() }

    /**
     * Deletes partial downloads older than an hour, APKs that are not newer than the installed
     * version, APKs older than a week and any unknown file. Returns the number of removed files.
     */
    fun cleanup(installedVersionCode: Long, now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        directory.listFiles().orEmpty().filter(File::isFile).forEach { file ->
            val age = now - file.lastModified()
            val code = NAME.matchEntire(file.name)?.groupValues?.get(1)?.toLongOrNull()
            val stale = when {
                code == null -> true
                file.name.endsWith(".part") -> age > PART_MAX_AGE_MS
                else -> code <= installedVersionCode || age > APK_MAX_AGE_MS
            }
            if (stale && file.delete()) removed++
        }
        return removed
    }

    private companion object {
        val NAME = Regex("""noblex-launcher-(\d+)\.apk(?:\.part)?""")
        const val PART_MAX_AGE_MS = 60L * 60 * 1000
        const val APK_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
