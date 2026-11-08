package com.juandiego.priuslauncher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Themer.wrap(newBase))
    }

    private companion object {
        const val REQ_LOC = 11
        const val GPS_STALE_MS = 5000L
        const val SPOTIFY = "com.spotify.music"
        const val GMAPS = "com.google.android.apps.maps"
    }

    private enum class GpsState { OK, SEARCHING, OFF, NO_PERMISSION }

    private var ready = false
    private var failed = false
    private var sport = false
    private var appliedKey = ""
    private var accent = 0
    private var loadedCar: String? = null
    private var firstStart = true
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var root: View
    private var carPhoto: CarPhotoView? = null
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private var tripLine: TextView? = null
    private var gpsText: TextView? = null
    private var weatherIcon: ImageView? = null
    private var weatherTemp: TextView? = null
    private var weatherDesc: TextView? = null
    private var navPanel: View? = null
    private var navIcon: ImageView? = null
    private var navTitle: TextView? = null
    private var navText: TextView? = null
    private var mapWaiting: View? = null
    private var artBox: View? = null
    private var albumArt: ImageView? = null
    private var artIcon: ImageView? = null
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var progress: ProgressLine
    private lateinit var btnPlay: ImageButton

    private lateinit var mapPanel: MapPanel
    private lateinit var dock: HomeDock

    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var locationManager: LocationManager? = null
    private var gpsListening = false
    private var lastFix = 0L
    private var lastMps = 0f
    private var gpsState = GpsState.SEARCHING
    private var lastTripText = ""

    // ---------- Listeners ----------

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            checkGpsStale()
            updateLights(true)
            handler.postDelayed(this, 1000)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix = SystemClock.elapsedRealtime()
            showSpeed(if (location.hasSpeed()) location.speed else 0f)
            showOnMap(location.latitude, location.longitude, if (location.hasBearing()) location.bearing else null)
            Trip.onLocation(this@MainActivity, location)
            Weather.update(this@MainActivity, location)
            showTrip()
            setGpsState(GpsState.OK)
        }

        override fun onProviderEnabled(provider: String) = setGpsState(GpsState.SEARCHING)
        override fun onProviderDisabled(provider: String) {
            showSpeed(0f)
            setGpsState(GpsState.OFF)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    /** Ubicación aproximada (red) para el mapa y el clima mientras no hay GPS. */
    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (lastFix == 0L) showOnMap(location.latitude, location.longitude, null)
            Weather.update(this@MainActivity, location)
        }

        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    private val navListener: () -> Unit = { updateNav() }
    private val weatherListener: () -> Unit = { showWeather() }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = updateNowPlaying()
        override fun onPlaybackStateChanged(state: PlaybackState?) = updateNowPlaying()
        override fun onSessionDestroyed() {
            bindController(null)
            refreshSessions()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    // ---------- Ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = CrashReport.take(this)
        if (trace != null) {
            CrashReport.show(this, trace)
            return
        }
        guarded { setupScreen(savedInstanceState) }
    }

    /** Si algo falla, muestra el error en pantalla en lugar de cerrar la app. */
    private inline fun guarded(block: () -> Unit) {
        if (failed) return
        try {
            block()
        } catch (e: Throwable) {
            failed = true
            ready = false
            CrashReport.show(this, CrashReport.format(e))
        }
    }

    private fun configKey() = "${Prefs.style(this)}|${Prefs.lightTheme(this)}|${Prefs.lang(this)}"

    private fun setupScreen(state: Bundle?) {
        appliedKey = configKey()
        sport = Prefs.isSport(this)
        accent = Prefs.accent(this)
        setContentView(if (sport) R.layout.activity_sport else R.layout.activity_home)
        hideSystemUi()
        bindViews()
        applyStyle()
        carPhoto?.apply {
            // Dónde va el carro dentro de su espacio (la carretera se dibuja alrededor)
            if (sport) { carWidthFrac = 0.92f; carBottomFrac = 0.84f; carMaxHeightFrac = 0.66f }
            else { carWidthFrac = 0.96f; carBottomFrac = 0.76f; carMaxHeightFrac = 0.6f }
        }

        mapPanel = MapPanel(this, findViewById(R.id.mapContainer), sport, Prefs.lightTheme(this), accent, { openMaps() }) {
            pickSlot(DockSlots.MAPS)
        }
        mapPanel.create(state)
        roundClip(findViewById(R.id.mapCard), if (sport) 4f else 22f)
        artBox?.let { roundClip(it, if (sport) 4f else 10f) }
        findViewById<View?>(R.id.carCard)?.let { roundClip(it, 24f) }

        dock = if (sport) {
            Dock(this, findViewById(R.id.dockBar), true, findViewById(R.id.statusWifi), findViewById(R.id.statusBt), dockActions)
        } else {
            SideDock(this, findViewById(R.id.sideApps), findViewById(R.id.statusWifi), findViewById(R.id.statusBt), dockActions)
        }
        dock.build()

        setupClicks()
        setupMediaButtons()

        val dockPrefs = getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE)
        if (!dockPrefs.getBoolean("hint2_shown", false)) {
            Toast.makeText(this, R.string.hint_first, Toast.LENGTH_LONG).show()
            dockPrefs.edit().putBoolean("hint2_shown", true).apply()
        }
        if (!hasLocationPermission()) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC)
        }

        Weather.load(this)
        Trip.load(this)
        reloadCarIfNeeded()
        applyAccent()
        animateCarIn()
        ready = true
    }

    private val dockActions = object : Dock.Actions {
        override fun openMaps() = this@MainActivity.openMaps()
        override fun openMusic() {
            val i = DockSlots.resolve(this@MainActivity, getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE), DockSlots.MUSIC)
            if (i == null || !tryStart(i)) openSpotify()
        }
        override fun openPhone() {
            val i = DockSlots.resolve(this@MainActivity, getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE), DockSlots.PHONE)
            if (i == null || !tryStart(i)) pickSlot(DockSlots.PHONE)
        }
        override fun pickSlot(slot: Int) = this@MainActivity.pickSlot(slot)
    }

    private fun pickSlot(slot: Int) {
        startActivity(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK_SLOT, slot))
    }

    /** Sport: letra de computadora, mapa arriba y música abajo con marcos de esquinas. */
    private fun applyStyle() {
        val dayNight = findViewById<ImageButton>(R.id.btnDayNight)
        if (!sport) {
            dayNight.setImageResource(if (Prefs.lightTheme(this)) R.drawable.ic_moon else R.drawable.ic_sun)
            // Íconos de las tarjetas, estilo iPhone
            badge(findViewById(R.id.musicBadge), 0xFFFF6A7E.toInt(), 0xFFF0263F.toInt(), android.graphics.Color.WHITE)
            badge(findViewById(R.id.weatherBadge), 0xFF5AB8FF.toInt(), 0xFF2F7BEA.toInt(), 0xFFFFD84A.toInt())
            findViewById<ImageView?>(R.id.navChipIcon)?.imageTintList = ColorStateList.valueOf(0xFF2B7BFF.toInt())
            return
        }
        root.setBackgroundColor(android.graphics.Color.BLACK)
        dayNight.visibility = View.GONE
        findViewById<View>(R.id.mediaLabel).visibility = View.GONE
        findViewById<View>(R.id.navLabel).visibility = View.GONE
        tripLine?.visibility = View.VISIBLE
        gpsText?.visibility = View.VISIBLE
        weatherDesc?.visibility = View.GONE
        speedValue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.speed_sport))
        speedUnit.textSize = 15f
        val col = findViewById<LinearLayout>(R.id.rightCol)
        val media = findViewById<View>(R.id.mediaCard)
        val nav = findViewById<View>(R.id.navCard)
        col.removeView(nav)
        col.addView(nav, 0)
        (nav.layoutParams as LinearLayout.LayoutParams).apply { topMargin = 0; weight = 1.7f }
        (media.layoutParams as LinearLayout.LayoutParams).apply { topMargin = resources.getDimensionPixelSize(R.dimen.gap); weight = 1f }
        val mono = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        fun walk(v: View) {
            if (v is TextView) v.typeface = mono
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    private fun badge(v: ImageView?, top: Int, bottom: Int, tint: Int) {
        v ?: return
        v.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply { cornerRadius = dp(6f) }
        v.imageTintList = ColorStateList.valueOf(tint)
    }

    override fun onStart() {
        super.onStart()
        if (ready) guarded { startEverything() }
    }

    private fun startEverything() {
        startGps()
        startMediaWatch()
        NavState.addListener(navListener)
        Weather.addListener(weatherListener)
        updateNav()
        showWeather()
        showTrip()
        carPhoto?.start()
        if (firstStart) {
            // Al abrir el launcher las luces se encienden con un fundido
            firstStart = false
            carPhoto?.setLights(false, false)
        }
        updateLights(true)
        mapPanel.onStart()
        dock.start()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onResume() {
        super.onResume()
        if (ready) guarded { resumeEverything() }
    }

    private fun resumeEverything() {
        if (configKey() != appliedKey) {
            recreate()
            return
        }
        hideSystemUi()
        mapPanel.onResume()
        dock.build()
        applyAccent()
        speedUnit.setText(
            if (sport) { if (Prefs.useKmh(this)) R.string.unit_kmh else R.string.unit_mph }
            else { if (Prefs.useKmh(this)) R.string.unit_kmh_small else R.string.unit_mph_small }
        )
        showSpeed(lastMps)
        showTrip()
        showWeather()
        reloadCarIfNeeded()
        carPhoto?.animationEnabled = Prefs.carAnimation(this)
        updateNowPlaying()
    }

    override fun onPause() {
        super.onPause()
        if (ready) mapPanel.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!ready) return
        stopGps()
        stopMediaWatch()
        NavState.removeListener(navListener)
        Weather.removeListener(weatherListener)
        carPhoto?.stop()
        mapPanel.onStop()
        dock.stop()
        Trip.save(this)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        if (ready) mapPanel.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (ready) mapPanel.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (ready) mapPanel.onSave(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ready) hideSystemUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Es la pantalla de inicio: "atrás" no hace nada aquí.
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC && ready) guarded { startGps() }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (ready && requestCode == CarPhoto.REQ_PICK && resultCode == RESULT_OK && CarPhoto.handleResult(this, data)) {
            reloadCarIfNeeded()
            animateCarIn()
            Toast.makeText(this, R.string.photo_changed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Vistas ----------

    private fun bindViews() {
        root = findViewById(R.id.root)
        carPhoto = findViewById(R.id.carPhoto)
        speedValue = findViewById(R.id.speedValue)
        speedUnit = findViewById(R.id.speedUnit)
        tripLine = findViewById(R.id.tripLine)
        gpsText = findViewById(R.id.gpsText)
        weatherIcon = findViewById(R.id.weatherIcon)
        weatherTemp = findViewById(R.id.weatherTemp)
        weatherDesc = findViewById(R.id.weatherDesc)
        navPanel = findViewById(R.id.navPanel)
        navIcon = findViewById(R.id.navIcon)
        navTitle = findViewById(R.id.navTitle)
        navText = findViewById(R.id.navText)
        mapWaiting = findViewById(R.id.mapWaiting)
        artBox = findViewById(R.id.artBox)
        albumArt = findViewById(R.id.albumArt)
        artIcon = findViewById(R.id.artIcon)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        progress = findViewById(R.id.progress)
        btnPlay = findViewById(R.id.btnPlay)
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun roundClip(v: View, radiusDp: Float) {
        val r = dp(radiusDp)
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        v.clipToOutline = true
    }

    private fun setupClicks() {
        findViewById<ImageButton>(R.id.btnDayNight).setOnClickListener {
            Prefs.setLightTheme(this, !Prefs.lightTheme(this))
            recreate()
        }
        if (!sport) carPhoto?.setOnLongClickListener {
            pickPhoto()
            true
        }
        gpsText?.setOnClickListener { onGpsTap() }
        findViewById<View?>(R.id.btnSettings)?.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View?>(R.id.btnApps)?.setOnClickListener { startActivity(Intent(this, AppsActivity::class.java)) }
        findViewById<View?>(R.id.navChip)?.setOnClickListener { openMaps() }
    }

    private fun onGpsTap() {
        when (gpsState) {
            GpsState.NO_PERMISSION -> requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC
            )
            GpsState.OFF -> tryStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> Unit
        }
    }

    /** Pinta todo con el color del tema. */
    private fun applyAccent() {
        accent = Prefs.accent(this)
        mapPanel.accent = accent
        dock.applyColors(accent)
        carPhoto?.accent = accent
        progress.color = accent
        val d = resources.displayMetrics.density
        if (sport) {
            val a2 = scaleColor(accent, 0.62f)
            val a3 = scaleColor(accent, 0.25f)
            paintTags(root, accent, a2, a3)
            findViewById<View>(R.id.mediaCard).background = BracketDrawable(a2, d)
            findViewById<View>(R.id.navCard).background = BracketDrawable(a2, d)
            findViewById<View>(R.id.mapCard).setBackgroundColor(android.graphics.Color.BLACK)
            findViewById<View>(R.id.mapBorder).background = null
            artBox?.background = GradientDrawable().apply { cornerRadius = 4 * d; setColor(a3) }
            artIcon?.imageTintList = ColorStateList.valueOf(accent)
            progress.trackColor = a3
            setGpsState(gpsState)
        } else {
            // Estilo Apple: portada con degradado del color del tema, barra de progreso blanca/negra
            artBox?.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(accent, scaleColor(accent, 0.45f)))
            artIcon?.imageTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
            progress.color = getColor(R.color.cp_text)
            progress.trackColor = getColor(R.color.cp_track)
            setGpsState(gpsState)
        }
    }

    private fun paintTags(v: View, a1: Int, a2: Int, a3: Int) {
        val c = when (v.tag) {
            "a1" -> a1
            "a2" -> a2
            "a3" -> a3
            else -> null
        }
        if (c != null) {
            when (v) {
                is TextView -> v.setTextColor(c)
                is ImageView -> v.imageTintList = ColorStateList.valueOf(c)
                else -> v.setBackgroundColor(c)
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) paintTags(v.getChildAt(i), a1, a2, a3)
    }

    private fun pickPhoto() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(CarPhoto.pickIntent(), CarPhoto.REQ_PICK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    /** Carga el dibujo del carro que toca (día, noche, Sport, Anatomy o tu foto) solo si cambió. */
    private fun reloadCarIfNeeded() {
        val cp = carPhoto ?: return
        val light = Prefs.lightTheme(this)
        val anat = Prefs.anatomy(this)
        val uri = if (sport) null else Prefs.carUri(this)?.toString()
        val key = "$sport|$light|$anat|$uri"
        if (key == loadedCar) return
        loadedCar = key
        val mode = when {
            sport -> CarPhotoView.Mode.SPORT
            light -> CarPhotoView.Mode.DAY
            else -> CarPhotoView.Mode.NIGHT
        }
        val custom = if (uri != null) CarPhoto.loadCustom(this, 1400) else null
        if (custom != null) {
            cp.setCar(custom, FloatArray(0), FloatArray(0), FloatArray(0), mode, true)
            return
        }
        // El mismo carro plateado de día, de noche y en Sport; Anatomy es opcional (Ajustes)
        cp.setCar(
            CarPhoto.loadRes(this, if (anat) R.drawable.anat_main else R.drawable.car_main),
            floats(if (anat) R.string.anat_wheels else R.string.car_wheels),
            floats(if (anat) R.string.anat_lamps else R.string.car_lamps),
            floats(R.string.road_anchor),
            mode, false
        )
    }

    private fun floats(res: Int): FloatArray = try {
        getString(res).split(",").map { it.trim().toFloat() }.toFloatArray()
    } catch (e: Exception) {
        FloatArray(0)
    }

    /** Luces del carro: encendidas de 5 PM a 6 AM. */
    private fun updateLights(fade: Boolean) {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        carPhoto?.setLights(h >= 17 || h < 6, fade)
    }

    /** Entrada suave: aparece y se desliza desde la izquierda (600 ms). */
    private fun animateCarIn() {
        val v: View = carPhoto ?: return
        v.alpha = 0f
        v.translationX = -dp(60f)
        v.animate().alpha(1f).translationX(0f).setDuration(600).setInterpolator(DecelerateInterpolator()).start()
    }

    // ---------- Clima y viaje ----------

    private fun showWeather() {
        val n = Weather.now ?: return
        weatherTemp?.text = Weather.tempText(this, n)
        val desc = getString(Weather.descRes(n.code))
        weatherDesc?.text = desc
        findViewById<TextView?>(R.id.weatherCity)?.text = n.city ?: ""
        findViewById<TextView?>(R.id.weatherRange)?.let { t ->
            val mx = n.maxC
            val mn = n.minC
            t.text = if (mx != null && mn != null) getString(R.string.w_max_min, Weather.degrees(this, mx), Weather.degrees(this, mn)) else ""
        }
        findViewById<LinearLayout?>(R.id.weatherHours)?.let { row -> showHours(row, n) }
        weatherIcon?.let { iv ->
            val res = Weather.iconRes(n)
            iv.setImageResource(res)
            iv.imageTintList = ColorStateList.valueOf(
                when {
                    sport -> scaleColor(accent, 0.62f)
                    res == R.drawable.ic_sun -> getColor(R.color.sun)
                    else -> getColor(R.color.cp_muted)
                }
            )
        }
    }

    /** Pronóstico de las próximas 4 horas: hora, ícono y temperatura. */
    private fun showHours(row: LinearLayout, n: Weather.Now) {
        row.removeAllViews()
        val is24 = android.text.format.DateFormat.is24HourFormat(this)
        for (h in n.hours) {
            val col = LinearLayout(this)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = android.view.Gravity.CENTER_HORIZONTAL
            val label = TextView(this).apply {
                text = if (is24) "${h.hour}:00" else "${if (h.hour % 12 == 0) 12 else h.hour % 12} ${if (h.hour < 12) "AM" else "PM"}"
                textSize = 10f
                setTextColor(getColor(R.color.cp_muted))
                maxLines = 1
            }
            val icon = ImageView(this).apply {
                val res = Weather.iconFor(h.code, h.isDay)
                setImageResource(res)
                imageTintList = ColorStateList.valueOf(if (res == R.drawable.ic_sun) getColor(R.color.sun) else getColor(R.color.cp_muted))
            }
            val temp = TextView(this).apply {
                text = Weather.degrees(this@MainActivity, h.tempC)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(getColor(R.color.cp_text))
            }
            col.addView(label)
            col.addView(icon, LinearLayout.LayoutParams(dp(14f).toInt(), dp(14f).toInt()).apply { topMargin = dp(2f).toInt(); bottomMargin = dp(2f).toInt() })
            col.addView(temp)
            row.addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** Línea del viaje (Sport): distancia · tiempo · velocidad máxima. */
    private fun showTrip() {
        val tl = tripLine ?: return
        if (!sport) return
        val kmh = Prefs.useKmh(this)
        val dist = if (kmh) Trip.distanceM / 1000.0 else Trip.distanceM / 1609.34
        val min = Trip.movingMs / 60000
        val max = (Trip.maxMps * if (kmh) 3.6f else 2.23694f).roundToInt()
        val text = String.format(
            Locale.US, "%.1f %s   ·   %d:%02d   ·   %s %d",
            dist, getString(if (kmh) R.string.unit_km else R.string.unit_mi), min / 60, min % 60, getString(R.string.stat_max), max
        )
        if (text != lastTripText) {
            lastTripText = text
            tl.text = text
        }
    }

    // ---------- Mapa y navegación ----------

    private fun showOnMap(lat: Double, lon: Double, bearing: Float?) {
        mapPanel.setLocation(lat, lon, bearing)
        mapWaiting?.visibility = View.GONE
    }

    /** Abre la app de mapas elegida (mantén presionado el mapa para cambiarla); si no, Google Maps. */
    private fun openMaps() {
        val i = DockSlots.resolve(this, getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE), DockSlots.MAPS)
            ?: packageManager.getLaunchIntentForPackage(GMAPS)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i == null || !tryStart(i)) Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
    }

    private fun updateNav() {
        if (!ready) return
        val panel = navPanel ?: return
        val nav = NavState.current
        if (nav == null) {
            panel.visibility = View.GONE
            return
        }
        navTitle?.text = nav.title.ifBlank { nav.text }
        navText?.text = listOf(if (nav.title.isBlank()) "" else nav.text, nav.sub).filter { it.isNotBlank() }.joinToString(" · ")
        if (nav.icon != null) {
            navIcon?.setImageDrawable(nav.icon)
            navIcon?.imageTintList = null
        }
        panel.visibility = View.VISIBLE
    }

    private fun tryStart(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    // ---------- GPS ----------

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startGps() {
        if (gpsListening) return
        if (!hasLocationPermission()) {
            setGpsState(GpsState.NO_PERMISSION)
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper())
            gpsListening = true
            showLastKnownLocation(lm)
            try {
                if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, networkListener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // sin ubicación por red
            }
            setGpsState(if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) GpsState.SEARCHING else GpsState.OFF)
        } catch (e: SecurityException) {
            setGpsState(GpsState.NO_PERMISSION)
        } catch (e: IllegalArgumentException) {
            setGpsState(GpsState.OFF)
        }
    }

    private fun showLastKnownLocation(lm: LocationManager) {
        try {
            val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { lm.allProviders.contains(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            if (best != null) {
                showOnMap(best.latitude, best.longitude, null)
                Weather.update(this, best)
            }
        } catch (e: SecurityException) {
            // sin permiso todavía
        }
    }

    private fun stopGps() {
        if (!gpsListening) return
        try {
            locationManager?.removeUpdates(locationListener)
            locationManager?.removeUpdates(networkListener)
        } catch (e: SecurityException) {
            // nada
        }
        gpsListening = false
    }

    private fun checkGpsStale() {
        if (gpsListening && lastFix > 0 && SystemClock.elapsedRealtime() - lastFix > GPS_STALE_MS) {
            lastFix = 0
            showSpeed(0f)
            setGpsState(GpsState.SEARCHING)
        }
    }

    private fun showSpeed(mps: Float) {
        lastMps = mps
        carPhoto?.speedMps = mps
        val kmh = Prefs.useKmh(this)
        val v = if (kmh) mps * 3.6f else mps * 2.23694f
        val txt = (if (v < 1f) 0 else v.roundToInt()).toString()
        if (speedValue.text.toString() != txt) speedValue.text = txt
    }

    private fun setGpsState(state: GpsState) {
        gpsState = state
        if (!sport) {
            // Apple: un puntito verde (GPS listo), amarillo (buscando) o gris (apagado)
            findViewById<View?>(R.id.gpsDot)?.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(
                    when (state) {
                        GpsState.OK -> 0xFF34C759.toInt()
                        GpsState.SEARCHING -> 0xFFFFC23D.toInt()
                        else -> 0xFF8E939B.toInt()
                    }
                )
            }
            return
        }
        val t = gpsText ?: return
        t.setTextColor(
            when (state) {
                GpsState.OK -> if (sport) accent else getColor(R.color.gps_ok)
                GpsState.SEARCHING -> getColor(R.color.gps_wait)
                else -> getColor(R.color.text_dim)
            }
        )
    }

    // ---------- Música (Spotify primero) ----------

    private fun setupMediaButtons() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToPrevious() else spotifyKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        btnPlay.setOnClickListener {
            val c = controller
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            } else {
                startSpotify()
            }
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToNext() else spotifyKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        findViewById<View>(R.id.mediaCard).setOnClickListener {
            if (!hasNotificationAccess()) {
                if (!tryStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
                    Toast.makeText(this, R.string.no_access_screen, Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }
            openSpotify()
        }
    }

    private fun spotifyInstalled() = packageManager.getLaunchIntentForPackage(SPOTIFY) != null

    private fun openSpotify() {
        val sp = packageManager.getLaunchIntentForPackage(SPOTIFY)
        if (sp != null && tryStart(sp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        val launch = controller?.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launch == null || !tryStart(launch)) {
            val i = DockSlots.resolve(this, getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE), DockSlots.MUSIC)
            if (i != null) tryStart(i)
        }
    }

    private fun spotifyKey(code: Int) {
        if (!spotifyInstalled()) {
            sendMediaKey(code)
            return
        }
        for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
            sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, code)))
        }
    }

    private fun startSpotify() {
        if (!spotifyInstalled()) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        spotifyKey(KeyEvent.KEYCODE_MEDIA_PLAY)
        handler.postDelayed({
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (controller?.playbackState?.state != PlaybackState.STATE_PLAYING && !am.isMusicActive) openSpotify()
        }, 2000)
    }

    private fun sendMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        handler.postDelayed({ if (controller == null) setPlayIcon(am.isMusicActive) }, 600)
    }

    private fun setPlayIcon(playing: Boolean) {
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun listenerComponent() = ComponentName(this, MediaListenerService::class.java)

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.contains("$packageName/") == true
    }

    private fun startMediaWatch() {
        if (!hasNotificationAccess()) {
            bindController(null)
            return
        }
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent())
            sessionManager = msm
            refreshSessions()
        } catch (e: SecurityException) {
            sessionManager = null
            bindController(null)
        }
    }

    private fun stopMediaWatch() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        bindController(null)
    }

    private fun refreshSessions() {
        val msm = sessionManager ?: return
        try {
            pickController(msm.getActiveSessions(listenerComponent()))
        } catch (e: SecurityException) {
            bindController(null)
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val spotify = list?.firstOrNull { it.packageName == SPOTIFY }
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        bindController(spotify ?: playing ?: list?.firstOrNull())
    }

    private fun bindController(c: MediaController?) {
        val old = controller
        if (old != null && c != null && old.sessionToken == c.sessionToken) {
            updateNowPlaying()
            return
        }
        old?.unregisterCallback(mediaCallback)
        controller = c
        c?.registerCallback(mediaCallback)
        updateNowPlaying()
    }

    private fun updateNowPlaying() {
        if (!ready) return
        val c = controller
        if (c == null) {
            val access = hasNotificationAccess()
            trackTitle.setText(if (access) R.string.nothing_playing else R.string.grant_access_title)
            trackArtist.setText(if (access) R.string.nothing_playing_sub else R.string.grant_access_sub)
            showArt(null)
            setPlayIcon((getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive)
            updateProgress()
            return
        }
        val md = c.metadata
        trackTitle.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: appLabel(c.packageName)
        trackArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        showArt(md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))
        setPlayIcon(c.playbackState?.state == PlaybackState.STATE_PLAYING)
        updateProgress()
    }

    private fun showArt(bitmap: Bitmap?) {
        val aa = albumArt ?: return
        if (bitmap != null) {
            aa.setImageBitmap(bitmap)
            aa.visibility = View.VISIBLE
            artIcon?.visibility = View.GONE
        } else {
            aa.visibility = View.GONE
            artIcon?.visibility = View.VISIBLE
        }
    }

    private fun updateProgress() {
        if (!ready) return
        val c = controller
        val duration = c?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (c == null || duration <= 0L) {
            progress.level = 0f
            return
        }
        val st = c.playbackState
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        progress.level = pos.coerceIn(0L, duration).toFloat() / duration
    }

    private fun appLabel(pkg: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
}
