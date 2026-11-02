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

    fun category(packageName: String): String =
        preferences.getString("$KEY_CATEGORY_PREFIX$packageName", DEFAULT_CATEGORY) ?: DEFAULT_CATEGORY

    fun setCategory(packageName: String, category: String) {
        preferences.edit().putString("$KEY_CATEGORY_PREFIX$packageName", category).apply()
    }

    fun categories(): List<String> {
        val custom = preferences.getString(KEY_CUSTOM_CATEGORIES, "").orEmpty()
            .split('\n')
            .filter(String::isNotBlank)
        return (CATEGORIES + custom).distinct()
    }

    /** Returns true when a new custom category was stored. */
    fun addCategory(category: String): Boolean {
        val normalized = LauncherLogic.normalizeCategory(category, categories()) ?: return false
        preferences.edit().putString(KEY_CUSTOM_CATEGORIES, (customCategories() + normalized).joinToString("\n")).apply()
        return true
    }

    fun customCategories(): List<String> = categories().filterNot { it in CATEGORIES }

    fun renameCategory(old: String, raw: String): Boolean {
        if (old !in customCategories()) return false
        val others = categories().filterNot { it == old }
        val renamed = LauncherLogic.normalizeCategory(raw, others) ?: return false
        val editor = preferences.edit()
        editor.putString(KEY_CUSTOM_CATEGORIES, customCategories().map { if (it == old) renamed else it }.joinToString("\n"))
        preferences.all.filter { (key, value) -> key.startsWith(KEY_CATEGORY_PREFIX) && value == old }
            .keys.forEach { editor.putString(it, renamed) }
        editor.apply()
        return true
    }

    fun deleteCategory(name: String): Boolean {
        if (name !in customCategories()) return false
        val editor = preferences.edit()
        editor.putString(KEY_CUSTOM_CATEGORIES, customCategories().filterNot { it == name }.joinToString("\n"))
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

    fun wallpaperUri(): String? = preferences.getString(KEY_WALLPAPER_URI, null)

    fun setWallpaperUri(uri: String?) {
        preferences.edit().putString(KEY_WALLPAPER_URI, uri).apply()
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
            "Entretenimiento",
            "Juegos",
            "Herramientas",
            "Otras",
        )
        const val DEFAULT_CATEGORY = "Otras"
        const val DEFAULT_DIM = 10
        const val MAX_DIM = 70
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_CATEGORY_PREFIX = "category."
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_ORDER = "order"
        private const val KEY_RECENTS = "recents"
        private const val KEY_CUSTOM_CATEGORIES = "custom_categories"
        private const val KEY_WALLPAPER_DIM = "wallpaper_dim"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_LAST_RELEASE = "last_release"
        private const val MAX_RECENTS = 12
    }
}
