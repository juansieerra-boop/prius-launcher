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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.Toast

/**
 * Barra de abajo: Apps, Mapas, Música, Teléfono, tus apps (con + para agregar), Ajustes y el nombre del carro.
 * En Art cada botón lleva su nombre; en Sport solo los íconos, del color del tema.
 * También maneja los botones de Wi-Fi y Bluetooth de arriba.
 */
class Dock(
    private val act: Activity,
    private val bar: LinearLayout,
    private val sport: Boolean,
    private val wifiBtn: ImageButton?,
    private val btBtn: ImageButton?,
    private val actions: Actions
) : HomeDock {
    interface Actions {
        fun openMaps()
        fun openMusic()
        fun openPhone()
        fun pickSlot(slot: Int)
    }

    companion object {
        const val MAX_APPS = 6
        private val DEFAULTS = listOf("com.spotify.music", "com.waze", "com.google.android.youtube", "com.whatsapp")

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
    private val icons = mutableListOf<ImageView>()
    private val labels = mutableListOf<TextView>()
    private var brand: TextView? = null
    private var started = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshToggles()
    }

    init {
        wifiBtn?.setOnClickListener { toggleWifi() }
        wifiBtn?.setOnLongClickListener { open(Settings.ACTION_WIFI_SETTINGS); true }
        btBtn?.setOnClickListener { toggleBt() }
        btBtn?.setOnLongClickListener { open(Settings.ACTION_BLUETOOTH_SETTINGS); true }
    }

    private fun px(v: Float) = (v * d).toInt()

    private fun mainColor() = if (sport) accent else act.getColor(R.color.text_main)
    private fun softColor() = if (sport) scaleColor(accent, 0.62f) else act.getColor(R.color.text_muted)

    override fun build() {
        bar.removeAllViews()
        icons.clear()
        labels.clear()
        bar.setBackgroundColor(if (sport) android.graphics.Color.BLACK else act.getColor(R.color.dock_bg))
        val iconSize = act.resources.getDimensionPixelSize(R.dimen.d_icon)

        fun item(iconView: View, label: CharSequence?, tint: Boolean, click: () -> Unit, long: (() -> Unit)? = null) {
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.setBackgroundResource(R.drawable.chip_bg)
            box.isClickable = true
            box.setOnClickListener { click() }
            if (long != null) box.setOnLongClickListener { long(); true }
            box.contentDescription = label
            box.addView(iconView, LinearLayout.LayoutParams(iconSize, iconSize))
            if (iconView is ImageView && tint) icons.add(iconView)
            if (!sport && label != null) {
                val t = TextView(act)
                t.text = label
                t.textSize = 12f
                t.maxLines = 1
                t.ellipsize = android.text.TextUtils.TruncateAt.END
                t.gravity = Gravity.CENTER
                box.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(5f) })
                labels.add(t)
            }
            val w = if (sport) px(58f) else px(76f)
            bar.addView(box, LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.MATCH_PARENT).apply {
                topMargin = px(6f); bottomMargin = px(6f)
                if (sport) marginEnd = px(8f)
            })
        }

        fun vec(res: Int): ImageView = ImageView(act).apply {
            setImageResource(res)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        item(vec(R.drawable.ic_d_apps), act.getString(R.string.d_apps), true, {
            act.startActivity(Intent(act, AppsActivity::class.java))
        })
        item(vec(R.drawable.ic_d_maps), act.getString(R.string.d_maps), true, { actions.openMaps() }, { actions.pickSlot(DockSlots.MAPS) })
        item(vec(R.drawable.ic_d_music), act.getString(R.string.d_music), true, { actions.openMusic() }, { actions.pickSlot(DockSlots.MUSIC) })
        item(vec(R.drawable.ic_d_phone), act.getString(R.string.d_phone), true, { actions.openPhone() }, { actions.pickSlot(DockSlots.PHONE) })

        // Tus apps
        val list = apps(act)
        val pm = act.packageManager
        for ((i, s) in list.withIndex()) {
            val cn = ComponentName.unflattenFromString(s) ?: continue
            val dr = try { pm.getActivityIcon(cn) } catch (e: Exception) { null } ?: continue
            val iv = ImageView(act)
            iv.setImageDrawable(dr.mutate())
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            if (sport) iv.colorFilter = monoFilter(accent)
            val name = try { pm.getActivityInfo(cn, 0).loadLabel(pm) } catch (e: Exception) { s }
            item(iv, name, false, { launch(cn) }, { edit(i) })
        }
        if (list.size < MAX_APPS) {
            val plus = ImageView(act)
            plus.setImageResource(R.drawable.ic_plus)
            plus.setPadding(px(5f), px(5f), px(5f), px(5f))
            plus.background = GradientDrawable().apply {
                cornerRadius = 7 * d
                setStroke(px(1.3f), softColor(), 4 * d, 3 * d)
            }
            item(plus, if (sport) null else " ", true, {
                act.startActivity(Intent(act, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_ADD_DOCK, true))
            })
        }

        bar.addView(Space(act), LinearLayout.LayoutParams(0, 1, 1f))
        item(vec(R.drawable.ic_d_gear), act.getString(R.string.d_settings), true, {
            act.startActivity(Intent(act, SettingsActivity::class.java))
        })

        brand = null
        if (!sport) {
            val b = TextView(act)
            b.text = act.getString(R.string.brand_short)
            b.textSize = 24f
            b.letterSpacing = 0.14f
            b.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            bar.addView(b, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = px(18f) })
            brand = b
        }
        applyColors(accent)
    }

    override fun applyColors(color: Int) {
        accent = color
        val ic = ColorStateList.valueOf(if (sport) accent else act.getColor(R.color.icon_tint))
        for (v in icons) v.imageTintList = ic
        for (t in labels) t.setTextColor(act.getColor(R.color.text_main))
        brand?.setTextColor(act.getColor(R.color.text_main))
        refreshToggles()
    }

    override fun start() {
        if (started) return
        started = true
        val f = IntentFilter().apply {
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        act.registerReceiver(stateReceiver, f)
        refreshToggles()
    }

    override fun stop() {
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

    /** Encendido = color del tema; apagado = tenue. */
    private fun style(b: ImageButton?, on: Boolean) {
        b ?: return
        val off = if (sport) scaleColor(accent, 0.35f) else act.getColor(R.color.text_muted) and 0x00FFFFFF or 0x66000000
        b.imageTintList = ColorStateList.valueOf(if (on) accent else off)
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

/** La barra de apps de la pantalla de inicio (abajo en Sport, a la izquierda en Apple). */
interface HomeDock {
    fun build()
    fun applyColors(color: Int)
    fun start()
    fun stop()
}
