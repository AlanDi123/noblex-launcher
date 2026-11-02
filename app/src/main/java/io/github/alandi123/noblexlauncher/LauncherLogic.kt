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

    private val STREAMING = setOf(
        "com.netflix.ninja", "com.disney.disneyplus", "com.wbd.stream", "com.amazon.amazonvideo.livingroom",
        "com.crunchyroll.crunchyroid", "com.directv.dtvlatam", "ar.onvideo", "com.google.android.youtube.tv",
        "com.cbs.ott", "com.hulu.plus", "com.apple.atve.androidtv.appletv", "tv.pluto.android",
    )
    private val TV_AND_MOVIES = setOf("com.google.android.videos", "com.mediatek.wwtv.tvcenter", "com.homwee.aipont")
    private val MUSIC = setOf("com.spotify.tv.android", "com.google.android.youtube.tvmusic")
    private val GAMES = setOf("com.google.android.play.games")
    private val TOOLS = setOf(
        "com.android.vending", "com.homwee.chtvmanager", "com.homwee.help", "com.homwee.mmp.fileexplorer",
        "com.homwee.multiscreenshare", "com.android.documentsui",
    )

    /** Category used until the user assigns one: known apps first, then system apps, then "Entretenimiento". */
    fun defaultCategory(packageName: String, system: Boolean): String = when {
        packageName in STREAMING -> "Streaming"
        packageName in TV_AND_MOVIES -> "Películas y TV"
        packageName in MUSIC -> "Música"
        packageName in GAMES || packageName.contains("games") -> "Juegos"
        packageName in TOOLS -> "Herramientas"
        system -> "Sistema"
        else -> "Entretenimiento"
    }
}
