package com.juandiego.priuslauncher.v4

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.juandiego.priuslauncher.AppsActivity
import com.juandiego.priuslauncher.CrashReport
import com.juandiego.priuslauncher.Prefs
import com.juandiego.priuslauncher.R
import com.juandiego.priuslauncher.Themer
import com.juandiego.priuslauncher.Trip
import com.juandiego.priuslauncher.Weather
import com.juandiego.priuslauncher.hideSystemUi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.hypot

/**
 * CarGO v4: barra de música fija arriba; abajo 4 espacios alineados (mapa | carro | clima + sección)
 * que se intercambian en el modo editar. Todo se ajusta a la pantalla (1024x600, 1280x720, 1920x1080).
 */
class V4Activity : Activity(), SettingsHost {

    private companion object {
        const val REQ_LOC = 21
        const val REQ_GALLERY = 22
        const val REQ_WIDGET_PICK = 23
        const val REQ_WIDGET_CONF = 24
        const val HOST_ID = 4204
        const val GPS_STALE_MS = 5000L
        val US_STATES = mapOf(
            "Alabama" to "AL", "Alaska" to "AK", "Arizona" to "AZ", "Arkansas" to "AR", "California" to "CA", "Colorado" to "CO",
            "Connecticut" to "CT", "Delaware" to "DE", "Florida" to "FL", "Georgia" to "GA", "Hawaii" to "HI", "Idaho" to "ID",
            "Illinois" to "IL", "Indiana" to "IN", "Iowa" to "IA", "Kansas" to "KS", "Kentucky" to "KY", "Louisiana" to "LA",
            "Maine" to "ME", "Maryland" to "MD", "Massachusetts" to "MA", "Michigan" to "MI", "Minnesota" to "MN", "Mississippi" to "MS",
            "Missouri" to "MO", "Montana" to "MT", "Nebraska" to "NE", "Nevada" to "NV", "New Hampshire" to "NH", "New Jersey" to "NJ",
            "New Mexico" to "NM", "New York" to "NY", "North Carolina" to "NC", "North Dakota" to "ND", "Ohio" to "OH", "Oklahoma" to "OK",
            "Oregon" to "OR", "Pennsylvania" to "PA", "Rhode Island" to "RI", "South Carolina" to "SC", "South Dakota" to "SD",
            "Tennessee" to "TN", "Texas" to "TX", "Utah" to "UT", "Vermont" to "VT", "Virginia" to "VA", "Washington" to "WA",
            "West Virginia" to "WV", "Wisconsin" to "WI", "Wyoming" to "WY", "District of Columbia" to "DC"
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var root: Root
    private var u: U? = null
    private var pal = Pal(false)
    private var ready = false
    private var savedState: Bundle? = null

    private var bar: MusicBar? = null
    private var editBar: Card? = null
    private var mapCard: MapCard? = null
    private var center: CenterCard? = null
    private var weather: WeatherCard? = null
    private var swap: SwapCard? = null
    private var settings: SettingsPanel? = null
    private var media: MediaHub? = null
    private var widgetHost: AppWidgetHost? = null

    private var designW = 1067f
    private val rects = HashMap<String, FloatArray>()   // espacio → x, y, w, h (unidades del diseño)
    private var slots = V4Prefs.DEFAULT_SLOTS

    private var editing = false
    private val outlines = ArrayList<View>()
    private var hole: View? = null

    private var lm: LocationManager? = null
    private var gpsListening = false
    private var lastFix = 0L
    private var lastLoc: Location? = null
    private var geoLoc: Location? = null
    private var geoAt = 0L
    private var bearing = 0f
    private var gpsColor = 0
    private var ticks = 0

    private var galleryDone: ((Boolean) -> Unit)? = null
    private var widgetDone: ((Boolean) -> Unit)? = null
    private var pendingWidgetId = -1

    override fun attachBaseContext(newBase: Context) {
        // las pantallas de la app (lista de apps) siguen el mismo tema claro/oscuro
        Prefs.setLightTheme(newBase, !V4Prefs.isDark(newBase))
        super.attachBaseContext(Themer.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trace = CrashReport.take(this)
        if (trace != null) {
            CrashReport.show(this, trace)
            return
        }
        savedState = savedInstanceState
        pal = Pal(V4Prefs.isDark(this), V4Prefs.style(this))
        root = Root(this)
        root.setBackgroundColor(pal.bg)
        setContentView(root)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(pal.bg))
        hideSystemUi()
        widgetHost = try { AppWidgetHost(applicationContext, HOST_ID) } catch (e: Exception) { null }
        root.post { guarded { build() } }
    }

    private inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            CrashReport.save(this, e)
            ready = false
            CrashReport.show(this, CrashReport.take(this) ?: e.toString())
        }
    }

    // ---------- Armado de la pantalla ----------

    private fun build() {
        val w = root.width.toFloat()
        val h = root.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val s = h / 600f
        val uu = U(this, s, pal)
        u = uu
        designW = w / s
        val cw = designW - 612f
        rects["L"] = floatArrayOf(10f, 78f, 286f, 512f)
        rects["C"] = floatArrayOf(306f, 78f, cw, 512f)
        rects["R1"] = floatArrayOf(designW - 296f, 78f, 286f, 248f)
        rects["R2"] = floatArrayOf(designW - 296f, 336f, 286f, 254f)

        media = MediaHub(this) { showMedia() }
        val b = MusicBar(uu, designW - 20f, { openApps() }, { openSettings() }) { media }
        root.place(uu, b, 10f, 10f, designW - 20f, 58f)
        bar = b

        mapCard = MapCard(uu, this, savedState, {}, {})
        center = CenterCard(uu, this, widgetHost) { lang -> setLanguage(lang) }
        weather = WeatherCard(uu)
        swap = SwapCard(uu, this, null) { lang -> setLanguage(lang) }

        slots = V4Prefs.slots(this)
        layoutCards(false)
        Weather.load(this)
        Forecast.load(this)
        weather?.refresh()
        ready = true
        startAll()
        handler.post { showMedia() }
    }

    private fun cardFor(id: String): View? = when (id) {
        V4Prefs.W_MAP -> mapCard
        V4Prefs.W_CAR -> center
        V4Prefs.W_WEATHER -> weather
        else -> swap
    }

    private val slotNames = listOf("L", "C", "R1", "R2")

    /** Coloca cada widget en su espacio; si animate, se deslizan desde donde estaban. */
    private fun layoutCards(animate: Boolean) {
        val uu = u ?: return
        slotNames.forEachIndexed { i, slot ->
            val v = cardFor(slots[i]) ?: return@forEachIndexed
            val r = rects[slot] ?: return@forEachIndexed
            val oldX = v.x
            val oldY = v.y
            val hadParent = v.parent != null
            root.place(uu, v, r[0], r[1], r[2], r[3])
            val tall = r[3] > 300f
            when (v) {
                is MapCard -> v.setCompact(!tall)
                is WeatherCard -> v.setTall(tall)
                is SwapCard -> v.setTall(tall)
                is CenterCard -> v.applyMode(tall)
            }
            if (animate && hadParent) {
                v.translationX = oldX - uu.px(r[0])
                v.translationY = oldY - uu.px(r[1])
                v.animate().translationX(0f).translationY(0f).setDuration(260).start()
            } else {
                v.translationX = 0f
                v.translationY = 0f
            }
        }
        // la barra queda encima de todo lo demás
        bar?.bringToFront()
        editBar?.bringToFront()
        settings?.bringToFront()
    }

    // ---------- Modo editar ----------

    override fun enterEditMode() {
        val uu = u ?: return
        closeSettings()
        if (editing) return
        editing = true
        swap?.editing = true
        center?.swap?.editing = true
        bar?.visibility = View.INVISIBLE
        val eb = Card(uu).apply { radius = 18f }
        eb.addView(uu.text(16f, 600, pal.tx, getString(R.string.v4_edit)), lpAt(uu, 20f, 19f, -1f, -1f))
        val reset = pillButton(uu, getString(R.string.v4_reset), pal.btn, pal.tx) { resetLayout() }
        eb.addView(reset, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, uu.i(36f), Gravity.TOP or Gravity.END).apply {
            rightMargin = uu.i(122f); topMargin = uu.i(11f)
        })
        val done = pillButton(uu, getString(R.string.v4_done), pal.accent, Color.WHITE) { exitEditMode() }
        eb.addView(done, FrameLayout.LayoutParams(uu.i(100f), uu.i(36f), Gravity.TOP or Gravity.END).apply {
            rightMargin = uu.i(12f); topMargin = uu.i(11f)
        })
        root.place(uu, eb, 10f, 10f, designW - 20f, 58f)
        editBar = eb
        showOutlines()
        center?.setChip(true) { openSettings() }
    }

    private fun pillButton(uu: U, label: String, bg: Int, fg: Int, click: () -> Unit): TextView = TextView(this).apply {
        text = label
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, uu.px(14f))
        typeface = Fonts.w600(this@V4Activity)
        setTextColor(fg)
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(uu.i(16f), 0, uu.i(16f), 0)
        background = uu.round(bg, 12f)
        setOnClickListener { click() }
    }

    private fun showOutlines() {
        val uu = u ?: return
        outlines.forEach { root.removeView(it) }
        outlines.clear()
        for (slot in slotNames) {
            val r = rects[slot] ?: continue
            val o = DashOutline(uu)
            root.place(uu, o, r[0] - 3f, r[1] - 3f, r[2] + 6f, r[3] + 6f)
            outlines.add(o)
        }
    }

    private fun exitEditMode() {
        if (!editing) return
        editing = false
        swap?.editing = false
        center?.swap?.editing = false
        V4Prefs.setSlots(this, slots)
        outlines.forEach { root.removeView(it) }
        outlines.clear()
        hole?.let { root.removeView(it) }
        hole = null
        editBar?.let { root.removeView(it) }
        editBar = null
        bar?.visibility = View.VISIBLE
        center?.setChip(false) {}
    }

    private fun slotAt(px: Float, py: Float): Int {
        val uu = u ?: return -1
        slotNames.forEachIndexed { i, s ->
            val r = rects[s] ?: return@forEachIndexed
            if (px >= uu.px(r[0]) && px <= uu.px(r[0] + r[2]) && py >= uu.px(r[1]) && py <= uu.px(r[1] + r[3])) return i
        }
        return -1
    }

    // arrastre
    private var dragIndex = -1
    private var dragView: View? = null
    private var downX = 0f
    private var downY = 0f
    private var dragging = false

    private fun dragStart(index: Int, x: Float, y: Float) {
        dragIndex = index
        dragView = cardFor(slots[index])
        downX = x
        downY = y
        dragging = false
    }

    private fun dragMove(x: Float, y: Float) {
        val v = dragView ?: return
        val uu = u ?: return
        if (!dragging && hypot(x - downX, y - downY) > ViewConfiguration.get(this).scaledTouchSlop) {
            dragging = true
            val r = rects[slotNames[dragIndex]] ?: return
            val ho = DashOutline(uu, solid = true)
            root.place(uu, ho, r[0], r[1], r[2], r[3])
            hole = ho
            v.bringToFront()
            v.elevation = uu.px(18f)
            v.animate().scaleX(1.03f).scaleY(1.03f).rotation(-1.5f).setDuration(120).start()
        }
        if (dragging) {
            v.translationX = x - downX
            v.translationY = y - downY
        }
    }

    private fun dragEnd(x: Float, y: Float) {
        val v = dragView ?: return
        dragView = null
        hole?.let { root.removeView(it) }
        hole = null
        v.elevation = 0f
        v.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(120).start()
        if (!dragging) return
        dragging = false
        val target = slotAt(x, y)
        if (target >= 0 && target != dragIndex) {
            val l = slots.toMutableList()
            val t = l[target]; l[target] = l[dragIndex]; l[dragIndex] = t
            slots = l
            layoutCards(true)
            showOutlines()
        } else {
            v.animate().translationX(0f).translationY(0f).setDuration(200).start()
        }
        bar?.bringToFront()
        editBar?.bringToFront()
    }

    /**
     * Raíz: en el modo editar maneja el arrastre de los widgets.
     * Fuera del modo editar los toques van directo a los widgets (el modo editar solo se abre desde Ajustes).
     */
    private inner class Root(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (!ready || settings != null || !editing) return super.dispatchTouchEvent(ev)
            val x = ev.x
            val y = ev.y
            val inCards = slotAt(x, y) >= 0
            if (ev.actionMasked == MotionEvent.ACTION_DOWN && (!inCards || center?.chipHit(ev.rawX, ev.rawY) == true)) {
                passThrough = true
            }
            if (passThrough) {
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) passThrough = false
                return super.dispatchTouchEvent(ev)
            }
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (inCards) dragStart(slotAt(x, y), x, y)
                MotionEvent.ACTION_MOVE -> dragMove(x, y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragEnd(x, y)
            }
            return true
        }
        private var passThrough = false
    }

    // ---------- Ajustes ----------

    private fun openSettings() {
        val uu = u ?: return
        if (settings != null) return
        val p = SettingsPanel(uu, this, this)
        root.addView(p, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        p.alpha = 0f
        p.animate().alpha(1f).setDuration(160).start()
        settings = p
    }

    override fun closeSettings() {
        val p = settings ?: return
        settings = null
        root.removeView(p)
    }

    override fun saved(before: V4Prefs.Draft, after: V4Prefs.Draft) {
        closeSettings()
        if (before.lang != after.lang || V4Prefs.isDark(this) != pal.dark || V4Prefs.style(this) != pal.style) {
            recreate()
            return
        }
        layoutCards(false)
        mapCard?.buildButtons()
    }

    override fun resetLayout() {
        V4Prefs.resetLayout(this)
        slots = V4Prefs.DEFAULT_SLOTS
        swap?.let { s -> if (s.kind != V4Prefs.K_SPEED) { V4Prefs.setSwapKind(this, V4Prefs.K_SPEED) } }
        val uu = u ?: return
        swap?.let { root.removeView(it) }
        swap = SwapCard(uu, this, null) { lang -> setLanguage(lang) }.also { it.editing = editing }
        layoutCards(true)
        if (editing) showOutlines()
        Toast.makeText(this, R.string.v4_layout_reset_done, Toast.LENGTH_SHORT).show()
    }

    override fun pickMapApp() {
        startSafe(this, Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK_SLOT, com.juandiego.priuslauncher.DockSlots.MAPS))
    }

    override fun pickGallery(done: (Boolean) -> Unit) {
        galleryDone = done
        if (!startForResult(CenterCard.pickIntent(), REQ_GALLERY)) {
            galleryDone = null
            Toast.makeText(this, R.string.v4_cant_open, Toast.LENGTH_SHORT).show()
            done(false)
        }
    }

    override fun pickAppWidget(done: (Boolean) -> Unit) {
        val host = widgetHost
        if (host == null) {
            Toast.makeText(this, R.string.v4_no_widget_picker, Toast.LENGTH_LONG).show()
            done(false)
            return
        }
        val id = host.allocateAppWidgetId()
        pendingWidgetId = id
        widgetDone = done
        val i = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        if (!startForResult(i, REQ_WIDGET_PICK)) {
            host.deleteAppWidgetId(id)
            pendingWidgetId = -1
            widgetDone = null
            Toast.makeText(this, R.string.v4_no_widget_picker, Toast.LENGTH_LONG).show()
            done(false)
        }
    }

    private fun startForResult(i: Intent, req: Int): Boolean = try {
        @Suppress("DEPRECATION")
        startActivityForResult(i, req)
        true
    } catch (e: Exception) {
        false
    }

    private fun finishWidget(ok: Boolean) {
        val host = widgetHost
        val id = pendingWidgetId
        pendingWidgetId = -1
        if (ok && id >= 0) {
            val old = V4Prefs.appWidgetId(this)
            if (old >= 0 && old != id) try { host?.deleteAppWidgetId(old) } catch (e: Exception) { }
            V4Prefs.setAppWidgetId(this, id)
        } else if (id >= 0) {
            try { host?.deleteAppWidgetId(id) } catch (e: Exception) { }
        }
        widgetDone?.invoke(ok)
        widgetDone = null
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_GALLERY -> {
                val uri = if (resultCode == RESULT_OK) data?.data else null
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) {
                        // algunos selectores no lo permiten; igual se usa
                    }
                    V4Prefs.setGalleryUri(this, uri)
                    Toast.makeText(this, R.string.v4_photo_saved, Toast.LENGTH_SHORT).show()
                }
                galleryDone?.invoke(uri != null)
                galleryDone = null
            }
            REQ_WIDGET_PICK -> {
                if (resultCode != RESULT_OK) { finishWidget(false); return }
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                pendingWidgetId = id
                val info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id)
                if (info?.configure != null) {
                    val conf = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).setComponent(info.configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    if (!startForResult(conf, REQ_WIDGET_CONF)) finishWidget(true)
                } else {
                    finishWidget(info != null)
                }
            }
            REQ_WIDGET_CONF -> finishWidget(resultCode == RESULT_OK)
        }
    }

    // ---------- Acciones ----------

    private fun openApps() {
        if (!startSafe(this, Intent(this, AppsActivity::class.java))) Toast.makeText(this, R.string.v4_cant_open, Toast.LENGTH_SHORT).show()
    }

    private fun setLanguage(lang: String) {
        if (lang == Prefs.lang(this)) return
        Prefs.setLang(this, lang)
        recreate()
    }

    private fun showMedia() {
        val m = media ?: return
        bar?.showMedia(m.now(), m.sourceLabel())
    }

    // ---------- Ciclo de vida ----------

    private fun startAll() {
        startGps()
        media?.start()
        Weather.addListener(weatherListener)
        com.juandiego.priuslauncher.NavState.addListener(navListener)
        mapCard?.showNav(com.juandiego.priuslauncher.NavState.current)
        Forecast.onChange = { weather?.refresh() }
        try { widgetHost?.startListening() } catch (e: Exception) { }
        mapCard?.panel?.onStart()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private val weatherListener: () -> Unit = { weather?.refresh() }
    private val navListener: () -> Unit = { mapCard?.showNav(com.juandiego.priuslauncher.NavState.current) }

    override fun onStart() {
        super.onStart()
        if (ready) guarded { startAll() }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (!ready) return
        if (V4Prefs.isDark(this) != pal.dark || V4Prefs.style(this) != pal.style) {
            recreate()
            return
        }
        mapCard?.panel?.onResume()
        mapCard?.buildButtons()
        mapCard?.showNav(com.juandiego.priuslauncher.NavState.current)
        swap?.build()
        center?.swap?.build()
        showMedia()
    }

    override fun onPause() {
        super.onPause()
        if (ready) mapCard?.panel?.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!ready) return
        stopGps()
        media?.stop()
        Weather.removeListener(weatherListener)
        com.juandiego.priuslauncher.NavState.removeListener(navListener)
        Forecast.onChange = null
        try { widgetHost?.stopListening() } catch (e: Exception) { }
        mapCard?.panel?.onStop()
        Trip.save(this)
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        if (ready) mapCard?.panel?.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (ready) mapCard?.panel?.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (ready) mapCard?.panel?.onSave(outState)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            settings != null -> closeSettings()
            editing -> exitEditMode()
            // es la pantalla de inicio: "atrás" no hace nada
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOC && ready) guarded { startGps() }
    }

    private val ticker = object : Runnable {
        override fun run() {
            ticks++
            bar?.tick()
            swap?.tick()
            center?.swap?.tick()
            showMedia()
            if (gpsListening && lastFix > 0 && SystemClock.elapsedRealtime() - lastFix > GPS_STALE_MS) {
                lastFix = 0
                showSpeed(0f)
                setGps(0xFFFFC23D.toInt())
            }
            if (ticks % 5 == 0) bar?.setStatus(Radios.wifiConnected(this@V4Activity), gpsColor)
            if (ticks % 30 == 0) {
                // 5 PM y 6 AM: cambia el tema (automático) y las luces
                if ((V4Prefs.isDark(this@V4Activity) != pal.dark || V4Prefs.style(this@V4Activity) != pal.style) && settings == null && !editing) {
                    recreate()
                    return
                }
                center?.stage?.lightsOn = V4Prefs.lightsOn(this@V4Activity)
            }
            handler.postDelayed(this, 1000)
        }
    }

    // ---------- GPS (velocidad, mapa, calle, rumbo, clima) ----------

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastFix = SystemClock.elapsedRealtime()
            onLocation(location, true)
        }
        override fun onProviderEnabled(provider: String) = setGps(0xFFFFC23D.toInt())
        override fun onProviderDisabled(provider: String) {
            showSpeed(0f)
            setGps(pal.faint)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private val networkListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (lastFix == 0L) onLocation(location, false)
        }
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    private fun onLocation(loc: Location, gps: Boolean) {
        lastLoc = loc
        if (gps) {
            showSpeed(if (loc.hasSpeed()) loc.speed else 0f)
            setGps(pal.green)
            Trip.onLocation(this, loc)
        }
        val moving = loc.hasSpeed() && loc.speed > 1.5f
        if (loc.hasBearing() && moving) bearing = loc.bearing
        if (moving && loc.hasBearing()) movingAt = SystemClock.elapsedRealtime()
        mapCard?.setLocation(loc.latitude, loc.longitude, if (loc.hasBearing() && moving) loc.bearing else null)
        mapCard?.located = true
        swap?.setBearing(bearing, movingAt > 0)
        center?.swap?.setBearing(bearing, movingAt > 0)
        Weather.update(this, loc)
        Forecast.update(this, loc)
        geocode(loc)
    }

    private fun showSpeed(mps: Float) {
        swap?.setSpeed(mps)
        center?.swap?.setSpeed(mps)
    }

    private fun setGps(color: Int) {
        gpsColor = color
        bar?.setStatus(Radios.wifiConnected(this), color)
    }

    /** Nombre de la calle y ciudad (cada 20 s o 100 m), en segundo plano. */
    private fun geocode(loc: Location) {
        val now = SystemClock.elapsedRealtime()
        val prev = geoLoc
        if (prev != null && now - geoAt < 20_000 && prev.distanceTo(loc) < 100f) {
            updatePlaceDir()
            return
        }
        geoLoc = loc
        geoAt = now
        val lang = Prefs.lang(this)
        Thread {
            val addr = try {
                @Suppress("DEPRECATION")
                Geocoder(applicationContext, Locale(lang)).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing || addr == null) return@runOnUiThread
                street = addr.thoroughfare
                val state = addr.adminArea?.let { a -> if (addr.countryCode == "US") US_STATES[a] ?: a else a }
                cityName = listOfNotNull(addr.locality ?: addr.subAdminArea, state).joinToString(", ")
                updatePlaceDir()
            }
        }.start()
    }

    private var street: String? = null
    private var movingAt = 0L
    private var cityName = ""

    private fun updatePlaceDir() {
        // el rumbo solo se muestra si el carro va en movimiento (sin GPS en movimiento no hay rumbo real)
        val dir = if (movingAt > 0 && SystemClock.elapsedRealtime() - movingAt < 15_000) cardinal(this, bearing) else null
        mapCard?.setPlace(street, listOfNotNull(cityName.ifBlank { null }, dir).joinToString(" · "))
    }

    private fun hasLocationPermission() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startGps() {
        if (gpsListening) return
        if (!hasLocationPermission()) {
            setGps(pal.faint)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOC)
            return
        }
        val m = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm = m
        try {
            m.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper())
            gpsListening = true
            try {
                if (m.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                    m.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, networkListener, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // sin ubicación por red
            }
            val best = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
                .filter { m.allProviders.contains(it) }
                .mapNotNull { try { m.getLastKnownLocation(it) } catch (e: SecurityException) { null } }
                .maxByOrNull { it.time }
            if (best != null) onLocation(best, false)
            setGps(if (m.isProviderEnabled(LocationManager.GPS_PROVIDER)) 0xFFFFC23D.toInt() else pal.faint)
        } catch (e: SecurityException) {
            setGps(pal.faint)
        } catch (e: IllegalArgumentException) {
            setGps(pal.faint)
        }
    }

    private fun stopGps() {
        if (!gpsListening) return
        try {
            lm?.removeUpdates(locationListener)
            lm?.removeUpdates(networkListener)
        } catch (e: SecurityException) {
            // nada
        }
        gpsListening = false
    }
}
