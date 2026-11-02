package io.github.alandi123.noblexlauncher

/** Pure list/category rules, kept free of Android types so they can be unit tested. */
object LauncherLogic {
    val RESERVED_CATEGORY_NAMES = setOf("Favoritos", "Todas", "Recientes", "Entradas")
    const val MAX_CATEGORY_LENGTH = 24

    /** Returns a trimmed name, or null when it is empty, reserved or already used (case-insensitive). */
    fun normalizeCategory(raw: String, existing: Collection<String>): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ").take(MAX_CATEGORY_LENGTH).trim()
        if (name.isEmpty() || '\n' in name) return null
        val taken = (existing + RESERVED_CATEGORY_NAMES).any { it.equals(name, ignoreCase = true) }
        return name.takeUnless { taken }
    }

    /**
     * Swaps [packageName] with [neighbor] inside the persisted [order]. Packages missing from the
     * persisted order are appended in [available] order first, so the swap is always visible.
     */
    fun swap(order: List<String>, available: List<String>, packageName: String, neighbor: String): List<String> {
        val full = (order.filter { it in available } + available.filterNot { it in order }).toMutableList()
        val a = full.indexOf(packageName)
        val b = full.indexOf(neighbor)
        if (a < 0 || b < 0) return full
        full[a] = neighbor
        full[b] = packageName
        return full
    }

    fun recordRecent(recents: List<String>, packageName: String, limit: Int): List<String> =
        (listOf(packageName) + recents.filter { it != packageName }).take(limit)
}
