package com.cargo.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.cargo.launcher.obd.ObdRepository
import com.cargo.launcher.obd.ObdService
import com.cargo.launcher.obd.ObdState
import com.cargo.launcher.viewport.VehicleType
import com.cargo.launcher.viewport.VehicleViewport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Pantalla de inicio de CarGo.
 *  - Pantalla completa inmersiva permanente (API 28-29: systemUiVisibility; API 30+: WindowInsetsController).
 *  - 55 % vehículo (carretera HUD + carro), 45 % widgets (mapa arriba; música y velocímetro abajo).
 *  - Telemetría OBD2 desde ObdService (StateFlow).
 *  - Menú del instalador: 5 toques en el logo del dock + código.
 */
class MainActivity : Activity() {

    companion object {
        private const val REQ_PICK_PNG = 10
        private const val REQ_PERMS = 11
        private const val TAPS_TO_OPEN = 5
        private const val TAP_WINDOW_MS = 2500L
        private const val MAX_CAR_WIDTH_PX = 1600
        private const val GLASS_BG = 0xFF07090D.toInt()
    }

    private val ui: CoroutineScope = MainScope()
    private var obdJob: Job? = null

    private lateinit var root: View
    private lateinit var vehiclePanel: FrameLayout
    private lateinit var roadView: RoadHudView
    private lateinit var carImage: ImageView
    private lateinit var viewport: VehicleViewport
    /** true cuando se muestra el viewport 3D (autos de fábrica); false con la foto del cliente. */
    private var viewportActive = false
    private lateinit var obdStatus: TextView
    private lateinit var dockLogo: ImageView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var speedGauge: SpeedGaugeView
    private lateinit var rpmBar: ProgressBar
    private lateinit var rpmText: TextView
    private lateinit var coolantText: TextView
    private lateinit var voltText: TextView
    private val cards = ArrayList<View>()
    private val textMain = ArrayList<TextView>()
    private val textMuted = ArrayList<TextView>()

    private var customBitmap: Bitmap? = null
    /** Momento en que se abrió ZLink (0 si no); al volver se restaura todo sin recrear la pantalla. */
    private var zlinkOpenedAt = 0L
    private var carAspect = 0.5f
    private val taps = ArrayDeque<Long>()

    // ------------------------------------------------------------------ ciclo de vida

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CarGoPrefs.init(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)
        bindViews()
        enterImmersive()
        setupInstallerTrigger()
        setupCarPlayButton()
        applyProfile()
        requestRuntimePermissions()
        ObdService.start(this)
    }

    override fun onResume() {
        super.onResume()
        enterImmersive()
        if (zlinkOpenedAt != 0L) {
            // De regreso de ZLink (botón Salir o Home del auto): algunas unidades vuelven a mostrar las
            // barras unos milisegundos después de que regresamos, por eso se re-oculta en dos tiempos.
            zlinkOpenedAt = 0L
            root.postDelayed({ enterImmersive() }, 300)
            root.postDelayed({ enterImmersive() }, 1200)
        }
        // Telemetría: el servicio sigue vivo en primer plano; start() es idempotente y lo revive si el
        // sistema lo hubiera detenido mientras ZLink usaba memoria/USB.
        ObdService.start(this)
        showTelemetry(ObdRepository.state.value)   // pinta el último dato al instante, sin esperar
        roadView.running = true
        viewport.running = true
        obdJob?.cancel()
        obdJob = ui.launch { ObdRepository.state.collect { showTelemetry(it) } }
    }

    override fun onPause() {
        roadView.running = false   // no se anima mientras otra app está encima
        viewport.running = false
        obdJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        ui.cancel()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()   // vuelve a ocultar barras después de un diálogo o un swipe
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home estando ya en inicio: cierra diálogos y regresa a la vista principal
        enterImmersive()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Un launcher no se cierra con Atrás
    }

    // ------------------------------------------------------------------ pantalla completa

    @Suppress("DEPRECATION")
    private fun enterImmersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
                if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) root.postDelayed({ enterImmersive() }, 2000)
            }
        }
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    // ------------------------------------------------------------------ vistas

    private fun bindViews() {
        root = findViewById(R.id.root)
        vehiclePanel = findViewById(R.id.vehiclePanel)
        roadView = findViewById(R.id.roadView)
        carImage = findViewById(R.id.carImage)
        viewport = findViewById(R.id.vehicleViewportContainer)
        speedGauge = findViewById(R.id.speedGauge)
        obdStatus = findViewById(R.id.obdStatus)
        dockLogo = findViewById(R.id.dockLogo)
        speedValue = findViewById(R.id.speedValue)
        speedUnit = findViewById(R.id.speedUnit)
        rpmBar = findViewById(R.id.rpmBar)
        rpmText = findViewById(R.id.rpmText)
        coolantText = findViewById(R.id.coolantText)
        voltText = findViewById(R.id.voltText)
        cards += listOf(vehiclePanel, findViewById(R.id.sideBar), findViewById(R.id.mapCard), findViewById(R.id.mediaCard), findViewById(R.id.speedCard))
        textMain += listOf<TextView>(speedValue, findViewById(R.id.clock), findViewById(R.id.trackTitle), findViewById(R.id.mapTitle))
        textMuted += listOf<TextView>(findViewById(R.id.carPlayLabel), speedUnit, rpmText, coolantText, voltText, obdStatus, findViewById(R.id.trackArtist), findViewById(R.id.mapHint))

        // Al cambiar el tamaño del panel (rotación, otra resolución) se recoloca el carro y la carretera
        vehiclePanel.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
            if (r - l != or - ol || b - t != ob - ot) placeCar()
        }
    }

    /** Aplica vehículo + tema + vista guardados. Se llama al iniciar y al cambiar algo en el menú. */
    private fun applyProfile() {
        val v = CarGoPrefs.vehicle
        val theme = CarGoPrefs.theme
        val view = CarGoPrefs.carView.takeIf { it in VehicleAssets.views(v) } ?: VehicleAssets.views(v).first()
        val pal = VehicleAssets.palette(v, theme)

        // Colores sin recrear la Activity
        val glass = theme == ThemeMode.NIGHT
        root.setBackgroundColor(if (glass) GLASS_BG else pal.bg)
        val r = resources.displayMetrics.density * 22f
        for ((i, card) in cards.withIndex()) {
            when {
                // Noche: viewport sin marco, dock negro mate y widgets glassmorphism
                glass && i == 0 -> card.background = null
                glass && i == 1 -> card.setBackgroundResource(R.drawable.bg_dock)
                glass -> card.setBackgroundResource(R.drawable.bg_card_glass)
                else -> card.background = GradientDrawable().apply {
                    cornerRadius = r
                    setColor(if (i == 0) pal.panel else pal.card)
                    setStroke((resources.displayMetrics.density).roundToInt(), pal.stroke)
                }
            }
            card.clipToOutline = !(glass && i <= 1)
        }
        val gaugeAccent = if (glass) VehicleViewport.NIGHT_TINT else pal.accent2
        speedGauge.setColors(gaugeAccent, if (glass) 0xFF0EA5B7.toInt() else pal.accent, pal.stroke, pal.muted)
        speedGauge.maxValue = if (CarGoPrefs.useMph) 80f else 140f
        textMain.forEach { it.setTextColor(pal.text) }
        textMuted.forEach { it.setTextColor(pal.muted) }
        rpmBar.progressTintList = ColorStateList.valueOf(pal.accent2)
        rpmBar.progressBackgroundTintList = ColorStateList.valueOf(pal.stroke)
        listOf<ImageButton>(findViewById(R.id.btnPrev), findViewById(R.id.btnPlay), findViewById(R.id.btnNext))
            .forEach { it.imageTintList = ColorStateList.valueOf(pal.text) }
        roadView.setColors(pal)
        roadView.visibility = if (VehicleAssets.showRoad(v, view)) View.VISIBLE else View.GONE
        speedUnit.setText(if (CarGoPrefs.useMph) R.string.unit_mph else R.string.unit_kmh)

        // Carro: PNG del cliente si hay; si no, el de fábrica
        val custom = CarGoPrefs.customCarUri
        if (custom != null) {
            ui.launch {
                val bmp = withContext(Dispatchers.IO) { decodeCustom(custom) }
                if (bmp != null) {
                    customBitmap?.recycle()
                    customBitmap = bmp
                    carImage.setImageBitmap(bmp)
                    carAspect = bmp.height.toFloat() / bmp.width
                } else {
                    Toast.makeText(this@MainActivity, R.string.png_missing, Toast.LENGTH_LONG).show()
                    setStockCar(v, theme, view)
                }
                placeCar()
            }
        } else {
            setStockCar(v, theme, view)
            placeCar()
        }

        // Autos de fábrica en vista normal o carretera: viewport 3D. Rayos X / alambre y la foto
        // del cliente siguen usando las capas anteriores.
        viewportActive = custom == null && (view == CarView.CLEAN || view == CarView.HUD_ROAD)
        if (viewportActive) {
            setupVehicleViewport(v, theme == ThemeMode.NIGHT)
        } else {
            viewport.visibility = View.GONE
            carImage.visibility = View.VISIBLE
        }
    }

    /**
     * Viewport del vehículo: carga el auto (Prius plata con rines negros o Jeep blanco con placa CARO)
     * y aplica día/noche. Noche: asfalto oscuro mojado, faros encendidos y HUD cian neón.
     * Día: escenario cálido, sin haces de luz. Todo con transiciones suaves, sin recrear vistas.
     */
    private fun setupVehicleViewport(vehicleType: VehicleType, isNightMode: Boolean) {
        val firstTime = viewport.vehicle == null
        viewport.visibility = View.VISIBLE
        roadView.visibility = View.GONE      // la carretera en perspectiva reemplaza a la HUD plana
        carImage.visibility = View.GONE
        viewport.setVehicle(vehicleType, animate = !firstTime)
        viewport.setNightMode(isNightMode, animate = !firstTime)
        val tint = ColorStateList.valueOf(viewport.hudTint)
        rpmBar.progressTintList = tint
        speedUnit.setTextColor(viewport.hudTint)
    }

    private fun setStockCar(v: Vehicle, theme: ThemeMode, view: CarView) {
        val res = VehicleAssets.carDrawable(v, theme, view)
        carImage.setImageResource(res)
        val d = carImage.drawable
        carAspect = if (d != null && d.intrinsicWidth > 0) d.intrinsicHeight.toFloat() / d.intrinsicWidth else 0.5f
    }

    /** Carro centrado, ~92 % del ancho del panel, apoyado al 80 % de la altura; la carretera sale de sus llantas. */
    private fun placeCar() {
        val pw = vehiclePanel.width
        val ph = vehiclePanel.height
        if (pw == 0 || ph == 0) return
        var w = pw * 0.92f
        var h = w * carAspect
        if (h > ph * 0.62f) { h = ph * 0.62f; w = h / carAspect }
        val left = (pw - w) / 2f
        val top = ph * 0.80f - h
        carImage.layoutParams = (carImage.layoutParams as FrameLayout.LayoutParams).apply {
            width = w.roundToInt(); height = h.roundToInt()
            leftMargin = left.roundToInt(); topMargin = top.roundToInt()
        }
        val a = VehicleAssets.roadAnchor(CarGoPrefs.vehicle)
        roadView.setAnchor(left + a[0] * w, top + a[1] * h, w)
    }

    // ------------------------------------------------------------------ telemetría

    private fun showTelemetry(s: ObdState) {
        val mph = CarGoPrefs.useMph
        val kmh = s.speedKmh
        val shownSpeed = kmh?.let { if (mph) it * 0.621371f else it.toFloat() }
        speedValue.text = shownSpeed?.roundToInt()?.toString() ?: getString(R.string.no_value)
        speedGauge.setValue(shownSpeed ?: 0f)
        val speedMph = kmh?.let { it * 0.621371f } ?: 0f
        roadView.setSpeedMph(speedMph)
        if (viewportActive) viewport.setSpeedMph(speedMph)

        val rpm = s.rpm
        rpmBar.progress = rpm ?: 0
        rpmText.text = getString(R.string.rpm_fmt, rpm?.toString() ?: getString(R.string.no_value))
        coolantText.text = s.coolantC?.let {
            if (mph) getString(R.string.temp_f, (it * 9 / 5 + 32)) else getString(R.string.temp_c, it)
        } ?: getString(R.string.temp_none)
        voltText.text = s.batteryV?.let { getString(R.string.volt_fmt, it) } ?: getString(R.string.volt_none)

        obdStatus.text = when (s.status) {
            ObdState.Status.CONNECTED -> "● OBD2 · ${s.adapter ?: ""}"
            ObdState.Status.CONNECTING -> getString(R.string.obd_connecting, s.adapter ?: "")
            ObdState.Status.NO_VEHICLE -> getString(R.string.obd_no_vehicle)
            ObdState.Status.NO_ADAPTER -> getString(R.string.obd_no_adapter)
            ObdState.Status.NO_PERMISSION -> getString(R.string.obd_no_permission)
            ObdState.Status.SEARCHING, ObdState.Status.IDLE -> getString(R.string.obd_searching)
        }
    }

    // ------------------------------------------------------------------ CarPlay (ZLink)

    private fun setupCarPlayButton() {
        val btn = findViewById<View>(R.id.btnCarPlay)
        btn.setOnClickListener { openZlink() }
        btn.setOnLongClickListener { chooseZlinkApp(); true }
    }

    /** Abre ZLink. Si el estéreo no la tiene (o tiene otro nombre), deja elegir la app una vez. */
    private fun openZlink() {
        zlinkOpenedAt = SystemClock.uptimeMillis()
        if (!ZlinkLauncher.open(this)) {
            zlinkOpenedAt = 0L
            Toast.makeText(this, R.string.zlink_missing, Toast.LENGTH_LONG).show()
            chooseZlinkApp()
        }
    }

    private fun chooseZlinkApp() {
        ui.launch {
            val apps = withContext(Dispatchers.IO) { ZlinkLauncher.launchableApps(this@MainActivity) }
            if (apps.isEmpty()) return@launch
            dialog().setTitle(R.string.zlink_choose).setItems(apps.map { it.first }.toTypedArray()) { _, i ->
                CarGoPrefs.zlinkComponent = apps[i].second.flattenToString()
                openZlink()
            }.show()
        }
    }

    // ------------------------------------------------------------------ menú del instalador

    /** 5 toques seguidos (en menos de 2.5 s) sobre el logo del dock abren el menú con código. */
    private fun setupInstallerTrigger() {
        dockLogo.setOnClickListener {
            val now = SystemClock.uptimeMillis()
            taps.addLast(now)
            while (taps.isNotEmpty() && now - taps.first() > TAP_WINDOW_MS) taps.removeFirst()
            if (taps.size >= TAPS_TO_OPEN) {
                taps.clear()
                askPin()
            }
        }
    }

    private fun dialog() = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = getString(R.string.pin_hint)
        }
        dialog().setTitle(R.string.installer_title).setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                if (CarGoPrefs.checkPin(input.text.toString())) installerMenu()
                else Toast.makeText(this, R.string.pin_wrong, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun installerMenu() {
        val items = arrayOf(
            getString(R.string.menu_vehicle, label(CarGoPrefs.vehicle)),
            getString(R.string.menu_theme, label(CarGoPrefs.theme)),
            getString(R.string.menu_view, label(CarGoPrefs.carView)),
            getString(R.string.menu_load_png),
            getString(R.string.menu_clear_png),
            getString(R.string.menu_units, getString(if (CarGoPrefs.useMph) R.string.unit_mph else R.string.unit_kmh)),
            getString(R.string.menu_obd, CarGoPrefs.obdMode.name),
            getString(R.string.menu_home),
            getString(R.string.menu_pin)
        )
        dialog().setTitle(R.string.installer_title).setItems(items) { _, w ->
            when (w) {
                0 -> pick(Vehicle.values().toList(), CarGoPrefs.vehicle) { CarGoPrefs.vehicle = it; applyProfile() }
                1 -> pick(ThemeMode.values().toList(), CarGoPrefs.theme) { CarGoPrefs.theme = it; applyProfile() }
                2 -> pick(VehicleAssets.views(CarGoPrefs.vehicle), CarGoPrefs.carView) { CarGoPrefs.carView = it; applyProfile() }
                3 -> pickPng()
                4 -> { CarGoPrefs.customCarUri = null; applyProfile() }
                5 -> { CarGoPrefs.useMph = !CarGoPrefs.useMph; applyProfile(); showTelemetry(ObdRepository.state.value) }
                6 -> pick(CarGoPrefs.ObdMode.values().toList(), CarGoPrefs.obdMode) {
                    CarGoPrefs.obdMode = it; ObdService.stop(this); ObdService.start(this)
                }
                7 -> try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                8 -> changePin()
            }
        }.show()
    }

    private fun <T : Enum<T>> pick(options: List<T>, current: T, onPick: (T) -> Unit) {
        val names = options.map { label(it) }.toTypedArray()
        dialog().setSingleChoiceItems(names, options.indexOf(current)) { d, i -> d.dismiss(); onPick(options[i]) }.show()
    }

    private fun label(e: Enum<*>): String = when (e) {
        Vehicle.PRIUS_2011 -> getString(R.string.v_prius)
        Vehicle.JEEP_SAHARA_2021 -> getString(R.string.v_jeep)
        ThemeMode.DAY -> getString(R.string.t_day)
        ThemeMode.NIGHT -> getString(R.string.t_night)
        ThemeMode.XMAS -> getString(R.string.t_xmas)
        CarView.CLEAN -> getString(R.string.view_clean)
        CarView.XRAY -> getString(R.string.view_xray)
        CarView.HUD_ROAD -> getString(R.string.view_road)
        CarView.WIREFRAME -> getString(R.string.view_wire)
        else -> e.name
    }

    private fun changePin() {
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        dialog().setTitle(R.string.menu_pin).setView(input).setPositiveButton(R.string.ok) { _, _ ->
            val pin = input.text.toString()
            if (pin.length >= 4) { CarGoPrefs.setPin(pin); Toast.makeText(this, R.string.pin_saved, Toast.LENGTH_SHORT).show() }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    // ------------------------------------------------------------------ PNG desde USB

    /** Selector del sistema (USB/OTG incluido); si el estéreo no lo trae, busca PNGs en las memorias conectadas. */
    private fun pickPng() {
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val get = Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
        val intent = listOf(open, get).firstOrNull { it.resolveActivity(packageManager) != null }
        if (intent != null) {
            try { startActivityForResult(intent, REQ_PICK_PNG); return } catch (_: Exception) { }
        }
        scanUsbForPng()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_PNG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // GET_CONTENT no da permiso persistente: se copia el PNG a la memoria interna de la app
        }
        ui.launch {
            val saved = withContext(Dispatchers.IO) { copyToInternal(uri) } ?: uri.toString()
            CarGoPrefs.customCarUri = saved
            applyProfile()
        }
    }

    /** Copia el PNG a la app: así sigue funcionando aunque se saque la memoria USB. */
    private fun copyToInternal(uri: Uri): String? = try {
        val f = File(filesDir, "car_${CarGoPrefs.vehicle.name.lowercase()}.png")
        contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
        if (f.length() > 0) f.absolutePath else null
    } catch (e: Exception) {
        null
    }

    /** Para estéreos sin selector de archivos (API 28-29): busca .png en /storage/<memoria>/ y /storage/<memoria>/cargo/. */
    private fun scanUsbForPng() {
        if (Build.VERSION.SDK_INT <= 32 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_PERMS)
            return
        }
        ui.launch {
            val found = withContext(Dispatchers.IO) {
                (File("/storage").listFiles() ?: emptyArray())
                    .filter { it.isDirectory && it.canRead() && it.name != "self" }
                    .flatMap { vol -> listOf(vol, File(vol, "cargo")) }
                    .flatMap { dir -> (dir.listFiles() ?: emptyArray()).filter { it.isFile && it.name.endsWith(".png", true) } }
                    .take(50)
            }
            if (found.isEmpty()) {
                Toast.makeText(this@MainActivity, R.string.png_none, Toast.LENGTH_LONG).show()
                return@launch
            }
            dialog().setTitle(R.string.menu_load_png).setItems(found.map { it.parentFile?.name + "/" + it.name }.toTypedArray()) { _, i ->
                ui.launch {
                    val saved = withContext(Dispatchers.IO) { copyToInternal(Uri.fromFile(found[i])) }
                    CarGoPrefs.customCarUri = saved ?: found[i].absolutePath
                    applyProfile()
                }
            }.show()
        }
    }

    /** Carga el PNG con reducción de tamaño para no gastar memoria (máx. 1600 px de ancho). */
    private fun decodeCustom(ref: String): Bitmap? = try {
        val open = {
            if (ref.startsWith("content://") || ref.startsWith("file://")) contentResolver.openInputStream(Uri.parse(ref))
            else File(ref).takeIf { it.exists() }?.inputStream()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) null else {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_CAR_WIDTH_PX) sample *= 2
            open()?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    // ------------------------------------------------------------------ permisos

    private fun requestRuntimePermissions() {
        val need = ArrayList<String>()
        need += Manifest.permission.ACCESS_FINE_LOCATION
        need += Manifest.permission.ACCESS_COARSE_LOCATION
        if (Build.VERSION.SDK_INT >= 31) need += Manifest.permission.BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.POST_NOTIFICATIONS
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS) {
            ObdService.stop(this); ObdService.start(this)    // reintenta con los permisos nuevos
            if (Manifest.permission.READ_EXTERNAL_STORAGE in permissions &&
                grantResults.getOrNull(permissions.indexOf(Manifest.permission.READ_EXTERNAL_STORAGE)) == PackageManager.PERMISSION_GRANTED
            ) scanUsbForPng()
        }
    }
}
