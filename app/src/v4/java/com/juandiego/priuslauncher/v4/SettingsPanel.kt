package com.juandiego.priuslauncher.v4

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.juandiego.priuslauncher.R

/** Interruptor estilo iOS (verde encendido). */
class Toggle(private val u: U) : View(u.ctx) {
    var on = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = if (on) u.pal.green else u.pal.line
        c.drawRoundRect(0f, 0f, w, h, h / 2f, h / 2f, p)
        val r = u.px(10f)
        val cx = if (on) w - u.px(3f) - r else u.px(3f) + r
        p.color = 0x40000000
        c.drawCircle(cx, h / 2f + u.px(0.8f), r, p)
        p.color = Color.WHITE
        c.drawCircle(cx, h / 2f, r, p)
    }
}

/** Wi-Fi y Bluetooth: prender/apagar con el sistema de la pantalla. */
object Radios {
    fun wifiOn(c: Context): Boolean = try {
        (c.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) {
        false
    }

    fun wifiConnected(c: Context): Boolean = try {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    } catch (e: Exception) {
        false
    }

    @Suppress("DEPRECATION")
    fun setWifi(act: Activity, on: Boolean) {
        val ok = try {
            (act.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).setWifiEnabled(on)
        } catch (e: Exception) {
            false
        }
        if (!ok) open(act, if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIFI_SETTINGS)
    }

    @Suppress("DEPRECATION")
    fun btAdapter(): BluetoothAdapter? = try {
        BluetoothAdapter.getDefaultAdapter()
    } catch (e: Exception) {
        null
    }

    fun btOn(): Boolean = try {
        btAdapter()?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /**
     * Si Android maneja el Bluetooth, lo prende/apaga directo.
     * Si no (en algunas pantallas el Bluetooth es del módulo del estéreo), abre la app de Bluetooth de la pantalla.
     */
    @Suppress("DEPRECATION", "MissingPermission")
    fun setBt(act: Activity, on: Boolean) {
        val a = btAdapter()
        val ok = try {
            if (a != null && Build.VERSION.SDK_INT < 33) (if (on) a.enable() else a.disable()) else false
        } catch (e: SecurityException) {
            false
        }
        if (ok) return
        val stereoBt = listOf("com.syu.bt", "com.microntek.bluetooth", "com.ts.bt", "com.fyt.bt")
            .firstNotNullOfOrNull { act.packageManager.getLaunchIntentForPackage(it) }
        if (a == null && stereoBt != null && startSafe(act, stereoBt.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return
        open(act, Settings.ACTION_BLUETOOTH_SETTINGS, Settings.ACTION_SETTINGS)
    }

    private fun open(act: Activity, action: String, fallback: String) {
        if (!startSafe(act, Intent(action))) startSafe(act, Intent(fallback))
    }
}

interface SettingsHost {
    fun pickGallery(done: (Boolean) -> Unit)
    fun pickAppWidget(done: (Boolean) -> Unit)
    fun enterEditMode()
    fun resetLayout()
    fun saved(before: V4Prefs.Draft, after: V4Prefs.Draft)
    fun closeSettings()
    fun pickMapApp()
}

/** Ajustes: toda la configuración del launcher en una sola pantalla. */
class SettingsPanel(private val u: U, private val act: Activity, private val host: SettingsHost) : FrameLayout(u.ctx) {

    private val before = V4Prefs.Draft(act)
    private val draft = V4Prefs.Draft(act)
    private val body = FrameLayout(u.ctx)
    private var wifiToggle: Toggle? = null
    private var wifiValue: TextView? = null
    private var btToggle: Toggle? = null
    private var btValue: TextView? = null

    private val radios = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshRadios()
    }

    init {
        setBackgroundColor(0x66000000)
        isClickable = true
        setOnClickListener { host.closeSettings() }
        val panel = FrameLayout(u.ctx).apply {
            background = u.round(u.pal.card, 26f)
            elevation = u.px(24f)
            isClickable = true
        }
        addView(panel, FrameLayout.LayoutParams(u.i(820f), u.i(540f), Gravity.CENTER))
        panel.addView(body, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        build()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        try {
            act.registerReceiver(radios, IntentFilter().apply {
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            })
        } catch (e: Exception) {
            // sin avisos: se ve el estado al abrir
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            act.unregisterReceiver(radios)
        } catch (e: Exception) {
            // no estaba registrado
        }
    }

    private fun label(x: Float, y: Float, res: Int) {
        val t = u.text(11f, 700, u.pal.faint, act.getString(res))
        t.letterSpacing = 0.8f / 11f
        body.addView(t, lpAt(u, x, y, -1f, -1f))
    }

    private fun seg(x: Float, y: Float, w: Float, items: List<Pair<String, String?>>, selected: Int, onPick: (Int) -> Unit) {
        val box = LinearLayout(u.ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = u.round(u.pal.btn, 13f)
            setPadding(u.i(4f), u.i(4f), u.i(4f), u.i(4f))
        }
        items.forEachIndexed { i, (main, sub) ->
            val on = i == selected
            val cell = LinearLayout(u.ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                if (on) {
                    background = u.round(u.pal.card, 10f)
                    elevation = u.px(1.5f)
                }
                addView(u.text(13f, 600, if (on) u.pal.tx else u.pal.sub, main).apply { gravity = Gravity.CENTER })
                if (sub != null) addView(u.text(10f, 500, u.pal.faint, sub).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(1f) })
                setOnClickListener { onPick(i) }
            }
            box.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = u.i(4f) })
        }
        body.addView(box, lpAt(u, x, y, w, 44f))
    }

    private fun row(icon: Int, iconW: Float, iconH: Float, name: Int, value: TextView?, right: View, click: () -> Unit): View =
        LinearLayout(u.ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(u.i(14f), 0, u.i(14f), 0)
            val ic = FrameLayout(u.ctx)
            ic.addView(u.icon(icon, u.pal.sub, iconW, iconH), FrameLayout.LayoutParams(u.i(iconW), u.i(iconH), Gravity.CENTER))
            addView(ic, LinearLayout.LayoutParams(u.i(20f), u.i(20f)))
            addView(u.text(14f, 600, u.pal.tx, act.getString(name)), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = u.i(12f) })
            if (value != null) addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = u.i(12f) })
            addView(right)
            setOnClickListener { click() }
        }

    private fun group(x: Float, y: Float, w: Float, rows: List<View>) {
        val box = LinearLayout(u.ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = u.round(u.pal.btn, 13f)
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) box.addView(View(u.ctx).apply { setBackgroundColor(u.pal.line) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, u.i(1f))))
            box.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(46f)))
        }
        body.addView(box, lpAt(u, x, y, w, -1f))
    }

    private fun build() {
        body.removeAllViews()
        val L = 24f
        val RX = 428f
        val CW = 368f

        body.addView(u.text(20f, 700, u.pal.tx, act.getString(R.string.v4_settings)), lpAt(u, L, 22f, -1f, -1f))
        val close = FrameLayout(u.ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(u.pal.btn) }
            addView(u.icon(R.drawable.v4_ic_close, u.pal.sub, 14f, 14f), FrameLayout.LayoutParams(u.i(14f), u.i(14f), Gravity.CENTER))
            setOnClickListener { host.closeSettings() }
        }
        body.addView(close, lpTopRight(u, 20f, 18f, 32f, 32f))

        // VEHÍCULO
        label(L, 74f, R.string.v4_vehicle)
        val nameRow = LinearLayout(u.ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = u.round(u.pal.btn, 13f)
            setPadding(u.i(14f), 0, u.i(14f), 0)
            addView(u.text(12f, 600, u.pal.faint, act.getString(R.string.v4_name)), LinearLayout.LayoutParams(u.i(56f), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(u.text(15f, 600, u.pal.tx, draft.name).apply { letterSpacing = 2f / 15f },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = u.i(10f) })
            addView(u.icon(R.drawable.v4_ic_pencil, u.pal.sub, 15f, 15f), LinearLayout.LayoutParams(u.i(15f), u.i(15f)))
            setOnClickListener { editName() }
        }
        body.addView(nameRow, lpAt(u, L, 94f, CW, 44f))

        label(L, 158f, R.string.v4_center)
        val panelIdx = when (draft.panel) { V4Prefs.PANEL_GALLERY -> 1; V4Prefs.PANEL_WIDGET -> 2; else -> 0 }
        seg(L, 178f, CW, listOf(act.getString(R.string.v4_seg_vehicle) to null, act.getString(R.string.v4_seg_gallery) to null, act.getString(R.string.v4_seg_widget) to null), panelIdx) { i ->
            when (i) {
                0 -> { draft.panel = V4Prefs.PANEL_CAR; build() }
                1 -> host.pickGallery { ok -> if (ok) { draft.panel = V4Prefs.PANEL_GALLERY; build() } }
                else -> chooseWidget()
            }
        }

        label(L, 242f, R.string.v4_lights)
        val lightsIdx = when (draft.lights) { V4Prefs.ALWAYS -> 1; V4Prefs.OFF -> 2; else -> 0 }
        seg(L, 262f, CW, listOf(act.getString(R.string.v4_auto) to act.getString(R.string.v4_lights_hours), act.getString(R.string.v4_always) to null, act.getString(R.string.v4_off) to null), lightsIdx) { i ->
            draft.lights = when (i) { 1 -> V4Prefs.ALWAYS; 2 -> V4Prefs.OFF; else -> V4Prefs.AUTO }
            build()
        }

        label(L, 326f, R.string.v4_theme)
        val themes = mutableListOf(V4Prefs.AUTO, V4Prefs.LIGHT, V4Prefs.DARK)
        if (V4Prefs.hasXmas(act)) themes.add(V4Prefs.XMAS)
        val themeNames = themes.map {
            act.getString(when (it) { V4Prefs.LIGHT -> R.string.v4_light; V4Prefs.DARK -> R.string.v4_dark; V4Prefs.XMAS -> R.string.v4_xmas; else -> R.string.v4_auto }) to null
        }
        seg(L, 346f, CW, themeNames, themes.indexOf(draft.theme).coerceAtLeast(0)) { i ->
            draft.theme = themes[i]
            build()
        }

        // APP DEL MAPA: la que abre el widget del mapa (y la que se ve al navegar)
        label(L, 410f, R.string.v4_map_app)
        val mapIdx = when (draft.mapApp) { V4Prefs.MAP_HERE -> 0; V4Prefs.MAP_WAZE -> 1; else -> 2 }
        seg(L, 430f, CW, listOf("HERE WeGo" to null, "Waze" to null, act.getString(R.string.v4_other_app) to null), mapIdx) { i ->
            when (i) {
                0 -> draft.mapApp = V4Prefs.MAP_HERE
                1 -> draft.mapApp = V4Prefs.MAP_WAZE
                else -> { draft.mapApp = V4Prefs.MAP_SLOT; host.pickMapApp() }
            }
            build()
        }

        // IDIOMA
        label(RX, 74f, R.string.v4_lang_title)
        seg(RX, 94f, CW, listOf("Español" to null, "English" to null), if (draft.lang == "en") 1 else 0) { i ->
            draft.lang = if (i == 1) "en" else "es"
            build()
        }

        // CONEXIONES
        label(RX, 158f, R.string.v4_connections)
        val wv = u.text(12f, 500, u.pal.faint, "")
        val wt = Toggle(u)
        val bv = u.text(12f, 500, u.pal.faint, "")
        val bt = Toggle(u)
        wifiValue = wv; wifiToggle = wt; btValue = bv; btToggle = bt
        group(RX, 178f, CW, listOf(
            row(R.drawable.v4_ic_wifi, 18f, 18f, R.string.v4_wifi, wv, wt.also { it.layoutParams = LinearLayout.LayoutParams(u.i(44f), u.i(26f)) }) {
                Radios.setWifi(act, !Radios.wifiOn(act)); postDelayed({ refreshRadios() }, 800)
            },
            row(R.drawable.v4_ic_bt, 15f, 18f, R.string.v4_bt, bv, bt.also { it.layoutParams = LinearLayout.LayoutParams(u.i(44f), u.i(26f)) }) {
                Radios.setBt(act, !Radios.btOn()); postDelayed({ refreshRadios() }, 800)
            }
        ))
        refreshRadios()

        // PANTALLA
        label(RX, 302f, R.string.v4_screen)
        fun chev(): View = u.icon(R.drawable.v4_ic_chev, u.pal.faint, 8f, 13f).apply { layoutParams = LinearLayout.LayoutParams(u.i(8f), u.i(13f)) }
        group(RX, 322f, CW, listOf(
            row(R.drawable.v4_ic_apps, 16f, 16f, R.string.v4_edit_widgets, null, chev()) {
                save()
                host.enterEditMode()
            },
            row(R.drawable.v4_ic_reset, 17f, 17f, R.string.v4_reset_layout, null, chev()) { host.resetLayout() }
        ))

        body.addView(u.text(12f, 500, u.pal.faint, act.getString(R.string.v4_version)), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START
        ).apply { leftMargin = u.i(L); bottomMargin = u.i(38f) })

        val saveBtn = TextView(u.ctx).apply {
            text = act.getString(R.string.v4_save)
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, u.px(15f))
            typeface = Fonts.w600(u.ctx)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = u.round(u.pal.accent, 14f)
            setOnClickListener { save() }
        }
        body.addView(saveBtn, FrameLayout.LayoutParams(u.i(180f), u.i(46f), Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = u.i(24f); bottomMargin = u.i(24f)
        })
    }

    private fun save() {
        draft.save(act)
        host.saved(before, draft)
    }

    private fun refreshRadios() {
        val w = Radios.wifiOn(act)
        wifiToggle?.on = w
        wifiValue?.setText(if (!w) R.string.v4_off_short else if (Radios.wifiConnected(act)) R.string.v4_connected else R.string.v4_on)
        val a = Radios.btAdapter()
        val b = Radios.btOn()
        btToggle?.on = b
        btValue?.setText(if (a == null) R.string.v4_unavailable else if (b) R.string.v4_on else R.string.v4_off_short)
    }

    private fun editName() {
        val input = EditText(act).apply {
            setText(draft.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(InputFilter.LengthFilter(18), InputFilter.AllCaps())
            isSingleLine = true
        }
        val pad = u.i(20f)
        val wrap = FrameLayout(act).apply { setPadding(pad, u.i(8f), pad, 0); addView(input) }
        AlertDialog.Builder(act)
            .setTitle(R.string.v4_name_title)
            .setView(wrap)
            .setPositiveButton(R.string.v4_done) { _, _ ->
                draft.name = input.text.toString().trim().ifEmpty { draft.name }
                build()
            }
            .setNegativeButton(R.string.v4_cancel, null)
            .show()
    }

    private fun chooseWidget() {
        val kinds = listOf(V4Prefs.K_SPEED, V4Prefs.K_APPS, V4Prefs.K_CLOCK, V4Prefs.K_LANG, V4Prefs.K_APPWIDGET)
        val names = arrayOf(
            act.getString(R.string.v4_speed), act.getString(R.string.v4_apps), act.getString(R.string.v4_clock),
            act.getString(R.string.v4_language), act.getString(R.string.v4_other_widget)
        )
        AlertDialog.Builder(act)
            .setTitle(R.string.v4_widget_title)
            .setItems(names) { _, i ->
                val k = kinds[i]
                if (k == V4Prefs.K_APPWIDGET) {
                    host.pickAppWidget { ok ->
                        if (ok) { draft.panel = V4Prefs.PANEL_WIDGET; draft.panelWidget = k; build() }
                    }
                } else {
                    draft.panel = V4Prefs.PANEL_WIDGET
                    draft.panelWidget = k
                    build()
                }
            }
            .setNegativeButton(R.string.v4_cancel, null)
            .show()
    }
}
