package io.github.alandi123.noblexlauncher

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reads and writes launcher backups. Everything stays on the television's local storage. */
class BackupManager(private val context: Context, private val preferences: LauncherPreferences) {
    class ExportResult(val appFile: File, val publicLocation: String?)

    private val backupDirectory: File?
        get() = context.getExternalFilesDir(null)?.let { File(it, "backup") }

    private val wallpaperFile: File get() = File(File(context.filesDir, "wallpapers"), "user-wallpaper.jpg")

    /** Must be called off the main thread. */
    fun export(): ExportResult {
        val directory = backupDirectory?.apply { mkdirs() } ?: throw IOException("El almacenamiento externo no está disponible.")
        val now = Date()
        val json = PreferencesBackup.encode(
            entries = preferences.exportEntries(),
            sourcePackage = context.packageName,
            launcherVersion = BuildConfig.VERSION_NAME,
            createdAtMillis = now.time,
            wallpaper = wallpaperFile.takeIf(File::isFile)?.takeIf { it.length() <= PreferencesBackup.MAX_WALLPAPER_BYTES }?.readBytes(),
        )
        val name = "noblex-launcher-backup-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(now)}.json"
        val file = File(directory, name)
        val temp = File(directory, "$name.tmp")
        temp.writeText(json)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("No se pudo guardar el respaldo.")
        }
        directory.listFiles { f -> f.name.startsWith("noblex-launcher-backup-") && f.name.endsWith(".json") }
            ?.sortedByDescending(File::getName)?.drop(MAX_KEPT_BACKUPS)?.forEach(File::delete)
        return ExportResult(file, copyToDownloads(name, json))
    }

    /** A copy in the shared Downloads folder survives uninstalling the debug package. */
    private fun copyToDownloads(name: String, json: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/NoblexLauncher")
            }
            val uri: Uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: return null
            "Download/NoblexLauncher/$name"
        } catch (_: Exception) {
            null
        }
    }

    fun availableBackups(): List<File> =
        backupDirectory?.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedByDescending(File::lastModified).orEmpty()

    /** Must be called off the main thread. Returns the number of restored entries. */
    fun import(file: File): Int {
        if (file.length() > PreferencesBackup.MAX_BYTES) throw IOException("El archivo de respaldo es demasiado grande.")
        return import(file.readText())
    }

    fun import(text: String): Int {
        val contents = PreferencesBackup.decode(text, LauncherPreferences.Companion::isBackedUpKey)
        contents.wallpaper?.let { bytes ->
            val directory = File(context.filesDir, "wallpapers").apply { mkdirs() }
            val temp = File(directory, "restored.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(wallpaperFile)) {
                temp.delete()
                throw IOException("No se pudo restaurar el fondo.")
            }
        }
        preferences.replaceEntries(contents.entries)
        if (contents.wallpaper != null) preferences.setWallpaperUri(Uri.fromFile(wallpaperFile).toString())
        return contents.entries.size
    }

    companion object {
        private const val MAX_KEPT_BACKUPS = 5
    }
}
