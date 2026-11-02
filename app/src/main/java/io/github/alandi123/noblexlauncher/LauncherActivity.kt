package io.github.alandi123.noblexlauncher

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
import java.util.concurrent.Executors

class LauncherActivity : Activity() {
    private val preferences by lazy { LauncherPreferences(this) }
    private val appDiscovery by lazy { AppDiscovery(this) }
    private val tvInputRepository by lazy { TvInputRepository(this) }
    private val changhongTvAdapter by lazy { ChanghongTvAdapter(this) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val wallpaperExecutor = Executors.newSingleThreadExecutor()
    private var apps = emptyList<LaunchableApp>()
    private var currentFilter = FILTER_ALL
    private var focusedPackage: String? = null
    private var wallpaperOverlay: View? = null
    private var clockView: TextView? = null
    private var dateView: TextView? = null

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClock()
            mainHandler.postDelayed(this, CLOCK_UPDATE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        apps = appDiscovery.discover()
        buildDashboard()
        mainHandler.post(clockRunnable)
        UpdateScheduler.schedule(this)
    }

    override fun onResume() {
        super.onResume()
        apps = appDiscovery.discover()
        buildDashboard()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(clockRunnable)
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            val selected = focusedPackage ?: (currentFocus?.tag as? String)
            val selectedApp = selected?.let { packageName ->
                apps.firstOrNull { it.packageName == packageName }
            }
            if (selectedApp != null) showAppActions(selectedApp) else showSettings()
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
        wallpaperExecutor.execute {
            val result = try {
                Result.success(saveWallpaper(uri))
            } catch (error: IOException) {
                Result.failure<File>(error)
            } catch (error: SecurityException) {
                Result.failure<File>(IOException("Android no permitió leer la imagen seleccionada.", error))
            }
            runOnUiThread {
                result.fold(
                    onSuccess = { file ->
                        preferences.setWallpaperUri(Uri.fromFile(file).toString())
                        buildDashboard()
                    },
                    onFailure = { error ->
                        Toast.makeText(this, error.message ?: "No se pudo guardar el fondo.", Toast.LENGTH_LONG).show()
                    },
                )
            }
        }
    }

    private fun buildDashboard() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(16, 23, 25))
        }

        root.addView(
            ImageView(this).apply {
                preferences.wallpaperUri()?.let { setImageURI(Uri.parse(it)) }
                    ?: setImageResource(R.drawable.wallpaper)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = null
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        wallpaperOverlay = View(this).apply {
            setBackgroundColor(Color.BLACK)
            alpha = preferences.wallpaperDim() / 100f
        }
        root.addView(
            wallpaperOverlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(30), dp(48), dp(26))
        }
        root.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        content.addView(buildHeader())
        addSectionTitle(content, "Tus aplicaciones")
        content.addView(buildFilters(), marginBottom = dp(8))
        if (currentFilter == FILTER_INPUTS) {
            addSectionTitle(content, "Entradas disponibles")
            content.addView(buildInputRow())
        } else {
            content.addView(buildAppRow())
        }

        val footer = text(
            "Pulsa MENU para organizar una aplicación",
            14f,
            Color.rgb(201, 213, 211),
        )
        content.addView(footer, marginTop = dp(16))
        setContentView(root)
        updateClock()
    }

    private fun buildHeader(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        brand.addView(text("NOBLEX", 24f, Color.WHITE, bold = true))
        brand.addView(text("L A U N C H E R", 12f, Color.rgb(187, 203, 200)))
        row.addView(brand)
        val savedRelease = preferences.savedReleaseManifest()
        if (savedRelease != null && savedRelease.optLong("versionCode", 0L) > BuildConfig.VERSION_CODE) {
            val update = text("Actualización disponible", 14f, Color.rgb(219, 230, 226), bold = true)
            val updateParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            updateParams.marginStart = dp(24)
            row.addView(update, updateParams)
        }
        row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val date = text("", 17f, Color.rgb(223, 231, 229))
        date.gravity = Gravity.CENTER_VERTICAL or Gravity.END
        row.addView(date, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)))
        dateView = date

        val clock = text("", 30f, Color.WHITE, bold = true)
        clock.gravity = Gravity.CENTER
        val clockParams = LinearLayout.LayoutParams(dp(112), dp(56))
        clockParams.marginStart = dp(22)
        row.addView(clock, clockParams)
        clockView = clock

        val settings = button("Ajustes") {
            showSettings()
        }
        val settingsParams = LinearLayout.LayoutParams(dp(110), dp(48))
        settingsParams.marginStart = dp(20)
        row.addView(settings, settingsParams)
        return row
    }

    private fun buildFilters(): View {
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
        }
        val filters = listOf(FILTER_FAVORITES, FILTER_ALL, FILTER_RECENT) +
            preferences.categories() + FILTER_INPUTS
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        filters.forEach { filter ->
            val chip = button(filter) {
                currentFilter = filter
                buildDashboard()
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44))
            params.marginEnd = dp(9)
            row.addView(chip, params)
            chip.isSelected = currentFilter == filter
            chip.contentDescription = if (currentFilter == filter) "$filter, seleccionado" else filter
        }
        scroll.addView(row)
        return scroll
    }

    private fun buildAppRow(): View {
        val hidden = preferences.hiddenApps()
        val favorites = preferences.favorites()
        val order = preferences.orderedPackages()
        val visibleApps = apps
            .filterNot { it.packageName in hidden }
            .filter { app ->
                when (currentFilter) {
                    FILTER_FAVORITES -> app.packageName in favorites
                    FILTER_ALL -> true
                    FILTER_RECENT -> app.packageName in preferences.recentPackages()
                    else -> preferences.category(app.packageName) == currentFilter
                }
            }
            .sortedWith(
                compareByDescending<LaunchableApp> { it.packageName in favorites }
                    .thenBy { order.indexOf(it.packageName).let { index -> if (index < 0) Int.MAX_VALUE else index } }
                    .thenBy { it.label.lowercase() },
            )

        if (visibleApps.isEmpty()) {
            return text(
                if (currentFilter == FILTER_FAVORITES) {
                    "Agrega aplicaciones a Favoritos con MENU."
                } else {
                    "No hay aplicaciones en esta categoría todavía."
                },
                20f,
                Color.WHITE,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(34), 0, dp(34))
            }
        }

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(8))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        visibleApps.forEach { app ->
            val card = createAppCard(app)
            val params = LinearLayout.LayoutParams(dp(166), dp(174))
            params.marginEnd = dp(14)
            row.addView(card, params)
        }
        scroll.addView(row)
        return scroll
    }

    private fun createAppCard(app: LaunchableApp): View {
        val label = text(
            app.label + if (app.packageName in preferences.favorites()) "  ★" else "",
            15f,
            Color.WHITE,
            bold = true,
        ).apply {
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isFocusable = true
            isClickable = true
            background = getDrawable(R.drawable.focus_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            contentDescription = app.label
            setOnFocusChangeListener { _, hasFocus ->
                focusedPackage = if (hasFocus) app.packageName else null
                val color = if (hasFocus) Color.rgb(26, 39, 40) else Color.WHITE
                label.setTextColor(color)
                animate().cancel()
                animate()
                    .scaleX(if (hasFocus) 1.04f else 1f)
                    .scaleY(if (hasFocus) 1.04f else 1f)
                    .setDuration(120L)
            }
            setOnClickListener { launchApp(app) }
            setOnLongClickListener {
                showAppActions(app)
                true
            }
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = null
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(72), dp(72)))

        card.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
            topMargin = dp(12)
        })
        card.tag = app.packageName
        return card
    }

    private fun buildInputRow(): View {
        val inputs = tvInputRepository.discover()
        if (inputs.isEmpty()) {
            return text("Android no informó entradas de TV disponibles.", 19f, Color.WHITE)
                .apply { setPadding(0, dp(22), 0, dp(22)) }
        }
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(8))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        inputs.forEach { input ->
            val card = button(input.label) {
                showInputDetails(input)
            }
            val params = LinearLayout.LayoutParams(dp(190), dp(86))
            params.marginEnd = dp(14)
            row.addView(card, params)
        }
        scroll.addView(row)
        return scroll
    }

    private fun showAppActions(app: LaunchableApp) {
        val categories = preferences.categories()
        val choices = arrayOf(
            if (preferences.isFavorite(app.packageName)) "Quitar de Favoritos" else "Agregar a Favoritos",
            "Asignar categoría",
            "Mover antes",
            "Mover después",
            if (app.packageName in preferences.hiddenApps()) "Mostrar aplicación" else "Ocultar aplicación",
        )
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(choices) { _, selected ->
                when (selected) {
                    0 -> preferences.toggleFavorite(app.packageName)
                    1 -> chooseCategory(app, categories)
                    2 -> preferences.move(app.packageName, -1, apps.map(LaunchableApp::packageName))
                    3 -> preferences.move(app.packageName, 1, apps.map(LaunchableApp::packageName))
                    4 -> preferences.toggleHidden(app.packageName)
                }
                buildDashboard()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun chooseCategory(app: LaunchableApp, categories: List<String>) {
        val choices = (categories + "Crear categoría…").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Categoría para ${app.label}")
            .setItems(choices) { _, selected ->
                if (selected == categories.size) {
                    createCategory { category ->
                        preferences.addCategory(category)
                        preferences.setCategory(app.packageName, category)
                        buildDashboard()
                    }
                } else {
                    preferences.setCategory(app.packageName, categories[selected])
                    buildDashboard()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun createCategory(onCreated: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            hint = "Nombre de categoría"
            isSingleLine = true
            setTextColor(Color.WHITE)
        }
        AlertDialog.Builder(this)
            .setTitle("Nueva categoría")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val value = input.text.toString().trim()
                if (value.isBlank()) {
                    Toast.makeText(this, "El nombre no puede estar vacío.", Toast.LENGTH_SHORT).show()
                } else {
                    onCreated(value)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showInputDetails(input: TvInputEntry) {
        AlertDialog.Builder(this)
            .setTitle(input.label)
            .setMessage(
                "Android informa esta entrada (${input.packageName}). Una aplicación de usuario puede " +
                    "enumerarla, pero el cambio global de HDMI requiere permisos del sistema. " +
                    "Puedes abrir la aplicación de TV del fabricante para cambiar la fuente.",
            )
            .setPositiveButton("Abrir TV") { _, _ ->
                if (!changhongTvAdapter.openTelevisionApp()) {
                    Toast.makeText(this, "No se encontró una aplicación de TV compatible.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun showSettings() {
        val options = arrayOf(
            "Elegir fondo",
            "Brillo del fondo",
            "Administrar categorías",
            "Aplicaciones ocultas",
            "Comprobar actualizaciones",
            "Acerca de NOBLEX LAUNCHER",
            "Volver al launcher original",
        )
        AlertDialog.Builder(this)
            .setTitle("Ajustes")
            .setItems(options) { _, selected ->
                when (selected) {
                    0 -> chooseWallpaper()
                    1 -> showWallpaperDim()
                    2 -> manageCategories()
                    3 -> manageHiddenApps()
                    4 -> checkForUpdates()
                    5 -> showAbout()
                    6 -> showRestoreInstructions()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun chooseWallpaper() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        try {
            startActivityForResult(intent, REQUEST_WALLPAPER)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "No hay un selector de imágenes disponible en este dispositivo.", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveWallpaper(uri: Uri): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("No se pudo leer la imagen seleccionada.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("El archivo seleccionado no es una imagen válida.")
        }
        val sampleSize = generateSequence(1) { it * 2 }
            .takeWhile { bounds.outWidth / it > MAX_WALLPAPER_WIDTH * 2 || bounds.outHeight / it > MAX_WALLPAPER_HEIGHT * 2 }
            .lastOrNull()
            ?: 1
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("No se pudo decodificar la imagen seleccionada.")
        val wallpaperFile = File(File(filesDir, "wallpapers").apply { mkdirs() }, "user-wallpaper.jpg")
        val saved = wallpaperFile.outputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, WALLPAPER_QUALITY, output)
        }
        bitmap.recycle()
        if (!saved) throw IOException("No se pudo guardar la imagen seleccionada.")
        return wallpaperFile
    }

    private fun showWallpaperDim() {
        val slider = SeekBar(this).apply {
            max = 70
            progress = preferences.wallpaperDim()
        }
        AlertDialog.Builder(this)
            .setTitle("Oscurecimiento del fondo")
            .setMessage("Ajusta la legibilidad del texto sobre el paisaje.")
            .setView(slider)
            .setPositiveButton("Listo") { _, _ ->
                preferences.setWallpaperDim(slider.progress)
                wallpaperOverlay?.alpha = slider.progress / 100f
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun manageCategories() {
        AlertDialog.Builder(this)
            .setTitle("Categorías")
            .setItems(preferences.categories().toTypedArray()) { _, index ->
                val selected = preferences.categories()[index]
                Toast.makeText(this, "Asigna aplicaciones a «$selected» con MENU.", Toast.LENGTH_LONG).show()
            }
            .setPositiveButton("Crear categoría") { _, _ ->
                createCategory {
                    preferences.addCategory(it)
                    buildDashboard()
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
        AlertDialog.Builder(this)
            .setTitle("Aplicaciones ocultas")
            .setItems(entries.map(LaunchableApp::label).toTypedArray()) { _, index ->
                preferences.toggleHidden(entries[index].packageName)
                buildDashboard()
                Toast.makeText(this, "${entries[index].label} vuelve a estar visible.", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    private fun checkForUpdates() {
        Toast.makeText(this, "Buscando una versión publicada…", Toast.LENGTH_SHORT).show()
        UpdateManager.checkLatest(this) { result ->
            runOnUiThread {
                result.fold(
                    onSuccess = { release ->
                        if (release.versionCode <= BuildConfig.VERSION_CODE) {
                            Toast.makeText(this, "NOBLEX LAUNCHER está actualizado.", Toast.LENGTH_LONG).show()
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Actualización ${release.versionName}")
                                .setMessage(release.releaseNotes)
                                .setPositiveButton("Descargar e instalar") { _, _ ->
                                    val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                                        max = 100
                                    }
                                    val progressDialog = AlertDialog.Builder(this)
                                        .setTitle("Descargando actualización")
                                        .setMessage("Verificando el archivo antes de abrir el instalador de Android.")
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
                                                progressDialog.dismiss()
                                                installResult.exceptionOrNull()?.let {
                                                    Toast.makeText(
                                                        this,
                                                        it.message ?: "No se pudo iniciar la actualización.",
                                                        Toast.LENGTH_LONG,
                                                    ).show()
                                                }
                                            }
                                        },
                                    )
                                }
                                .setNegativeButton("Más tarde", null)
                                .show()
                        }
                    },
                    onFailure = { error ->
                        Toast.makeText(
                            this,
                            error.message ?: "No se pudo comprobar la actualización.",
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
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
        AlertDialog.Builder(this)
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
            ?: Intent(Intent.ACTION_MAIN).setComponent(ComponentName(app.packageName, app.activityName))
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            preferences.recordLaunch(app.packageName)
            startActivity(launchIntent)
        } catch (error: ActivityNotFoundException) {
            Toast.makeText(this, "No se pudo abrir ${app.label}.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateClock() {
        val now = Date()
        clockView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateView?.text = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now)
    }

    private fun addSectionTitle(parent: LinearLayout, title: String) {
        parent.addView(
            text(title, 23f, Color.WHITE, bold = true),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                topMargin = dp(15)
            },
        )
    }

    private fun button(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 15f
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

    private fun text(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean = false,
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER_VERTICAL
    }

    private fun LinearLayout.addView(view: View, marginTop: Int = 0, marginBottom: Int = 0) {
        addView(view, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = marginTop
            bottomMargin = marginBottom
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val FILTER_FAVORITES = "Favoritos"
        private const val FILTER_ALL = "Todas"
        private const val FILTER_INPUTS = "Entradas"
        private const val FILTER_RECENT = "Recientes"
        private const val CLOCK_UPDATE_MS = 60_000L
        private const val REQUEST_WALLPAPER = 1201
        private const val MAX_WALLPAPER_WIDTH = 1920
        private const val MAX_WALLPAPER_HEIGHT = 1080
        private const val WALLPAPER_QUALITY = 85
    }
}
