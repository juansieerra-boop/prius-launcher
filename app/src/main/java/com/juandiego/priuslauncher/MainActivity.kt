package com.juandiego.priuslauncher

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Outline
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
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Themer.wrap(newBase))
    }

    private companion object {
        const val REQ_LOC = 11
        const val MAX_MPH = 100f
        const val MAX_KMH = 160f
        const val GPS_STALE_MS = 5000L
    }

    private enum class GpsState { OK, SEARCHING, OFF, NO_PERMISSION }

    private lateinit var dockPrefs: SharedPreferences
    private lateinit var carPhoto: CarPhotoView
    private lateinit var stripes: StripesView
    private lateinit var dateView: TextView
    private lateinit var gpsDot: View
    private lateinit var gpsText: TextView
    private lateinit var speedValue: TextView
    private lateinit var speedUnit: TextView
    private lateinit var speedBar: SegmentBar
    private lateinit var mapView: LiveMapView
    private lateinit var navPanel: View
    private lateinit var navIcon: ImageView
    private lateinit var navTitle: TextView
    private lateinit var navText: TextView
    private lateinit var navSub: TextView
    private lateinit var mapLabel: View
    private lateinit var mapHint: TextView
    private lateinit var phoneIconBox: View
    private lateinit var tileRow1: LinearLayout
    private lateinit var tileRow2: LinearLayout
    private lateinit var artBox: View
    private lateinit var albumArt: ImageView
    private lateinit var artIcon: ImageView
    private lateinit var nowCap: TextView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var progress: ProgressLine
    private lateinit var posText: TextView
    private lateinit var durText: TextView
    private lateinit var btnPlay: ImageButton

    private var ready = false
    private var appliedLight = false
    private var failed = false
    private var accent = 0
    private var loadedPhoto: String? = "none"
    private val handler = Handler(Looper.getMainLooper())

    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private var locationManager: LocationManager? = null
    private var gpsListening = false
    private var lastFix = 0L
    private var lastMps = 0f
    private var gpsState = GpsState.SEARCHING

    // ---------- Receptores y listeners ----------

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateDate()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateProgress()
            checkGpsStale()
            handler.postDelayed(this, 1000)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix = SystemClock.elapsedRealtime()
            showSpeed(if (location.hasSpeed()) location.speed else 0f)
            mapView.setLocation(location.latitude, location.longitude, if (location.hasBearing()) location.bearing else null)
            setGpsState(GpsState.OK)
        }

        override fun onProviderEnabled(provider: String) {
            setGpsState(GpsState.SEARCHING)
        }

        override fun onProviderDisabled(provider: String) {
            showSpeed(0f)
            setGpsState(GpsState.OFF)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    /** Ubicación aproximada (Wi-Fi/red) solo para el mapa, mientras el GPS no tenga señal. */
    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (lastFix == 0L) mapView.setLocation(location.latitude, location.longitude, null)
        }

        override fun onProviderEnabled(provider: String) {}

        override fun onProviderDisabled(provider: String) {}

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        }
    }

    private val navListener: () -> Unit = { updateNav() }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateNowPlaying()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateNowPlaying()
        }

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
        guarded { setupScreen() }
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

    private fun setupScreen() {
        appliedLight = Prefs.lightTheme(this)
        setContentView(R.layout.activity_main)
        hideSystemUi()
        dockPrefs = getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE)
        bindViews()

        findViewById<View>(R.id.root).background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(getColor(R.color.screen_glow), getColor(R.color.bg_dark))
            gradientRadius = resources.displayMetrics.widthPixels * 0.75f
            setGradientCenter(0.3f, 0.2f)
        }
        roundClip(findViewById(R.id.leftCard), 18f)
        roundClip(findViewById(R.id.mapTile), 14f)
        roundClip(artBox, 12f)

        setupClicks()
        setupMediaButtons()

        if (!dockPrefs.getBoolean("hint_shown", false)) {
            Toast.makeText(this, R.string.hint_long_press, Toast.LENGTH_LONG).show()
            dockPrefs.edit().putBoolean("hint_shown", true).apply()
        }

        if (!hasLocationPermission()) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                REQ_LOC
            )
        }

        reloadCarPhotoIfNeeded()
        animateCarIn()
        ready = true
    }

    override fun onStart() {
        super.onStart()
        if (ready) guarded { startEverything() }
    }

    private fun startEverything() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeReceiver, filter)
        startGps()
        startMediaWatch()
        NavState.addListener(navListener)
        updateNav()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onResume() {
        super.onResume()
        if (ready) guarded { resumeEverything() }
    }

    private fun resumeEverything() {
        if (Prefs.lightTheme(this) != appliedLight) {
            recreate()
            return
        }
        hideSystemUi()
        applyAccent()
        buildTiles()
        speedUnit.setText(if (Prefs.useKmh(this)) R.string.unit_kmh else R.string.unit_mph)
        showSpeed(lastMps)
        reloadCarPhotoIfNeeded()
        updateNowPlaying()
    }

    override fun onStop() {
        super.onStop()
        if (!ready) return
        try {
            unregisterReceiver(timeReceiver)
        } catch (e: IllegalArgumentException) {
            // ya estaba desregistrado
        }
        stopGps()
        stopMediaWatch()
        NavState.removeListener(navListener)
        handler.removeCallbacksAndMessages(null)
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
            reloadCarPhotoIfNeeded()
            animateCarIn()
            Toast.makeText(this, R.string.photo_changed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Vistas ----------

    private fun bindViews() {
        carPhoto = findViewById(R.id.carPhoto)
        stripes = findViewById(R.id.stripes)
        dateView = findViewById(R.id.date)
        gpsDot = findViewById(R.id.gpsDot)
        gpsText = findViewById(R.id.gpsText)
        speedValue = findViewById(R.id.speedValue)
        speedUnit = findViewById(R.id.speedUnit)
        speedBar = findViewById(R.id.speedBar)
        mapView = findViewById(R.id.mapView)
        navPanel = findViewById(R.id.navPanel)
        navIcon = findViewById(R.id.navIcon)
        navTitle = findViewById(R.id.navTitle)
        navText = findViewById(R.id.navText)
        navSub = findViewById(R.id.navSub)
        mapLabel = findViewById(R.id.mapLabel)
        mapHint = findViewById(R.id.mapHint)
        phoneIconBox = findViewById(R.id.phoneIconBox)
        tileRow1 = findViewById(R.id.tileRow1)
        tileRow2 = findViewById(R.id.tileRow2)
        artBox = findViewById(R.id.artBox)
        albumArt = findViewById(R.id.albumArt)
        artIcon = findViewById(R.id.artIcon)
        nowCap = findViewById(R.id.nowCap)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        progress = findViewById(R.id.progress)
        posText = findViewById(R.id.posText)
        durText = findViewById(R.id.durText)
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
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        carPhoto.setOnLongClickListener {
            pickPhoto()
            true
        }
        findViewById<View>(R.id.allApps).setOnClickListener {
            startActivity(Intent(this, AppsActivity::class.java))
        }
        findViewById<View>(R.id.phoneBar).setOnClickListener {
            if (!tryStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) tryStart(Intent(Settings.ACTION_SETTINGS))
        }
        val map = findViewById<View>(R.id.mapTile)
        map.setOnClickListener { openGoogleMaps() }
        map.setOnLongClickListener {
            pickForSlot(DockSlots.MAPS)
            true
        }
        findViewById<View>(R.id.gpsPill).setOnClickListener {
            when (gpsState) {
                GpsState.NO_PERMISSION -> requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    REQ_LOC
                )
                GpsState.OFF -> tryStart(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                else -> Unit
            }
        }
    }

    private fun pickPhoto() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(CarPhoto.pickIntent(), CarPhoto.REQ_PICK)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun reloadCarPhotoIfNeeded() {
        val current = Prefs.carUri(this)?.toString()
        if (current == loadedPhoto) return
        loadedPhoto = current
        carPhoto.setImageBitmap(CarPhoto.load(this, 1400))
    }

    /** Entrada suave: aparece y se desliza desde la izquierda (600 ms). */
    private fun animateCarIn() {
        carPhoto.alpha = 0f
        carPhoto.translationX = -dp(60f)
        carPhoto.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(600)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun applyAccent() {
        accent = Prefs.accent(this)
        val tint = ColorStateList.valueOf(accent)
        phoneIconBox.backgroundTintList = tint
        btnPlay.backgroundTintList = tint
        artBox.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(accent, getColor(R.color.track))
        ).apply { cornerRadius = dp(12f) }
        nowCap.setTextColor(accent)
        speedUnit.setTextColor(accent)
        progress.color = accent
        speedBar.color = accent
        mapView.accent = accent
        stripes.accent = accent
        updateDate()
    }

    private fun updateDate() {
        val es = Locale("es", "MX")
        val now = Date()
        val day = SimpleDateFormat("EEEE", es).format(now).uppercase(es)
        val rest = SimpleDateFormat("d MMM", es).format(now).replace(".", "").uppercase(es)
        val text = "$day  ·  $rest"
        val span = SpannableString(text)
        val dot = text.indexOf('·')
        if (dot >= 0) span.setSpan(ForegroundColorSpan(accent), dot, dot + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        dateView.text = span
    }

    // ---------- Botones de apps ----------

    private fun buildTiles() {
        tileRow1.removeAllViews()
        tileRow2.removeAllViews()
        val gap = resources.getDimensionPixelSize(R.dimen.gap)
        DockSlots.ALL.forEachIndexed { i, slot ->
            val row = if (i < 3) tileRow1 else tileRow2
            val v = layoutInflater.inflate(R.layout.tile_item, row, false)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (i % 3 != 0) lp.marginStart = gap
            v.layoutParams = lp
            v.findViewById<ImageView>(R.id.tileIcon).setImageResource(slot.iconRes)
            val label = DockSlots.labelFor(this, dockPrefs, i)
            v.findViewById<TextView>(R.id.tileLabel).text = label
            v.contentDescription = label
            v.setOnClickListener { openSlot(i) }
            v.setOnLongClickListener {
                pickForSlot(i)
                true
            }
            row.addView(v)
        }
    }

    private fun openSlot(index: Int) {
        val intent = DockSlots.resolve(this, dockPrefs, index)
        if (intent == null || !tryStart(intent)) pickForSlot(index)
    }

    private fun pickForSlot(index: Int) {
        startActivity(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK_SLOT, index))
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
                // sin ubicación por red; el mapa espera al GPS
            }
            setGpsState(if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) GpsState.SEARCHING else GpsState.OFF)
        } catch (e: SecurityException) {
            setGpsState(GpsState.NO_PERMISSION)
        } catch (e: IllegalArgumentException) {
            setGpsState(GpsState.OFF)
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
        val kmh = Prefs.useKmh(this)
        val v = if (kmh) mps * 3.6f else mps * 2.23694f
        speedValue.text = (if (v < 1f) 0 else v.roundToInt()).toString()
        speedBar.level = v / (if (kmh) MAX_KMH else MAX_MPH)
    }

    private fun setGpsState(state: GpsState) {
        gpsState = state
        val (color, text) = when (state) {
            GpsState.OK -> getColor(R.color.gps_ok) to R.string.gps_ok
            GpsState.SEARCHING -> getColor(R.color.gps_wait) to R.string.gps_searching
            GpsState.OFF -> getColor(R.color.text_dim) to R.string.gps_off
            GpsState.NO_PERMISSION -> getColor(R.color.text_dim) to R.string.gps_no_permission
        }
        gpsDot.backgroundTintList = ColorStateList.valueOf(color)
        gpsText.setText(text)
    }

    private fun showLastKnownLocation(lm: LocationManager) {
        if (mapView.hasLocation) return
        try {
            val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { lm.allProviders.contains(it) }
                .mapNotNull { lm.getLastKnownLocation(it) }
                .maxByOrNull { it.time }
            if (best != null) mapView.setLocation(best.latitude, best.longitude, null)
        } catch (e: SecurityException) {
            // sin permiso todavía
        }
    }

    // ---------- Mapa y navegación ----------

    /** Abre Google Maps; si no está instalado, la app elegida para "Mapas". */
    private fun openGoogleMaps() {
        val gm = packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
        if (gm == null || !tryStart(gm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) openSlot(DockSlots.MAPS)
    }

    private fun updateNav() {
        if (!ready) return
        val nav = NavState.current
        if (nav == null) {
            navPanel.visibility = View.GONE
            mapLabel.visibility = View.VISIBLE
            mapHint.setText(R.string.maps_hint)
            return
        }
        navTitle.text = nav.title.ifBlank { nav.text }
        navText.text = if (nav.title.isBlank()) "" else nav.text
        navText.visibility = if (navText.text.isNullOrBlank()) View.GONE else View.VISIBLE
        navSub.text = nav.sub
        navSub.visibility = if (nav.sub.isBlank()) View.GONE else View.VISIBLE
        navSub.setTextColor(accent)
        if (nav.icon != null) {
            navIcon.setImageDrawable(nav.icon)
            navIcon.visibility = View.VISIBLE
        } else {
            navIcon.visibility = View.GONE
        }
        navPanel.visibility = View.VISIBLE
        mapLabel.visibility = View.GONE
        mapHint.setText(R.string.maps_hint_nav)
    }

    // ---------- Música ----------

    private fun setupMediaButtons() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToPrevious() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        btnPlay.setOnClickListener {
            val c = controller
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            } else {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToNext() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }
        findViewById<View>(R.id.mediaCard).setOnClickListener {
            if (!hasNotificationAccess()) {
                if (!tryStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) {
                    Toast.makeText(this, R.string.no_access_screen, Toast.LENGTH_LONG).show()
                }
                return@setOnClickListener
            }
            val launch = controller?.packageName?.let { packageManager.getLaunchIntentForPackage(it) }
            if (launch == null || !tryStart(launch)) openSlot(5)
        }
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
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        bindController(playing ?: list?.firstOrNull())
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
        if (!::trackTitle.isInitialized) return
        val c = controller
        if (c == null) {
            val access = hasNotificationAccess()
            trackTitle.setText(if (access) R.string.nothing_playing else R.string.grant_access_title)
            trackArtist.text = if (access) "" else getString(R.string.grant_access_sub)
            showArt(null)
            setPlayIcon((getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive)
            updateProgress()
            return
        }
        val md = c.metadata
        trackTitle.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: appLabel(c.packageName)
        trackArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        showArt(md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART))
        setPlayIcon(c.playbackState?.state == PlaybackState.STATE_PLAYING)
        updateProgress()
    }

    private fun showArt(bitmap: android.graphics.Bitmap?) {
        if (bitmap != null) {
            albumArt.setImageBitmap(bitmap)
            albumArt.visibility = View.VISIBLE
            artIcon.visibility = View.GONE
        } else {
            albumArt.visibility = View.GONE
            artIcon.visibility = View.VISIBLE
        }
    }

    private fun updateProgress() {
        if (!::progress.isInitialized) return
        val c = controller
        val duration = c?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (c == null || duration <= 0L) {
            progress.level = 0f
            posText.text = ""
            durText.text = ""
            return
        }
        val st = c.playbackState
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        pos = pos.coerceIn(0L, duration)
        progress.level = pos.toFloat() / duration
        posText.text = formatTime(pos)
        durText.text = formatTime(duration)
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60)
    }

    private fun appLabel(pkg: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
}
