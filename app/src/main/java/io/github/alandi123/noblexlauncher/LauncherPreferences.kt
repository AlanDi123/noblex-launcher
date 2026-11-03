package io.github.alandi123.noblexlauncher

import android.content.Context
import org.json.JSONObject

class LauncherPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun favorites(): Set<String> = preferences.getStringSet(KEY_FAVORITES, emptySet()).orEmpty().toSet()

    fun isFavorite(packageName: String): Boolean = packageName in favorites()

    fun toggleFavorite(packageName: String): Boolean {
        val updated = favorites().toMutableSet()
        val isFavorite = if (updated.remove(packageName)) {
            false
        } else {
            updated.add(packageName)
            true
        }
        preferences.edit().putStringSet(KEY_FAVORITES, updated).apply()
        return isFavorite
    }

    /** The user's explicit assignment if it still names an existing category, otherwise [fallback]. */
    fun category(packageName: String, fallback: String = DEFAULT_CATEGORY, valid: Collection<String> = categories()): String {
        val assigned = preferences.getString("$KEY_CATEGORY_PREFIX$packageName", null)
        return assigned?.takeIf { it in valid } ?: fallback
    }

    fun setCategory(packageName: String, category: String) {
        preferences.edit().putString("$KEY_CATEGORY_PREFIX$packageName", category).apply()
    }

    fun categories(): List<String> {
        val all = (CATEGORIES + storedCustomCategories()).distinct()
        val order = preferences.getString(KEY_CATEGORY_ORDER, "").orEmpty().split('\n').filter { it in all }
        return (order + all.filterNot { it in order }).distinct()
    }

    private fun storedCustomCategories(): List<String> =
        preferences.getString(KEY_CUSTOM_CATEGORIES, "").orEmpty().split('\n').filter(String::isNotBlank)

    /** Moves [name] one position earlier (-1) or later (+1) in the persisted chip order. */
    fun moveCategory(name: String, direction: Int): Boolean {
        val current = categories().toMutableList()
        val index = current.indexOf(name)
        val target = index + direction
        if (index < 0 || target !in current.indices) return false
        current[index] = current[target]
        current[target] = name
        preferences.edit().putString(KEY_CATEGORY_ORDER, current.joinToString("\n")).apply()
        return true
    }

    /** Returns true when a new custom category was stored. */
    fun addCategory(category: String): Boolean {
        val normalized = LauncherLogic.normalizeCategory(category, categories()) ?: return false
        preferences.edit().putString(KEY_CUSTOM_CATEGORIES, (customCategories() + normalized).joinToString("\n")).apply()
        return true
    }

    fun customCategories(): List<String> = categories().filterNot { it in CATEGORIES }

    fun isBuiltInCategory(name: String): Boolean = name in CATEGORIES

    fun renameCategory(old: String, raw: String): Boolean {
        if (old !in customCategories()) return false
        val others = categories().filterNot { it == old }
        val renamed = LauncherLogic.normalizeCategory(raw, others) ?: return false
        val editor = preferences.edit()
        editor.putString(KEY_CUSTOM_CATEGORIES, customCategories().map { if (it == old) renamed else it }.joinToString("\n"))
        editor.putString(KEY_CATEGORY_ORDER, categories().map { if (it == old) renamed else it }.joinToString("\n"))
        preferences.all.filter { (key, value) -> key.startsWith(KEY_CATEGORY_PREFIX) && value == old }
            .keys.forEach { editor.putString(it, renamed) }
        editor.apply()
        return true
    }

    fun deleteCategory(name: String): Boolean {
        if (name !in customCategories()) return false
        val editor = preferences.edit()
        editor.putString(KEY_CUSTOM_CATEGORIES, customCategories().filterNot { it == name }.joinToString("\n"))
        editor.putString(KEY_CATEGORY_ORDER, categories().filterNot { it == name }.joinToString("\n"))
        preferences.all.filter { (key, value) -> key.startsWith(KEY_CATEGORY_PREFIX) && value == name }
            .keys.forEach { editor.remove(it) }
        editor.apply()
        return true
    }

    fun hiddenApps(): Set<String> = preferences.getStringSet(KEY_HIDDEN, emptySet()).orEmpty().toSet()

    fun toggleHidden(packageName: String): Boolean {
        val updated = hiddenApps().toMutableSet()
        val hidden = if (updated.remove(packageName)) {
            false
        } else {
            updated.add(packageName)
            true
        }
        preferences.edit().putStringSet(KEY_HIDDEN, updated).apply()
        return hidden
    }

    fun orderedPackages(): List<String> =
        preferences.getString(KEY_ORDER, "").orEmpty().split('\n').filter(String::isNotBlank)

    fun move(packageName: String, neighbor: String, availablePackages: List<String>) {
        val order = LauncherLogic.swap(orderedPackages(), availablePackages, packageName, neighbor)
        preferences.edit().putString(KEY_ORDER, order.joinToString("\n")).apply()
    }

    fun recordLaunch(packageName: String) {
        val recent = LauncherLogic.recordRecent(recentPackages(), packageName, MAX_RECENTS)
        preferences.edit().putString(KEY_RECENTS, recent.joinToString("\n")).apply()
    }

    fun recentPackages(): List<String> =
        preferences.getString(KEY_RECENTS, "").orEmpty().split('\n').filter(String::isNotBlank)

    fun wallpaperDim(): Int = preferences.getInt(KEY_WALLPAPER_DIM, DEFAULT_DIM)

    fun setWallpaperDim(value: Int) {
        preferences.edit().putInt(KEY_WALLPAPER_DIM, value.coerceIn(0, MAX_DIM)).apply()
    }

    fun introSeen(): Boolean = preferences.getBoolean(KEY_INTRO_SEEN, false)

    fun setIntroSeen(seen: Boolean) {
        preferences.edit().putBoolean(KEY_INTRO_SEEN, seen).apply()
    }

    fun wallpaperUri(): String? = preferences.getString(KEY_WALLPAPER_URI, null)

    fun setWallpaperUri(uri: String?) {
        preferences.edit().putString(KEY_WALLPAPER_URI, uri).apply()
    }

    /** All user-owned entries (never the cached update manifest) as plain JSON-friendly values. */
    fun exportEntries(): Map<String, Any> = preferences.all
        .filter { (key, value) -> isBackedUpKey(key) && value != null }
        .mapValues { it.value!! }

    /** Replaces every backed-up entry with [entries]; keys outside the backup whitelist are ignored. */
    fun replaceEntries(entries: Map<String, Any>) {
        val editor = preferences.edit()
        preferences.all.keys.filter(::isBackedUpKey).forEach(editor::remove)
        entries.filter { isBackedUpKey(it.key) }.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        editor.commit()
    }

    fun saveReleaseManifest(json: JSONObject?) {
        preferences.edit().putString(KEY_LAST_RELEASE, json?.toString()).apply()
    }

    fun savedReleaseManifest(): JSONObject? =
        preferences.getString(KEY_LAST_RELEASE, null)?.let(::JSONObject)

    companion object {
        val CATEGORIES = listOf(
            "Streaming",
            "Películas y TV",
            "Música",
            "Juegos",
            "Entretenimiento",
            "Herramientas",
            "Sistema",
            "Otras",
        )
        const val DEFAULT_CATEGORY = "Otras"

        fun isBackedUpKey(key: String): Boolean =
            key in BACKUP_KEYS || key.startsWith(KEY_CATEGORY_PREFIX)
        private val BACKUP_KEYS = setOf(
            "favorites", "hidden", "order", "recents", "custom_categories", "category_order", "wallpaper_dim",
        )
        const val DEFAULT_DIM = 10
        const val MAX_DIM = 70
        private const val KEY_INTRO_SEEN = "intro_seen"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_CATEGORY_PREFIX = "category."
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ORDER = "order"
        private const val KEY_RECENTS = "recents"
        private const val KEY_CUSTOM_CATEGORIES = "custom_categories"
        private const val KEY_CATEGORY_ORDER = "category_order"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_LAST_RELEASE = "last_release"
        private const val MAX_RECENTS = 12
    }
}
