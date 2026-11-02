package io.github.alandi123.noblexlauncher

import java.util.Base64
import java.io.IOException
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Portable, versioned backup of the launcher's user data. Android does not migrate private data
 * between the debug and release application IDs, so this file is the supported migration path.
 * It is plain JSON stored locally; nothing here is ever uploaded.
 */
object PreferencesBackup {
    const val FORMAT = "noblex-launcher-backup"
    const val VERSION = 1
    const val MAX_BYTES = 6 * 1024 * 1024
    const val MAX_WALLPAPER_BYTES = 3 * 1024 * 1024
    private const val MAX_KEYS = 5000
    private const val MAX_SET_SIZE = 5000

    class Contents(val entries: Map<String, Any>, val wallpaper: ByteArray?, val sourcePackage: String)

    fun encode(
        entries: Map<String, Any>,
        sourcePackage: String,
        launcherVersion: String,
        createdAtMillis: Long,
        wallpaper: ByteArray?,
    ): String {
        val strings = JSONObject()
        val ints = JSONObject()
        val sets = JSONObject()
        entries.toSortedMap().forEach { (key, value) ->
            when (value) {
                is String -> strings.put(key, value)
                is Int -> ints.put(key, value)
                is Set<*> -> sets.put(key, JSONArray(value.filterIsInstance<String>().sorted()))
            }
        }
        val json = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("sourcePackage", sourcePackage)
            .put("launcherVersion", launcherVersion)
            .put("createdAt", createdAtMillis)
            .put("strings", strings)
            .put("ints", ints)
            .put("stringSets", sets)
        if (wallpaper != null && wallpaper.size <= MAX_WALLPAPER_BYTES) {
            json.put("wallpaperJpegBase64", Base64.getEncoder().encodeToString(wallpaper))
        }
        return json.toString(2)
    }

    /** Validates structure, size and key whitelist; throws [IOException] with a user-readable reason. */
    fun decode(text: String, isAllowedKey: (String) -> Boolean): Contents {
        if (text.length > MAX_BYTES) throw IOException("El archivo de respaldo es demasiado grande.")
        val json = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw IOException("El archivo no es un respaldo válido de NOBLEX LAUNCHER.", error)
        }
        if (json.optString("format") != FORMAT) throw IOException("El archivo no es un respaldo de NOBLEX LAUNCHER.")
        val version = json.optInt("version", -1)
        if (version < 1 || version > VERSION) throw IOException("Versión de respaldo no compatible ($version).")
        val entries = LinkedHashMap<String, Any>()
        try {
            json.optJSONObject("strings")?.let { obj ->
                obj.keys().forEach { key -> if (isAllowedKey(key)) entries[key] = obj.getString(key) }
            }
            json.optJSONObject("ints")?.let { obj ->
                obj.keys().forEach { key -> if (isAllowedKey(key)) entries[key] = obj.getInt(key) }
            }
            json.optJSONObject("stringSets")?.let { obj ->
                obj.keys().forEach { key ->
                    if (!isAllowedKey(key)) return@forEach
                    val array = obj.getJSONArray(key)
                    if (array.length() > MAX_SET_SIZE) throw IOException("El respaldo contiene demasiados elementos.")
                    entries[key] = (0 until array.length()).map(array::getString).toSet()
                }
            }
        } catch (error: JSONException) {
            throw IOException("El respaldo está dañado o tiene un formato inesperado.", error)
        }
        if (entries.size > MAX_KEYS) throw IOException("El respaldo contiene demasiados elementos.")
        val wallpaper = json.optString("wallpaperJpegBase64").takeIf { it.isNotEmpty() }?.let {
            try {
                Base64.getMimeDecoder().decode(it)
            } catch (error: IllegalArgumentException) {
                throw IOException("La imagen incluida en el respaldo está dañada.", error)
            }
        }
        if (wallpaper != null && wallpaper.size > MAX_WALLPAPER_BYTES) throw IOException("El fondo del respaldo es demasiado grande.")
        return Contents(entries, wallpaper, json.optString("sourcePackage"))
    }
}
