package com.juandiego.priuslauncher

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast

/**
 * Barra de abajo: atrás / inicio / multitarea / todas las apps,
 * tus accesos directos (con + para agregar), Wi-Fi, Bluetooth y la hora.
 */
class Dock(private val act: Activity, private val bar: LinearLayout, private val sport: Boolean, private val showClock: Boolean = !sport) {

    companion object {
        const val MAX_APPS = 8
        private val DEFAULTS = listOf(
            "com.spotify.music", "com.google.android.apps.maps", "com.waze",
            "com.google.android.youtube", "com.google.android.apps.youtube.music", "com.whatsapp"
        )

        /** Lista de apps de la barra (la primera vez se llena con las apps conocidas instaladas). */
        fun apps(c: Context): MutableList<String> {
            Prefs.dockApps(c)?.let { return it }
            val pm = c.packageManager
            val list = DEFAULTS.mapNotNull { pm.getLaunchIntentForPackage(it)?.component?.flattenToString() }.toMutableList()
            Prefs.setDockApps(c, list)
            return list
        }

        fun add(c: Context, cn: ComponentName): Boolean {
            val list = apps(c)
            val s = cn.flattenToString()
            if (list.contains(s)) return true
            if (list.size >= MAX_APPS) return false
            list.add(s)
            Prefs.setDockApps(c, list)
            return true
        }
    }

    private val d = act.resources.displayMetrics.density
    private var accent = Prefs.accent(act)
    private var wifiBtn: ImageButton? = null
    private var btBtn: ImageButton? = null
    private val tinted = mutableListOf<ImageView>()
    private val tintedText = mutableListOf<TextView>()
    private var started = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshToggles()
    }

    private fun px(v: Float) = (v * d).toInt()

    private fun iconColor() = if (sport) scaleColor(accent, 0.65f) else act.getColor(R.color.icon_tint)

    fun build() {
        bar.removeAllViews()
        tinted.clear()
        tintedText.clear()
        val btn = act.resources.getDimensionPixelSize(R.dimen.dock_btn)
        // Navegación
        fun nav(icon: Int, desc: Int, click: () -> Unit) {
            val b = ImageButton(act)
            b.setImageResource(icon)
            b.setBackgroundResource(R.drawable.chip_bg)
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.setPadding(px(10f), px(10f), px(10f), px(10f))
            b.contentDescription = act.getString(desc)
            b.setOnClickListener { click() }
            bar.addView(b, LinearLayout.LayoutParams(btn, btn))
            tinted.add(b)
        }
        nav(R.drawable.ic_back, R.string.prev) { NavService.instance?.back() ?: needService() }
        nav(R.drawable.ic_home, R.string.all_apps) { NavService.instance?.home() }
        nav(R.drawable.ic_recents, R.string.all_apps) { NavService.instance?.recents() ?: needService() }
        nav(R.drawable.ic_menu, R.string.all_apps) { act.startActivity(Intent(act, AppsActivity::class.java)) }

        bar.addView(Space(act), LinearLayout.LayoutParams(0, 1, 1f))

        // Accesos directos
        val icon = act.resources.getDimensionPixelSize(R.dimen.dock_icon)
        val list = apps(act)
        val pm = act.packageManager
        for ((i, s) in list.withIndex()) {
            val cn = ComponentName.unflattenFromString(s) ?: continue
            val dr = try { pm.getActivityIcon(cn) } catch (e: Exception) { null } ?: continue
            val iv = ImageView(act)
            iv.setImageDrawable(dr.mutate())
            if (sport) iv.colorFilter = monoFilter(accent)
            iv.setBackgroundResource(R.drawable.chip_bg)
            iv.setPadding(px(5f), px(5f), px(5f), px(5f))
            iv.contentDescription = try { pm.getActivityInfo(cn, 0).loadLabel(pm) } catch (e: Exception) { s }
            iv.setOnClickListener { launch(cn) }
            iv.setOnLongClickListener { edit(i); true }
            val lp = LinearLayout.LayoutParams(icon + px(10f), icon + px(10f))
            lp.marginStart = px(4f)
            lp.marginEnd = px(4f)
            bar.addView(iv, lp)
        }
        if (list.size < MAX_APPS) {
            val plus = ImageButton(act)
            plus.setImageResource(R.drawable.ic_plus)
            plus.background = GradientDrawable().apply {
                cornerRadius = 10 * d
                setStroke(px(1.5f), iconColor(), 6 * d, 4 * d)
            }
            plus.scaleType = ImageView.ScaleType.FIT_CENTER
            plus.setPadding(px(9f), px(9f), px(9f), px(9f))
            plus.contentDescription = act.getString(R.string.dock_add)
            plus.setOnClickListener {
                act.startActivity(Intent(act, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_ADD_DOCK, true))
            }
            val lp = LinearLayout.LayoutParams(icon, icon)
            lp.marginStart = px(6f)
            bar.addView(plus, lp)
            tinted.add(plus)
        }

        bar.addView(Space(act), LinearLayout.LayoutParams(0, 1, 1f))

        // Wi-Fi y Bluetooth
        fun toggle(iconRes: Int, desc: Int, click: () -> Unit, long: () -> Unit): ImageButton {
            val b = ImageButton(act)
            b.setImageResource(iconRes)
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.setPadding(px(9f), px(9f), px(9f), px(9f))
            b.contentDescription = act.getString(desc)
            b.setOnClickListener { click() }
            b.setOnLongClickListener { long(); true }
            val lp = LinearLayout.LayoutParams(btn + px(4f), btn - px(4f))
            lp.marginStart = px(6f)
            bar.addView(b, lp)
            return b
        }
        wifiBtn = toggle(R.drawable.ic_wifi, R.string.wifi, { toggleWifi() }, { open(Settings.ACTION_WIFI_SETTINGS) })
        btBtn = toggle(R.drawable.ic_bt, R.string.bluetooth, { toggleBt() }, { open(Settings.ACTION_BLUETOOTH_SETTINGS) })

        // Hora (en Sport ya está arriba)
        if (showClock) {
            val box = LinearLayout(act)
            box.orientation = LinearLayout.HORIZONTAL
            box.gravity = Gravity.CENTER_VERTICAL
            val clock = TextClock(act)
            clock.format12Hour = "h:mm"
            clock.format24Hour = "H:mm"
            clock.textSize = 20f
            clock.setTextColor(act.getColor(R.color.text_main))
            box.addView(clock)
            val date = TextClock(act)
            date.format12Hour = "EEE d MMM"
            date.format24Hour = "EEE d MMM"
            date.textSize = 13f
            date.setTextColor(act.getColor(R.color.text_muted))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginStart = px(10f)
            box.addView(date, lp)
            val blp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            blp.marginStart = px(16f)
            bar.addView(box, blp)
        }
        applyColors(accent)
    }

    fun applyColors(color: Int) {
        accent = color
        val ic = ColorStateList.valueOf(iconColor())
        for (v in tinted) v.imageTintList = ic
        if (sport) bar.setBackgroundColor(android.graphics.Color.BLACK)
        refreshToggles()
    }

    fun start() {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        act.registerReceiver(stateReceiver, f)
        refreshToggles()
    }

    fun stop() {
        if (!started) return
        started = false
        try { act.unregisterReceiver(stateReceiver) } catch (e: IllegalArgumentException) { }
    }

    private fun wifiOn(): Boolean = try {
        (act.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    } catch (e: Exception) { false }

    @Suppress("DEPRECATION")
    private fun btOn(): Boolean = try {
        BluetoothAdapter.getDefaultAdapter()?.isEnabled == true
    } catch (e: SecurityException) { false }

    private fun refreshToggles() {
        style(wifiBtn, wifiOn())
        style(btBtn, btOn())
    }

    private fun style(b: ImageButton?, on: Boolean) {
        b ?: return
        b.background = GradientDrawable().apply {
            cornerRadius = 12 * d
            if (on) setColor(if (sport) scaleColor(accent, 0.22f) else accent)
            else setColor(0x00000000)
            if (sport) setStroke(px(1f), scaleColor(accent, if (on) 0.9f else 0.3f))
        }
        val tint = when {
            sport -> if (on) accent else scaleColor(accent, 0.45f)
            on -> android.graphics.Color.WHITE
            else -> act.getColor(R.color.text_muted)
        }
        b.imageTintList = ColorStateList.valueOf(tint)
    }

    @Suppress("DEPRECATION")
    private fun toggleWifi() {
        val wm = act.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ok = try { wm.setWifiEnabled(!wm.isWifiEnabled) } catch (e: Exception) { false }
        if (!ok) open(Settings.ACTION_WIFI_SETTINGS)
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun toggleBt() {
        val a = BluetoothAdapter.getDefaultAdapter()
        val ok = try {
            if (a == null) false else if (a.isEnabled) a.disable() else a.enable()
        } catch (e: SecurityException) { false }
        if (!ok) open(Settings.ACTION_BLUETOOTH_SETTINGS)
    }

    private fun open(action: String) {
        try { act.startActivity(Intent(action)) } catch (e: Exception) {
            try { act.startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (e2: Exception) { }
        }
    }

    private fun launch(cn: ComponentName) {
        try {
            act.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(act, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun edit(i: Int) {
        val items = arrayOf(act.getString(R.string.dock_move_left), act.getString(R.string.dock_move_right), act.getString(R.string.dock_remove))
        val theme = if (Prefs.lightTheme(act)) android.R.style.Theme_Material_Light_Dialog_Alert else android.R.style.Theme_Material_Dialog_Alert
        AlertDialog.Builder(act, theme).setItems(items) { _, w ->
            val list = apps(act)
            if (i !in list.indices) return@setItems
            when (w) {
                0 -> if (i > 0) { val x = list.removeAt(i); list.add(i - 1, x) }
                1 -> if (i < list.size - 1) { val x = list.removeAt(i); list.add(i + 1, x) }
                2 -> list.removeAt(i)
            }
            Prefs.setDockApps(act, list)
            build()
        }.show()
    }

    private fun needService() {
        Toast.makeText(act, R.string.need_nav_service, Toast.LENGTH_LONG).show()
    }
}

fun scaleColor(c: Int, f: Float): Int {
    val r = (android.graphics.Color.red(c) * f).toInt().coerceIn(0, 255)
    val g = (android.graphics.Color.green(c) * f).toInt().coerceIn(0, 255)
    val b = (android.graphics.Color.blue(c) * f).toInt().coerceIn(0, 255)
    return android.graphics.Color.rgb(r, g, b)
}

/** Vuelve un ícono de un solo color (el del tema), para el estilo Sport. */
fun monoFilter(c: Int): ColorMatrixColorFilter {
    val r = android.graphics.Color.red(c) / 255f
    val g = android.graphics.Color.green(c) / 255f
    val b = android.graphics.Color.blue(c) / 255f
    val m = ColorMatrix(floatArrayOf(
        0.30f * r * 1.3f, 0.59f * r * 1.3f, 0.11f * r * 1.3f, 0f, 0f,
        0.30f * g * 1.3f, 0.59f * g * 1.3f, 0.11f * g * 1.3f, 0f, 0f,
        0.30f * b * 1.3f, 0.59f * b * 1.3f, 0.11f * b * 1.3f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))
    return ColorMatrixColorFilter(m)
}
