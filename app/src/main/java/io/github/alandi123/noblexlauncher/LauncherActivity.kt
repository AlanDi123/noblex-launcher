package io.github.alandi123.noblexlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class LauncherActivity : Activity() {
    private val preferences by lazy { LauncherPreferences(this) }
    private val appDiscovery by lazy { AppDiscovery(this) }
    private val tvInputRepository by lazy { TvInputRepository(this) }
    private val changhongTvAdapter by lazy { ChanghongTvAdapter(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()

    private var apps = emptyList<LaunchableApp>()
    private var appsLoaded = false
    private var currentFilter = FILTER_ALL
    private var destroyed = false

    private lateinit var wallpaperView: ImageView
    private lateinit var dimView: View
    private lateinit var contentHost: LinearLayout
    private lateinit var clockView: TextView
    private lateinit var dateView: TextView
    private lateinit var updateBadge: TextView
    private var wallpaperBitmap: Bitmap? = null
    private var firstCardTag: String? = null
    private var started = false

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            val now = System.currentTimeMillis()
            mainHandler.postDelayed(this, CLOCK_UPDATE_MS - now % CLOCK_UPDATE_MS + 50L)
        }
    }

    private val reloadAppsRunnable = Runnable { reloadApps() }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            mainHandler.removeCallbacks(reloadAppsRunnable)
            mainHandler.postDelayed(reloadAppsRunnable, PACKAGE_CHANGE_DEBOUNCE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildShell())
        refreshContent(FOCUS_FIRST_CARD)
        UpdateScheduler.schedule(this)
    }

    override fun onStart() {
        super.onStart()
        started = true
        registerReceiver(
            packageReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            },
        )
        loadWallpaperIfNeeded()
        reloadApps()
        refreshUpdateBadge()
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.post(clockRunnable)
    }

    override fun onStop() {
        started = false
        mainHandler.removeCallbacks(clockRunnable)
        mainHandler.removeCallbacks(reloadAppsRunnable)
        unregisterReceiver(packageReceiver)
        super.onStop()
    }

    override fun onDestroy() {
        destroyed = true
        worker.shutdownNow()
        wallpaperView.setImageDrawable(null)
        wallpaperBitmap = null
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND && !started) {
            wallpaperView.setImageDrawable(null)
            wallpaperBitmap = null
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // HOME pressed while the launcher is already on top: go back to the start of the dashboard.
        if (currentFilter != FILTER_ALL) currentFilter = FILTER_ALL
        refreshContent(FOCUS_FIRST_CARD)
    }

    @Deprecated("Deprecated in Android API; retained for API 28 compatibility.")
    override fun onBackPressed() {
        // A home screen never exits; Back only returns from a filtered view to "Todas".
        if (currentFilter != FILTER_ALL) {
            currentFilter = FILTER_ALL
            refreshContent("$TAG_CHIP$FILTER_ALL")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val tag = currentFocus?.tag as? String
            when {
                tag?.startsWith(TAG_APP) == true -> {
                    val packageName = tag.removePrefix(TAG_APP)
                    apps.firstOrNull { it.packageName == packageName }?.let(::showAppActions)
                }
                tag?.startsWith(TAG_CHIP) == true -> {
                    val filter = tag.removePrefix(TAG_CHIP)
                    if (filter in preferences.customCategories()) showCategoryActions(filter) else showSettings()
                }
                else -> showSettings()
            }
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @Deprecated("Deprecated in Android API; retained for API 28 compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_WALLPAPER || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Toast.makeText(this, "Preparando el fondo…", Toast.LENGTH_SHORT).show()
        runOnWorker {
            val result = try {
                Result.success(saveWallpaper(uri))
            } catch (error: IOException) {
                Result.failure<File>(error)
            } catch (error: SecurityException) {
                Result.failure<File>(IOException("Android no permitió leer la imagen seleccionada.", error))
            } catch (error: OutOfMemoryError) {
                Result.failure<File>(IOException("La imagen es demasiado grande para este dispositivo.", error))
            }
            runOnUiThread {
                result.fold(
                    onSuccess = { file ->
                        preferences.setWallpaperUri(Uri.fromFile(file).toString())
                        wallpaperBitmap = null
                        loadWallpaperIfNeeded()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "No se pudo guardar el fondo.", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    // ---------------------------------------------------------------- layout

    private fun buildShell(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(16, 23, 25)) }
        val match = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        wallpaperView = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(wallpaperView, match)

        dimView = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = preferences.wallpaperDim() / 100f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(dimView, match)

        val scrim = View(this).apply {
            background = getDrawable(R.drawable.scrim)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(scrim, match)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(26), dp(48), dp(22))
            clipChildren = false
            clipToPadding = false
        }
        content.addView(buildHeader())
        content.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        contentHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        content.addView(contentHost, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
        root.addView(content, match)
        return root
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(text("NOBLEX", 26f, Color.WHITE, bold = true))
        brand.addView(text("L A U N C H E R", 12f, Color.rgb(200, 214, 211)))
        row.addView(brand)

        updateBadge = text("", 15f, Color.rgb(230, 240, 236), bold = true).apply { visibility = View.GONE }
        row.addView(updateBadge, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
            marginStart = dp(28)
        })
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val settings = button("Ajustes") { showSettings() }
        settings.tag = TAG_SETTINGS
        row.addView(settings, LinearLayout.LayoutParams(dp(120), dp(48)).apply { marginEnd = dp(28) })

        val clockBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        clockView = text("", 44f, Color.WHITE, bold = true).apply { gravity = Gravity.END }
        dateView = text("", 17f, Color.rgb(226, 234, 232)).apply { gravity = Gravity.END }
        clockBlock.addView(clockView)
        clockBlock.addView(dateView)
        row.addView(clockBlock)
        return row
    }

    /** Rebuilds only the lower area; the wallpaper, clock and header persist across rebuilds. */
    private fun refreshContent(focusTag: String?) {
        contentHost.removeAllViews()
        firstCardTag = null
        contentHost.addView(sectionTitle(if (currentFilter == FILTER_INPUTS) "Entradas de TV" else "Tus aplicaciones"))
        contentHost.addView(buildFilters(), marginBottom = dp(6))
        when {
            currentFilter == FILTER_INPUTS -> contentHost.addView(buildInputRow())
            !appsLoaded -> contentHost.addView(message("Cargando aplicaciones…"))
            else -> contentHost.addView(buildAppRow())
        }
        contentHost.addView(
            text("MENU: organizar aplicaciones y categorías  ·  ATRÁS: volver a Todas", 15f, Color.rgb(205, 218, 215)),
            marginTop = dp(10),
        )
        updateClock()
        restoreFocus(focusTag)
    }

    private fun restoreFocus(focusTag: String?) {
        val target = focusTag?.takeIf { it != FOCUS_FIRST_CARD }?.let(contentHost::findViewWithTag)
            ?: firstCardTag?.let { contentHost.findViewWithTag<View>(it) }
            ?: contentHost.findViewWithTag("$TAG_CHIP$currentFilter")
        target?.requestFocus()
    }

    private fun buildFilters(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            clipChildren = false
            setPadding(0, dp(4), 0, dp(4))
        }
        val filters = listOf(FILTER_FAVORITES, FILTER_ALL, FILTER_RECENT) + preferences.categories() + FILTER_INPUTS
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filters.forEach { filter ->
            val chip = button(filter) {
                currentFilter = filter
                refreshContent("$TAG_CHIP$filter")
            }
            chip.tag = "$TAG_CHIP$filter"
            chip.isSelected = currentFilter == filter
            chip.contentDescription = if (currentFilter == filter) "$filter, seleccionado" else filter
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)).apply {
                marginEnd = dp(10)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun visibleApps(): List<LaunchableApp> {
        val hidden = preferences.hiddenApps()
        val favorites = preferences.favorites()
        val order = preferences.orderedPackages()
        val recents = preferences.recentPackages()
        val candidates = apps.filterNot { it.packageName in hidden }
        return when (currentFilter) {
            FILTER_RECENT -> candidates.filter { it.packageName in recents }.sortedBy { recents.indexOf(it.packageName) }
            else -> candidates
                .filter { app ->
                    when (currentFilter) {
                        FILTER_FAVORITES -> app.packageName in favorites
                        FILTER_ALL -> true
                        else -> preferences.category(app.packageName) == currentFilter
                    }
                }
                .sortedWith(
                    compareByDescending<LaunchableApp> { it.packageName in favorites }
                        .thenBy { order.indexOf(it.packageName).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                        .thenBy { it.label.lowercase() },
                )
        }
    }

    private fun buildAppRow(): View {
        val visibleApps = visibleApps()
        if (visibleApps.isEmpty()) {
            return message(
                when (currentFilter) {
                    FILTER_FAVORITES -> "Agrega aplicaciones a Favoritos con MENU."
                    FILTER_RECENT -> "Las aplicaciones que abras aparecerán aquí."
                    else -> "No hay aplicaciones en esta categoría todavía."
                },
            )
        }
        val favorites = preferences.favorites()
        val scroll = cardScroll()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        visibleApps.forEachIndexed { index, app ->
            val card = createAppCard(app, app.packageName in favorites)
            if (index == 0) firstCardTag = card.tag as String
            row.addView(card, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply {
                marginEnd = dp(CARD_GAP_DP)
            })
        }
        scroll.addView(row)
        return scroll
    }

    private fun cardScroll() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        clipChildren = false
        setPadding(dp(6), dp(10), dp(6), dp(10))
    }

    private fun createAppCard(app: LaunchableApp, favorite: Boolean): View {
        val label = text(app.label + if (favorite) "  ★" else "", 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.focus_card)
            setPadding(dp(10), dp(10), dp(10), dp(8))
            contentDescription = app.label + if (favorite) ", favorita" else ""
            tag = "$TAG_APP${app.packageName}"
            setOnFocusChangeListener { view, hasFocus ->
                view.animate().cancel()
                view.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120L)
                if (hasFocus) view.elevation = dp(8).toFloat() else view.elevation = 0f
            }
            setOnClickListener { launchApp(app) }
            setOnLongClickListener {
                showAppActions(app)
                true
            }
        }
        val artwork = ImageView(this).apply {
            background = getDrawable(R.drawable.card_tile)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            if (app.banner != null) {
                setImageDrawable(app.banner)
                scaleType = ImageView.ScaleType.CENTER_CROP
            } else {
                setImageDrawable(app.icon)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(22), dp(14), dp(22), dp(14))
            }
        }
        card.addView(artwork, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ARTWORK_HEIGHT_DP)))
        card.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return card
    }

    private fun buildInputRow(): View {
        val inputs = tvInputRepository.discover()
        if (inputs.isEmpty()) return message("Android no informó entradas de TV disponibles.")
        val scroll = cardScroll()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        inputs.forEachIndexed { index, input ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                background = getDrawable(R.drawable.focus_card)
                tag = "$TAG_INPUT${input.id}"
                contentDescription = "${input.label}, ${TvInputRepository.stateLabel(input.state)}"
                setOnClickListener { showInputDetails(input) }
                setOnFocusChangeListener { view, hasFocus ->
                    view.animate().cancel()
                    view.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120L)
                }
            }
            card.addView(text(input.label, 18f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER })
            card.addView(text(TvInputRepository.stateLabel(input.state), 14f, Color.rgb(205, 218, 215)).apply {
                gravity = Gravity.CENTER
            })
            if (index == 0) firstCardTag = card.tag as String
            row.addView(card, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(96)).apply { marginEnd = dp(CARD_GAP_DP) })
        }
        scroll.addView(row)
        return scroll
    }

    // ---------------------------------------------------------------- data

    private fun reloadApps() {
        runOnWorker {
            val discovered = appDiscovery.discover()
            runOnUiThread {
                val changed = !appsLoaded ||
                    discovered.map { it.packageName to it.label } != apps.map { it.packageName to it.label }
                apps = discovered
                appsLoaded = true
                if (changed) refreshContent(currentFocus?.tag as? String ?: FOCUS_FIRST_CARD)
            }
        }
    }

    private fun runOnWorker(block: () -> Unit) {
        try {
            worker.execute(block)
        } catch (_: RejectedExecutionException) {
            // The activity is being destroyed.
        }
    }

    private fun refreshUpdateBadge() {
        val release = preferences.savedReleaseManifest()
        val available = release != null && release.optLong("versionCode", 0L) > BuildConfig.VERSION_CODE
        updateBadge.visibility = if (available) View.VISIBLE else View.GONE
        updateBadge.text = "Actualización ${release?.optString("version").orEmpty()} disponible · Ajustes"
    }

    // ---------------------------------------------------------------- wallpaper

    private fun loadWallpaperIfNeeded() {
        if (wallpaperBitmap != null) return
        val customPath = preferences.wallpaperUri()?.let { Uri.parse(it).path }
        runOnWorker {
            val bitmap = decodeWallpaper(customPath)
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                wallpaperBitmap = bitmap
                wallpaperView.setImageBitmap(bitmap)
            }
        }
    }

    /** Decodes at most 1920×1080; falls back to the bundled wallpaper if the saved file is unusable. */
    private fun decodeWallpaper(customPath: String?): Bitmap? {
        val file = customPath?.let(::File)?.takeIf(File::isFile)
        if (file != null) {
            decodeSampled { options -> BitmapFactory.decodeFile(file.absolutePath, options) }?.let { return it }
        }
        return decodeSampled { options -> BitmapFactory.decodeResource(resources, R.drawable.wallpaper, options) }
    }

    private fun decodeSampled(decode: (BitmapFactory.Options) -> Bitmap?): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        decode(bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return try {
            decode(options)
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= MAX_WALLPAPER_WIDTH && height / (sample * 2) >= MAX_WALLPAPER_HEIGHT) sample *= 2
        return sample
    }

    private fun saveWallpaper(uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("No se pudo leer la imagen seleccionada.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("El archivo seleccionado no es una imagen válida.")
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val decoded = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("No se pudo decodificar la imagen seleccionada.")
        val scale = minOf(1f, MAX_WALLPAPER_WIDTH / decoded.width.toFloat(), MAX_WALLPAPER_HEIGHT / decoded.height.toFloat())
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val directory = File(filesDir, "wallpapers").apply { mkdirs() }
        val temp = File(directory, "user-wallpaper.tmp")
        val target = File(directory, "user-wallpaper.jpg")
        val saved = try {
            temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, WALLPAPER_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        if (!saved || !temp.renameTo(target)) {
            temp.delete()
            throw IOException("No se pudo guardar la imagen seleccionada.")
        }
        return target
    }

    // ---------------------------------------------------------------- dialogs

    private fun dialog(): AlertDialog.Builder = AlertDialog.Builder(this, R.style.LauncherDialog)

    private fun showAppActions(app: LaunchableApp) {
        val tag = "$TAG_APP${app.packageName}"
        val choices = arrayOf(
            if (preferences.isFavorite(app.packageName)) "Quitar de Favoritos" else "Agregar a Favoritos",
            "Asignar categoría",
            "Mover antes",
            "Mover después",
            if (app.packageName in preferences.hiddenApps()) "Mostrar aplicación" else "Ocultar aplicación",
        )
        dialog()
            .setTitle(app.label)
            .setItems(choices) { _, selected ->
                var focus: String? = tag
                when (selected) {
                    0 -> preferences.toggleFavorite(app.packageName)
                    1 -> {
                        chooseCategory(app)
                        return@setItems
                    }
                    2 -> moveApp(app, -1)
                    3 -> moveApp(app, 1)
                    4 -> {
                        preferences.toggleHidden(app.packageName)
                        focus = null
                    }
                }
                refreshContent(focus)
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun moveApp(app: LaunchableApp, direction: Int) {
        val visible = visibleApps()
        val index = visible.indexOfFirst { it.packageName == app.packageName }
        val neighbor = visible.getOrNull(index + direction)
        if (index < 0 || neighbor == null) {
            Toast.makeText(this, "No se puede mover más en esa dirección.", Toast.LENGTH_SHORT).show()
            return
        }
        val favorites = preferences.favorites()
        if (currentFilter == FILTER_RECENT) {
            Toast.makeText(this, "Recientes se ordena por uso; usa Todas para reordenar.", Toast.LENGTH_SHORT).show()
            return
        }
        if ((app.packageName in favorites) != (neighbor.packageName in favorites)) {
            Toast.makeText(this, "Los favoritos siempre aparecen primero.", Toast.LENGTH_SHORT).show()
            return
        }
        preferences.move(app.packageName, neighbor.packageName, apps.map(LaunchableApp::packageName))
    }

    private fun chooseCategory(app: LaunchableApp) {
        val categories = preferences.categories()
        val choices = (categories + "Crear categoría…").toTypedArray()
        dialog()
            .setTitle("Categoría para ${app.label}")
            .setItems(choices) { _, selected ->
                if (selected == categories.size) {
                    askCategoryName("Nueva categoría", "") { name ->
                        if (preferences.addCategory(name)) {
                            val stored = preferences.categories().last { it.equals(name.trim(), ignoreCase = true) }
                            preferences.setCategory(app.packageName, stored)
                            refreshContent("$TAG_APP${app.packageName}")
                        } else {
                            Toast.makeText(this, "Nombre no válido o repetido.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    preferences.setCategory(app.packageName, categories[selected])
                    refreshContent("$TAG_APP${app.packageName}")
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCategoryActions(category: String) {
        dialog()
            .setTitle(category)
            .setItems(arrayOf("Renombrar categoría", "Eliminar categoría")) { _, selected ->
                if (selected == 0) {
                    askCategoryName("Renombrar categoría", category) { name ->
                        if (preferences.renameCategory(category, name)) {
                            val renamed = LauncherLogic.normalizeCategory(name, emptyList()) ?: category
                            if (currentFilter == category) currentFilter = renamed
                            refreshContent("$TAG_CHIP$renamed")
                        } else {
                            Toast.makeText(this, "Nombre no válido o repetido.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    confirmDeleteCategory(category)
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun confirmDeleteCategory(category: String) {
        dialog()
            .setTitle("¿Eliminar «$category»?")
            .setMessage("Sus aplicaciones volverán a «${LauncherPreferences.DEFAULT_CATEGORY}». No se desinstala nada.")
            .setPositiveButton("Eliminar") { _, _ ->
                preferences.deleteCategory(category)
                if (currentFilter == category) currentFilter = FILTER_ALL
                refreshContent("$TAG_CHIP$FILTER_ALL")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun askCategoryName(title: String, initial: String, onName: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Nombre de categoría"
            setText(initial)
            isSingleLine = true
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(160, 175, 172))
        }
        dialog()
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ -> onName(input.text.toString()) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showInputDetails(input: TvInputEntry) {
        val message = if (input.passthrough) {
            "Estado informado por Android: ${TvInputRepository.stateLabel(input.state)}.\n\n" +
                "«Cambiar a esta entrada» envía la petición estándar de Android TV a la aplicación de TV del " +
                "sistema. Pulsa INICIO para volver al launcher."
        } else {
            "Es el sintonizador del televisor. Ábrelo con la aplicación de TV del fabricante."
        }
        val builder = dialog().setTitle(input.label).setMessage(message)
        if (input.passthrough) {
            builder.setPositiveButton("Cambiar a esta entrada") { _, _ ->
                if (!tvInputRepository.switchTo(input)) {
                    Toast.makeText(this, "Este televisor no acepta el cambio de entrada desde aplicaciones.", Toast.LENGTH_LONG).show()
                }
            }
            builder.setNeutralButton("Abrir TV") { _, _ -> openTelevisionApp() }
        } else {
            builder.setPositiveButton("Abrir TV") { _, _ -> openTelevisionApp() }
        }
        builder.setNegativeButton("Cerrar", null).show()
    }

    private fun openTelevisionApp() {
        if (!changhongTvAdapter.openTelevisionApp()) {
            Toast.makeText(this, "No se encontró una aplicación de TV compatible.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showSettings() {
        val options = arrayOf(
            "Elegir fondo",
            "Restaurar fondo original",
            "Brillo del fondo",
            "Administrar categorías",
            "Aplicaciones ocultas",
            "Comprobar actualizaciones",
            "Acerca de NOBLEX LAUNCHER",
            "Volver al launcher original",
        )
        dialog()
            .setTitle("Ajustes")
            .setItems(options) { _, selected ->
                when (selected) {
                    0 -> chooseWallpaper()
                    1 -> restoreWallpaper()
                    2 -> showWallpaperDim()
                    3 -> manageCategories()
                    4 -> manageHiddenApps()
                    5 -> checkForUpdates()
                    6 -> showAbout()
                    7 -> showRestoreInstructions()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        try {
            startActivityForResult(intent, REQUEST_WALLPAPER)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "No hay un selector de imágenes disponible en este dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreWallpaper() {
        preferences.setWallpaperUri(null)
        File(File(filesDir, "wallpapers"), "user-wallpaper.jpg").delete()
        wallpaperBitmap = null
        loadWallpaperIfNeeded()
        Toast.makeText(this, "Fondo original restaurado.", Toast.LENGTH_SHORT).show()
    }

    private fun showWallpaperDim() {
        val slider = SeekBar(this).apply {
            max = LauncherPreferences.MAX_DIM
            progress = preferences.wallpaperDim()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    dimView.alpha = value / 100f
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        dialog()
            .setTitle("Oscurecimiento del fondo")
            .setMessage("Usa ◀ ▶ para ajustar la legibilidad del texto sobre el paisaje.")
            .setView(slider)
            .setPositiveButton("Listo") { _, _ -> preferences.setWallpaperDim(slider.progress) }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { dimView.alpha = preferences.wallpaperDim() / 100f }
            .show()
    }

    private fun manageCategories() {
        val custom = preferences.customCategories()
        val items = custom.toTypedArray()
        val builder = dialog().setTitle("Categorías personalizadas")
        if (items.isEmpty()) {
            builder.setMessage("Aún no hay categorías propias. Las integradas no se pueden modificar.")
        } else {
            builder.setItems(items) { _, index -> showCategoryActions(custom[index]) }
        }
        builder
            .setPositiveButton("Crear categoría") { _, _ ->
                askCategoryName("Nueva categoría", "") { name ->
                    if (preferences.addCategory(name)) refreshContent(null)
                    else Toast.makeText(this, "Nombre no válido o repetido.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun manageHiddenApps() {
        val hidden = preferences.hiddenApps()
        val entries = apps.filter { it.packageName in hidden }
        if (entries.isEmpty()) {
            Toast.makeText(this, "No hay aplicaciones ocultas.", Toast.LENGTH_SHORT).show()
            return
        }
        dialog()
            .setTitle("Aplicaciones ocultas")
            .setItems(entries.map(LaunchableApp::label).toTypedArray()) { _, index ->
                preferences.toggleHidden(entries[index].packageName)
                refreshContent("$TAG_APP${entries[index].packageName}")
                Toast.makeText(this, "${entries[index].label} vuelve a estar visible.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Buscando una versión publicada…", Toast.LENGTH_SHORT).show()
        UpdateManager.checkLatest(this) { result ->
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                result.fold(
                    onSuccess = ::onReleaseFound,
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "No se pudo comprobar la actualización.", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    private fun onReleaseFound(release: LauncherRelease) {
        val decision = try {
            UpdatePolicy.decide(release, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
        } catch (error: IOException) {
            Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            return
        }
        if (decision == UpdateDecision.UP_TO_DATE) {
            Toast.makeText(this, "NOBLEX LAUNCHER está actualizado.", Toast.LENGTH_LONG).show()
            return
        }
        dialog()
            .setTitle("Actualización ${release.versionName}")
            .setMessage(release.releaseNotes.ifBlank { "Hay una nueva versión disponible." })
            .setPositiveButton("Descargar e instalar") { _, _ -> startUpdate(release) }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun startUpdate(release: LauncherRelease) {
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val progressDialog = dialog()
            .setTitle("Descargando actualización")
            .setMessage("Se verifica el archivo antes de abrir el instalador de Android. Android te pedirá confirmar.")
            .setView(progressBar)
            .setCancelable(false)
            .create()
        progressDialog.show()
        UpdateManager.downloadAndInstall(
            this,
            release,
            onProgress = { progress ->
                runOnUiThread {
                    progressBar.isIndeterminate = progress < 0
                    if (progress >= 0) progressBar.progress = progress
                }
            },
            callback = { installResult ->
                runOnUiThread {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    installResult.exceptionOrNull()?.let {
                        Toast.makeText(this, it.message ?: "No se pudo iniciar la actualización.", Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }

    private fun showAbout() {
        dialog()
            .setTitle("NOBLEX LAUNCHER")
            .setMessage(
                "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Android mínimo: API 28\n" +
                    "Proyecto independiente; no está afiliado a Noblex, Changhong, Google ni Android TV.",
            )
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showRestoreInstructions() {
        dialog()
            .setTitle("Volver al launcher original")
            .setMessage(
                "Abre Ajustes de Android → Aplicaciones → Aplicaciones predeterminadas → Aplicación de inicio " +
                    "y selecciona Google TV. NOBLEX LAUNCHER no elimina ni desactiva el launcher original.",
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    private fun launchApp(app: LaunchableApp) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: Intent(Intent.ACTION_MAIN).setComponent(ComponentName(app.packageName, app.activityName))
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(launchIntent)
            preferences.recordLaunch(app.packageName)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "No se pudo abrir ${app.label}.", Toast.LENGTH_LONG).show()
        } catch (error: SecurityException) {
            Toast.makeText(this, "Android no permitió abrir ${app.label}.", Toast.LENGTH_LONG).show()
        }
    }

    // ---------------------------------------------------------------- view helpers

    private fun updateClock() {
        val now = Date()
        clockView.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateView.text = SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(now)
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }

    private fun sectionTitle(title: String): View =
        text(title, 24f, Color.WHITE, bold = true).apply {
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }

    private fun message(value: String): View =
        text(value, 20f, Color.WHITE).apply {
            setPadding(0, dp(34), 0, dp(34))
            setShadowLayer(6f, 0f, 1f, Color.BLACK)
        }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 16f
            isAllCaps = false
            isFocusable = true
            isClickable = true
            setTextColor(Color.WHITE)
            background = getDrawable(R.drawable.focus_chip)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, hasFocus ->
                (view as Button).setTextColor(if (hasFocus) Color.rgb(24, 36, 37) else Color.WHITE)
            }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

    private fun LinearLayout.addView(view: View, marginTop: Int = 0, marginBottom: Int = 0) {
        addView(
            view,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = marginTop
                bottomMargin = marginBottom
            },
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILTER_FAVORITES = "Favoritos"
        private const val FILTER_ALL = "Todas"
        private const val FILTER_INPUTS = "Entradas"
        private const val FILTER_RECENT = "Recientes"
        private const val TAG_APP = "app:"
        private const val TAG_CHIP = "chip:"
        private const val TAG_INPUT = "input:"
        private const val TAG_SETTINGS = "settings"
        private const val FOCUS_FIRST_CARD = "first-card"
        private const val CLOCK_UPDATE_MS = 60_000L
        private const val PACKAGE_CHANGE_DEBOUNCE_MS = 700L
        private const val REQUEST_WALLPAPER = 1201
        private const val MAX_WALLPAPER_WIDTH = 1920
        private const val MAX_WALLPAPER_HEIGHT = 1080
        private const val WALLPAPER_QUALITY = 88
        private const val CARD_WIDTH_DP = 212
        private const val CARD_HEIGHT_DP = 160
        private const val CARD_GAP_DP = 14
        private const val ARTWORK_HEIGHT_DP = 104
    }
}
