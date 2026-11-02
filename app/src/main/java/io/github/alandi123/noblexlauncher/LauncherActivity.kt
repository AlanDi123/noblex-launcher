package io.github.alandi123.noblexlauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.net.ConnectivityManager
import android.net.Network
import android.view.inputmethod.EditorInfo
import android.bluetooth.BluetoothAdapter
import java.io.ByteArrayOutputStream
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
    private val backupManager by lazy { BackupManager(this, preferences) }
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
    private lateinit var wifiPillStatus: TextView
    private lateinit var bluetoothPillStatus: TextView
    private var wallpaperBitmap: Bitmap? = null
    private var firstCardTag: String? = null
    private var started = false
    private var networkCallbackRegistered = false

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

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshStatusPills()
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = postStatusRefresh()
        override fun onLost(network: Network) = postStatusRefresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: android.net.NetworkCapabilities) = postStatusRefresh()
    }

    private fun postStatusRefresh() {
        mainHandler.removeCallbacks(statusRunnable)
        mainHandler.postDelayed(statusRunnable, STATUS_DEBOUNCE_MS)
    }

    private val statusRunnable = Runnable { refreshStatusPills() }

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
        registerReceiver(statusReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        try {
            (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        } catch (_: RuntimeException) {
            networkCallbackRegistered = false
        }
        refreshStatusPills()
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
        mainHandler.removeCallbacks(statusRunnable)
        unregisterReceiver(packageReceiver)
        unregisterReceiver(statusReceiver)
        if (networkCallbackRegistered) {
            try {
                (getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager)?.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
            }
            networkCallbackRegistered = false
        }
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
                    if (filter in preferences.categories()) showCategoryActions(filter) else showSettings()
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
        if (requestCode == REQUEST_HOME_ROLE) {
            val held = HomeRole.state(this) == HomeRole.State.HELD
            toast(
                if (held) {
                    "Rol asignado. Si INICIO sigue abriendo Google TV, es una regla de Android: " +
                        "su prioridad de sistema (2) gana a cualquier launcher instalado."
                } else {
                    "El rol de inicio no cambió."
                },
                long = true,
            )
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQUEST_IMPORT) {
            confirmImport { backupManager.import(readBackupText(uri)) }
            return
        }
        if (requestCode != REQUEST_WALLPAPER) return
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

    private fun readBackupText(uri: Uri): String {
        val output = ByteArrayOutputStream()
        val input = contentResolver.openInputStream(uri) ?: throw IOException("No se pudo leer el archivo.")
        input.use { stream ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > PreferencesBackup.MAX_BYTES) throw IOException("El archivo de respaldo es demasiado grande.")
            }
        }
        return output.toString(Charsets.UTF_8.name())
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
        updateBadge = text("", 13f, Color.rgb(190, 226, 205), bold = true).apply { visibility = View.GONE }
        brand.addView(updateBadge)
        row.addView(brand)
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val wifi = statusPill("Wi-Fi", R.drawable.ic_wifi, TAG_WIFI) { showWifi() }
        wifiPillStatus = wifi.second
        row.addView(wifi.first, LinearLayout.LayoutParams(dp(196), dp(52)).apply { marginEnd = dp(12) })
        val bluetooth = statusPill("Bluetooth", R.drawable.ic_bluetooth, TAG_BLUETOOTH) { showBluetooth() }
        bluetoothPillStatus = bluetooth.second
        row.addView(bluetooth.first, LinearLayout.LayoutParams(dp(176), dp(52)).apply { marginEnd = dp(12) })

        val settings = statusPill("Ajustes", R.drawable.ic_settings, TAG_SETTINGS) { showSettings() }
        settings.second.visibility = View.GONE
        settings.first.contentDescription = "Ajustes"
        row.addView(settings.first, LinearLayout.LayoutParams(dp(132), dp(52)).apply { marginEnd = dp(26) })

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

    /** Focusable header control: icon, name and (optionally) a live status line. */
    private fun statusPill(title: String, icon: Int, tag: String, onClick: () -> Unit): Pair<View, TextView> {
        val glyph = ImageView(this).apply {
            setImageResource(icon)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            imageTintList = ColorStateList.valueOf(Color.rgb(228, 238, 234))
        }
        val name = text(title, 15f, Color.WHITE, bold = true)
        val status = text("", 12f, Color.rgb(200, 214, 211)).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(status)
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            this.tag = tag
            background = getDrawable(R.drawable.focus_chip)
            setPadding(dp(14), 0, dp(12), 0)
            setOnClickListener { onClick() }
            setOnFocusChangeListener { _, focused ->
                val ink = if (focused) Color.rgb(24, 36, 37) else Color.WHITE
                name.setTextColor(ink)
                status.setTextColor(if (focused) Color.rgb(48, 66, 64) else Color.rgb(200, 214, 211))
                glyph.imageTintList = ColorStateList.valueOf(if (focused) ink else Color.rgb(228, 238, 234))
            }
        }
        pill.addView(glyph, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(10) })
        pill.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return pill to status
    }

    private fun refreshStatusPills() {
        if (!::wifiPillStatus.isInitialized) return
        val network = ConnectivityStatus.network(this)
        val wifi = ConnectivityStatus.wifiText(network)
        wifiPillStatus.text = wifi
        (wifiPillStatus.parent.parent as View).contentDescription = "Wi-Fi: $wifi"
        val bluetooth = ConnectivityStatus.bluetoothText(ConnectivityStatus.bluetooth())
        bluetoothPillStatus.text = bluetooth
        (bluetoothPillStatus.parent.parent as View).contentDescription = "Bluetooth: $bluetooth"
    }

    /** Rebuilds only the lower area; the wallpaper, clock and header persist across rebuilds. */
    private fun refreshContent(focusTag: String?) {
        contentHost.removeAllViews()
        firstCardTag = null
        contentHost.addView(sectionTitle(when (currentFilter) {
            FILTER_INPUTS -> "Entradas de TV"
            FILTER_TOOLS -> "Herramientas"
            else -> "Tus aplicaciones"
        }))
        contentHost.addView(buildFilters(), marginBottom = dp(6))
        when {
            currentFilter == FILTER_INPUTS -> contentHost.addView(buildInputRow())
            !appsLoaded -> contentHost.addView(message("Cargando aplicaciones…"))
            else -> contentHost.addView(buildAppRow())
        }
        contentHost.addView(
            text("MENU: organizar  ·  ATRÁS: volver a Todas  ·  INICIO: volver aquí", 15f, Color.rgb(205, 218, 215)),
            marginTop = dp(10),
        )
        updateClock()
        refreshStatusPills()
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
            chip.setPadding(dp(22), 0, dp(22), 0)
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
        val validCategories = preferences.categories()
        return when (currentFilter) {
            FILTER_RECENT -> candidates.filter { it.packageName in recents }.sortedBy { recents.indexOf(it.packageName) }
            else -> candidates
                .filter { app ->
                    when (currentFilter) {
                        FILTER_FAVORITES -> app.packageName in favorites
                        FILTER_ALL -> true
                        else -> categoryOf(app, validCategories) == currentFilter
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
        if (visibleApps.isEmpty() && currentFilter != FILTER_TOOLS) {
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
        if (currentFilter == FILTER_TOOLS) {
            tools().forEach { tool ->
                val card = createToolCard(tool)
                if (firstCardTag == null) firstCardTag = card.tag as String
                row.addView(card, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply {
                    marginEnd = dp(CARD_GAP_DP)
                })
            }
        }
        visibleApps.forEachIndexed { index, app ->
            val card = createAppCard(app, app.packageName in favorites)
            if (firstCardTag == null) firstCardTag = card.tag as String
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
            setOnFocusChangeListener { view, hasFocus -> focusScale(view, hasFocus) }
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

    private fun focusScale(view: View, hasFocus: Boolean) {
        view.animate().cancel()
        view.animate().scaleX(if (hasFocus) 1.06f else 1f).scaleY(if (hasFocus) 1.06f else 1f).setDuration(120L)
        view.elevation = if (hasFocus) dp(8).toFloat() else 0f
    }

    private fun iconCard(tag: String, title: String, subtitle: String, icon: Int, description: String, onClick: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.focus_card)
            setPadding(dp(10), dp(10), dp(10), dp(8))
            contentDescription = description
            this.tag = tag
            setOnFocusChangeListener { view, hasFocus -> focusScale(view, hasFocus) }
            setOnClickListener { onClick() }
        }
        val artwork = ImageView(this).apply {
            background = getDrawable(R.drawable.card_tile)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
            setImageResource(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(40), dp(24), dp(40), dp(24))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        card.addView(artwork, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(ARTWORK_HEIGHT_DP)))
        card.addView(text(title, 16f, Color.WHITE, bold = true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        card.addView(text(subtitle, 12f, Color.rgb(205, 218, 215)).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        return card
    }

    private fun createToolCard(tool: Tool): View =
        iconCard("$TAG_TOOL${tool.id}", tool.title, tool.subtitle, tool.icon, "${tool.title}. ${tool.subtitle}") { tool.action() }

    private fun buildInputRow(): View {
        val inputs = tvInputRepository.discover()
        val scroll = cardScroll()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
        }
        val live = iconCard("${TAG_INPUT}live-tv", "TV en vivo", "Aplicación de TV", R.drawable.ic_tv, "TV en vivo") { openTelevisionApp() }
        firstCardTag = live.tag as String
        row.addView(live, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply { marginEnd = dp(CARD_GAP_DP) })
        inputs.forEach { input ->
            val state = TvInputRepository.stateLabel(input.state)
            val card = iconCard(
                "$TAG_INPUT${input.id}", input.label, state,
                if (input.passthrough) R.drawable.ic_input else R.drawable.ic_tv,
                "${input.label}, $state",
            ) { showInputDetails(input) }
            row.addView(card, LinearLayout.LayoutParams(dp(CARD_WIDTH_DP), dp(CARD_HEIGHT_DP)).apply { marginEnd = dp(CARD_GAP_DP) })
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
        updateBadge.text = "Actualización ${release?.optString("version").orEmpty()} disponible"
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

    // ---------------------------------------------------------------- panels

    private fun panel() = PanelDialog(this)

    private fun toast(message: String, long: Boolean = false) =
        Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    private fun categoryOf(app: LaunchableApp, valid: Collection<String> = preferences.categories()): String =
        preferences.category(app.packageName, LauncherLogic.defaultCategory(app.packageName, app.system), valid)

    private fun showAppActions(app: LaunchableApp) {
        val tag = "$TAG_APP${app.packageName}"
        val favorite = preferences.isFavorite(app.packageName)
        val hidden = app.packageName in preferences.hiddenApps()
        panel()
            .title(app.label)
            .message("Categoría: ${categoryOf(app)}")
            .item(if (favorite) "Quitar de Favoritos" else "Agregar a Favoritos") {
                preferences.toggleFavorite(app.packageName)
                refreshContent(tag)
            }
            .item("Asignar categoría") { chooseCategory(app) }
            .item("Mover antes") { moveApp(app, -1); refreshContent(tag) }
            .item("Mover después") { moveApp(app, 1); refreshContent(tag) }
            .item(if (hidden) "Mostrar aplicación" else "Ocultar aplicación", if (hidden) null else "Se puede restaurar en Ajustes → Aplicaciones ocultas") {
                preferences.toggleHidden(app.packageName)
                refreshContent(null)
            }
            .show()
    }

    private fun moveApp(app: LaunchableApp, direction: Int) {
        val visible = visibleApps()
        val index = visible.indexOfFirst { it.packageName == app.packageName }
        val neighbor = visible.getOrNull(index + direction)
        if (index < 0 || neighbor == null) {
            toast("No se puede mover más en esa dirección.")
            return
        }
        val favorites = preferences.favorites()
        if (currentFilter == FILTER_RECENT) {
            toast("Recientes se ordena por uso; usa Todas para reordenar.")
            return
        }
        if ((app.packageName in favorites) != (neighbor.packageName in favorites)) {
            toast("Los favoritos siempre aparecen primero.")
            return
        }
        preferences.move(app.packageName, neighbor.packageName, apps.map(LaunchableApp::packageName))
    }

    private fun chooseCategory(app: LaunchableApp) {
        val categories = preferences.categories()
        val current = categoryOf(app, categories)
        val builder = panel().title("Categoría para ${app.label}")
        categories.forEach { name ->
            builder.item(name, if (name == current) "Categoría actual" else null) {
                preferences.setCategory(app.packageName, name)
                refreshContent("$TAG_APP${app.packageName}")
            }
        }
        builder.item("Crear categoría nueva…") {
            askCategoryName("Nueva categoría", "") { name ->
                if (preferences.addCategory(name)) {
                    val stored = preferences.categories().last { it.equals(name.trim(), ignoreCase = true) }
                    preferences.setCategory(app.packageName, stored)
                    refreshContent("$TAG_APP${app.packageName}")
                } else {
                    toast("Nombre no válido o repetido.", long = true)
                }
            }
        }
        builder.show()
    }

    /** Full category management screen: order, create, rename and delete. */
    private fun manageCategories(focusName: String? = null) {
        val categories = preferences.categories()
        val valid = categories.toSet()
        val counts = apps.groupingBy { categoryOf(it, valid) }.eachCount()
        val builder = panel().title("Categorías").width(700)
        builder.item("Crear categoría nueva", "Agrupa tus aplicaciones a tu manera") {
            askCategoryName("Nueva categoría", "") { name ->
                if (preferences.addCategory(name)) {
                    refreshContent(null)
                    manageCategories(name.trim())
                } else {
                    toast("Nombre no válido o repetido.", long = true)
                    manageCategories()
                }
            }
        }
        categories.forEach { name ->
            val kind = if (preferences.isBuiltInCategory(name)) "integrada" else "personalizada"
            builder.item(name, "${counts[name] ?: 0} aplicaciones · $kind") { showCategoryActions(name) }
        }
        builder.negative("Cerrar") { refreshContent(null) }.show()
    }

    private fun showCategoryActions(category: String) {
        val builtIn = preferences.isBuiltInCategory(category)
        val builder = panel().title(category)
            .message(if (builtIn) "Categoría integrada: puedes cambiar su posición pero no su nombre." else "Categoría personalizada.")
            .item("Mover antes") {
                if (!preferences.moveCategory(category, -1)) toast("Ya está al principio.")
                refreshContent(null)
                manageCategories()
            }
            .item("Mover después") {
                if (!preferences.moveCategory(category, 1)) toast("Ya está al final.")
                refreshContent(null)
                manageCategories()
            }
        if (!builtIn) {
            builder.item("Renombrar categoría") {
                askCategoryName("Renombrar categoría", category) { name ->
                    if (preferences.renameCategory(category, name)) {
                        val renamed = LauncherLogic.normalizeCategory(name, emptyList()) ?: category
                        if (currentFilter == category) currentFilter = renamed
                        refreshContent(null)
                    } else {
                        toast("Nombre no válido o repetido.", long = true)
                    }
                    manageCategories()
                }
            }
            builder.item("Eliminar categoría", "Sus aplicaciones vuelven a su categoría automática") { confirmDeleteCategory(category) }
        }
        builder.negative("Volver") { manageCategories() }.show()
    }

    private fun confirmDeleteCategory(category: String) {
        panel()
            .title("¿Eliminar «$category»?")
            .message("Sus aplicaciones volverán a su categoría automática. No se desinstala nada.")
            .positive("Eliminar") {
                preferences.deleteCategory(category)
                if (currentFilter == category) currentFilter = FILTER_ALL
                refreshContent(null)
                manageCategories()
            }
            .negative("Cancelar") { manageCategories() }
            .show()
    }

    private fun askCategoryName(title: String, initial: String, onName: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Nombre de categoría"
            setText(initial)
            isSingleLine = true
            textSize = 20f
            setTextColor(Color.WHITE)
            setHintTextColor(Color.rgb(160, 175, 172))
            background = getDrawable(R.drawable.field_background)
            setPadding(dp(18), dp(12), dp(18), dp(12))
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val dialog = panel()
            .title(title)
            .message("Máximo ${LauncherLogic.MAX_CATEGORY_LENGTH} caracteres.")
            .view(input)
            .positive("Guardar") { onName(input.text.toString()) }
            .negative("Cancelar") { if (title.startsWith("Renombrar")) manageCategories() }
            .show()
        // The on-screen keyboard owns DPAD focus while typing, so its Done key must save.
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                dialog.dismiss()
                onName(input.text.toString())
                true
            } else {
                false
            }
        }
    }

    // ---- connectivity

    private fun showWifi() {
        val network = ConnectivityStatus.network(this)
        panel()
            .title("Wi-Fi")
            .message(
                "Estado: ${ConnectivityStatus.wifiText(network)}.\n\n" +
                    "Android no entrega el nombre de la red a aplicaciones sin permiso de ubicación, por eso no se muestra. " +
                    "NOBLEX LAUNCHER nunca cambia tu red: los cambios se hacen en los ajustes oficiales de Android.",
            )
            .positive("Abrir ajustes de Wi-Fi") {
                if (!SystemShortcuts.openWifi(this)) toast("Este televisor no expone los ajustes de Wi-Fi.", long = true)
            }
            .show()
    }

    private fun showBluetooth() {
        val state = ConnectivityStatus.bluetooth()
        panel()
            .title("Bluetooth")
            .message(
                "Adaptador: ${ConnectivityStatus.bluetoothText(state)}.\n\n" +
                    "«Agregar accesorio» abre la pantalla oficial de Android TV que busca mandos, teclados o audio. " +
                    "No se empareja nada sin que lo elijas, y tus dispositivos ya emparejados no se modifican. " +
                    "Para olvidar o revisar dispositivos usa los ajustes de Android.",
            )
            .positive("Agregar accesorio") {
                if (!SystemShortcuts.openBluetooth(this)) toast("Este televisor no expone la configuración Bluetooth.", long = true)
            }
            .neutral("Ajustes de Android") {
                if (!SystemShortcuts.openSettings(this)) toast("No se pudieron abrir los ajustes de Android.", long = true)
            }
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
        val builder = panel().title(input.label).message(message)
        if (input.passthrough) {
            builder.positive("Cambiar a esta entrada") {
                if (!tvInputRepository.switchTo(input)) {
                    toast("Este televisor no acepta el cambio de entrada desde aplicaciones.", long = true)
                }
            }
            builder.neutral("Abrir TV") { openTelevisionApp() }
        } else {
            builder.positive("Abrir TV") { openTelevisionApp() }
        }
        builder.negative("Cerrar").show()
    }

    private fun openTelevisionApp() {
        if (!changhongTvAdapter.openTelevisionApp()) toast("No se encontró una aplicación de TV compatible.", long = true)
    }

    // ---- settings

    private fun showSettings() {
        panel()
            .title("Ajustes")
            .item("Fondo y brillo", "Imagen del paisaje y legibilidad") { showWallpaperSettings() }
            .item("Categorías", "Crear, renombrar, ordenar y eliminar") { manageCategories() }
            .item("Aplicaciones ocultas", "Mostrar de nuevo lo que ocultaste") { manageHiddenApps() }
            .item("Respaldo y migración", "Exportar o importar tus preferencias") { showBackup() }
            .item("Centro de actualizaciones", "Versión ${BuildConfig.VERSION_NAME}") { showUpdateCenter() }
            .item("Acerca de NOBLEX LAUNCHER") { showAbout() }
            .item("Establecer como launcher predeterminado", homeRoleSubtitle()) { requestHomeRole() }
            .item("Volver al launcher original", "Cómo restaurar Google TV como inicio") { showRestoreInstructions() }
            .show()
    }

    private fun homeRoleSubtitle(): String = when (HomeRole.state(this)) {
        HomeRole.State.HELD -> "NOBLEX ya es el launcher predeterminado"
        HomeRole.State.AVAILABLE -> "Solicita el cambio a Android"
        HomeRole.State.UNSUPPORTED -> "Este televisor no ofrece esta opción"
    }

    private fun requestHomeRole() {
        val state = HomeRole.state(this)
        val intent = HomeRole.requestIntent(this)
        if (intent == null) {
            val text = if (state == HomeRole.State.HELD) {
                "NOBLEX LAUNCHER ya tiene el rol de inicio."
            } else {
                "El firmware de este televisor no ofrece el rol de inicio de Android, por lo que el cambio no puede " +
                    "solicitarse desde la aplicación. Google TV sigue siendo el inicio."
            }
            panel().title("Launcher predeterminado").message(text).noNegative().positive("Cerrar").show()
            return
        }
        try {
            startActivityForResult(intent, REQUEST_HOME_ROLE)
        } catch (error: ActivityNotFoundException) {
            panel().title("Launcher predeterminado")
                .message("Android no incluye la pantalla de solicitud de rol en este televisor. Google TV sigue siendo el inicio.")
                .noNegative().positive("Cerrar").show()
        }
    }

    private fun showWallpaperSettings() {
        panel()
            .title("Fondo y brillo")
            .item("Elegir imagen", "Desde el almacenamiento del televisor") { chooseWallpaper() }
            .item("Brillo del fondo", "Oscurecimiento actual: ${preferences.wallpaperDim()} %") { showWallpaperDim() }
            .item("Restaurar fondo original", "Montañas y bosque incluidos") { restoreWallpaper() }
            .negative("Volver") { showSettings() }
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
            toast("No hay un selector de imágenes disponible en este dispositivo.", long = true)
        }
    }

    private fun restoreWallpaper() {
        preferences.setWallpaperUri(null)
        File(File(filesDir, "wallpapers"), "user-wallpaper.jpg").delete()
        wallpaperBitmap = null
        loadWallpaperIfNeeded()
        toast("Fondo original restaurado.")
    }

    private fun showWallpaperDim() {
        val value = text("", 18f, Color.WHITE)
        fun render(progress: Int) { value.text = "Oscurecimiento: $progress %" }
        val slider = SeekBar(this).apply {
            max = LauncherPreferences.MAX_DIM
            progress = preferences.wallpaperDim()
            keyProgressIncrement = 2
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    dimView.alpha = value / 100f
                    render(value)
                }

                override fun onStartTrackingTouch(bar: SeekBar) = Unit

                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        render(slider.progress)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(value)
            addView(slider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        }
        panel()
            .title("Brillo del fondo")
            .message("Usa ◀ ▶ para ajustar la legibilidad del texto sobre el paisaje. Menos oscurecimiento = fondo más luminoso.")
            .view(content)
            .positive("Listo") { preferences.setWallpaperDim(slider.progress); showWallpaperSettings() }
            .negative("Cancelar") { showWallpaperSettings() }
            .onDismiss { dimView.alpha = preferences.wallpaperDim() / 100f }
            .show()
    }

    private fun manageHiddenApps() {
        val hidden = preferences.hiddenApps()
        val entries = apps.filter { it.packageName in hidden }
        if (entries.isEmpty()) {
            panel().title("Aplicaciones ocultas").message("No hay aplicaciones ocultas.").negative("Volver") { showSettings() }.show()
            return
        }
        val builder = panel().title("Aplicaciones ocultas").message("Elige una para mostrarla de nuevo.")
        entries.forEach { app ->
            builder.item(app.label, "Mostrar de nuevo") {
                preferences.toggleHidden(app.packageName)
                refreshContent("$TAG_APP${app.packageName}")
                toast("${app.label} vuelve a estar visible.")
                manageHiddenApps()
            }
        }
        builder.negative("Volver") { showSettings() }.show()
    }

    // ---- backup

    private fun showBackup() {
        panel()
            .title("Respaldo y migración")
            .message(
                "Guarda favoritos, categorías, orden, aplicaciones ocultas, fondo y brillo en un archivo local. " +
                    "Sirve para pasar tus preferencias del paquete de prueba (.debug) a la versión definitiva, " +
                    "porque Android no migra datos entre aplicaciones distintas. El archivo nunca se sube a internet.",
            )
            .item("Exportar preferencias") { exportBackup() }
            .item("Importar desde esta aplicación", "Respaldos guardados por este paquete") { importFromOwnFolder() }
            .item("Importar desde otro archivo…", "Por ejemplo Download/NoblexLauncher") { pickBackupFile() }
            .negative("Volver") { showSettings() }
            .show()
    }

    private fun exportBackup() {
        runOnWorker {
            val result = try {
                Result.success(backupManager.export())
            } catch (error: IOException) {
                Result.failure(error)
            }
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                result.fold(
                    onSuccess = {
                        panel().title("Respaldo guardado")
                            .message(
                                "Archivo: ${it.appFile.name}\nCarpeta de la aplicación: Android/data/$packageName/files/backup/" +
                                    (it.publicLocation?.let { path -> "\nCopia compartida: $path" } ?: ""),
                            )
                            .noNegative().positive("Entendido").show()
                    },
                    onFailure = { toast(it.message ?: "No se pudo exportar.", long = true) },
                )
            }
        }
    }

    private fun importFromOwnFolder() {
        val files = backupManager.availableBackups()
        if (files.isEmpty()) {
            toast("No hay respaldos guardados por esta aplicación.", long = true)
            return
        }
        val builder = panel().title("Elegir respaldo")
        files.take(8).forEach { file ->
            builder.item(file.name, SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified()))) {
                confirmImport { backupManager.import(file) }
            }
        }
        builder.show()
    }

    private fun pickBackupFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT)
        } catch (error: ActivityNotFoundException) {
            toast("No hay un selector de archivos disponible en este dispositivo.", long = true)
        }
    }

    private fun confirmImport(restore: () -> Int) {
        panel()
            .title("¿Importar preferencias?")
            .message("Reemplaza tus favoritos, categorías, orden y ajustes actuales por los del respaldo.")
            .positive("Importar") {
                runOnWorker {
                    val result = try {
                        Result.success(restore())
                    } catch (error: IOException) {
                        Result.failure(error)
                    }
                    runOnUiThread {
                        if (destroyed) return@runOnUiThread
                        result.fold(
                            onSuccess = { count ->
                                dimView.alpha = preferences.wallpaperDim() / 100f
                                wallpaperBitmap = null
                                loadWallpaperIfNeeded()
                                currentFilter = FILTER_ALL
                                refreshContent(FOCUS_FIRST_CARD)
                                toast("Preferencias importadas ($count elementos).", long = true)
                            },
                            onFailure = { toast(it.message ?: "No se pudo importar.", long = true) },
                        )
                    }
                }
            }
            .negative("Cancelar")
            .show()
    }

    // ---- updates and about

    private fun showUpdateCenter() {
        panel()
            .title("Centro de actualizaciones")
            .message(
                "Versión instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                    "Solo se aceptan versiones publicadas oficialmente y firmadas con la clave del proyecto. " +
                    "Android te pedirá confirmar cada instalación. Nada se descarga sin que lo pidas aquí.",
            )
            .positive("Buscar actualización") { checkForUpdates() }
            .negative("Volver") { showSettings() }
            .show()
    }

    private fun checkForUpdates() {
        toast("Buscando una versión publicada…")
        UpdateManager.checkLatest(this) { result ->
            runOnUiThread {
                if (destroyed) return@runOnUiThread
                result.fold(
                    onSuccess = ::onReleaseFound,
                    onFailure = { error ->
                        panel().title("Actualizaciones")
                            .message(error.message ?: "No se pudo comprobar la actualización.")
                            .noNegative().positive("Entendido") { showUpdateCenter() }.show()
                    },
                )
            }
        }
    }

    private fun onReleaseFound(release: LauncherRelease) {
        val decision = try {
            UpdatePolicy.decide(release, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong())
        } catch (error: IOException) {
            toast(error.message ?: "Actualización rechazada.", long = true)
            return
        }
        if (decision == UpdateDecision.UP_TO_DATE) {
            panel().title("Actualizaciones").message("NOBLEX LAUNCHER está actualizado.")
                .noNegative().positive("Entendido").show()
            return
        }
        panel()
            .title("Actualización ${release.versionName}")
            .message(release.releaseNotes.ifBlank { "Hay una nueva versión disponible." })
            .positive("Descargar e instalar") { startUpdate(release) }
            .negative("Más tarde")
            .show()
    }

    private fun startUpdate(release: LauncherRelease) {
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val progressDialog = panel()
            .title("Descargando actualización")
            .message("Se verifica el archivo antes de abrir el instalador de Android. Android te pedirá confirmar.")
            .view(progressBar)
            .noNegative()
            .cancelable(false)
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
                    installResult.exceptionOrNull()?.let { toast(it.message ?: "No se pudo iniciar la actualización.", long = true) }
                }
            },
        )
    }

    private fun showAbout() {
        panel()
            .title("NOBLEX LAUNCHER")
            .message(
                "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                    "Paquete: $packageName\n" +
                    "Android mínimo: API 28\n\n" +
                    "Candidata a versión. Proyecto independiente; no está afiliado a Noblex, Changhong, Google ni Android TV. " +
                    "Sin anuncios, sin recomendaciones, sin rastreo.",
            )
            .noNegative().positive("Cerrar")
            .show()
    }

    private fun showRestoreInstructions() {
        panel()
            .title("Volver al launcher original")
            .message(
                "NOBLEX LAUNCHER no elimina ni desactiva Google TV. Para que el inicio vuelva a ser Google TV: " +
                    "abre «Ajustes de inicio» y elige Google TV (si tu televisor no lo permite, Ajustes de Android → " +
                    "Aplicaciones → Ver todas → NOBLEX LAUNCHER → Borrar valores predeterminados). " +
                    "Desde un computador también puedes ejecutar scripts/restore_google_home.sh.",
            )
            .positive("Abrir ajustes de inicio") {
                if (!SystemShortcuts.openHomeSettings(this) && !SystemShortcuts.openSettings(this)) {
                    toast("No se pudieron abrir los ajustes de Android.", long = true)
                }
            }
            .negative("Cerrar")
            .show()
    }

    // ---- tools

    private class Tool(val id: String, val title: String, val subtitle: String, val icon: Int, val action: () -> Unit)

    private fun tools(): List<Tool> {
        val network = ConnectivityStatus.network(this)
        return listOf(
            Tool("wifi", "Wi-Fi", ConnectivityStatus.wifiText(network), R.drawable.ic_wifi, ::showWifi),
            Tool("bluetooth", "Bluetooth", ConnectivityStatus.bluetoothText(ConnectivityStatus.bluetooth()), R.drawable.ic_bluetooth, ::showBluetooth),
            Tool("tv", "TV en vivo", "Aplicación de TV", R.drawable.ic_tv, ::openTelevisionApp),
            Tool("inputs", "Entradas HDMI", "Fuentes de video", R.drawable.ic_input) {
                currentFilter = FILTER_INPUTS
                refreshContent("$TAG_CHIP$FILTER_INPUTS")
            },
            Tool("settings", "Ajustes Android", "Sistema del televisor", R.drawable.ic_settings) {
                if (!SystemShortcuts.openSettings(this)) toast("No se pudieron abrir los ajustes de Android.", long = true)
            },
            Tool("installed", "Aplicaciones", "Administrar instaladas", R.drawable.ic_apps) {
                if (!SystemShortcuts.openInstalledApps(this)) toast("No se pudo abrir la lista de aplicaciones.", long = true)
            },
            Tool("update", "Actualizaciones", "Versión ${BuildConfig.VERSION_NAME}", R.drawable.ic_update, ::showUpdateCenter),
            Tool("about", "Acerca de", "NOBLEX LAUNCHER", R.drawable.ic_info, ::showAbout),
        )
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
            minimumHeight = dp(176)
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
        private const val FILTER_TOOLS = "Herramientas"
        private const val FILTER_RECENT = "Recientes"
        private const val TAG_APP = "app:"
        private const val TAG_CHIP = "chip:"
        private const val TAG_INPUT = "input:"
        private const val TAG_TOOL = "tool:"
        private const val TAG_WIFI = "pill:wifi"
        private const val TAG_BLUETOOTH = "pill:bluetooth"
        private const val TAG_SETTINGS = "settings"
        private const val STATUS_DEBOUNCE_MS = 400L
        private const val REQUEST_IMPORT = 1202
        private const val REQUEST_HOME_ROLE = 1203
        private const val FOCUS_FIRST_CARD = "first-card"
        private const val CLOCK_UPDATE_MS = 60_000L
        private const val PACKAGE_CHANGE_DEBOUNCE_MS = 700L
        private const val REQUEST_WALLPAPER = 1201
        private const val MAX_WALLPAPER_WIDTH = 1920
        private const val MAX_WALLPAPER_HEIGHT = 1080
        private const val WALLPAPER_QUALITY = 88
        private const val CARD_WIDTH_DP = 212
        private const val CARD_HEIGHT_DP = 176
        private const val CARD_GAP_DP = 14
        private const val ARTWORK_HEIGHT_DP = 108
    }
}
