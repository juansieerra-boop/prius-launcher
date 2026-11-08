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
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.wifi.WifiManager
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Barra lateral del diseño estilo Apple: íconos redondeados con su nombre abajo.
 *  ZLink · Mapas · Música · Teléfono · tus apps · (+)
 * Mantén presionado ZLink, Mapas, Música o Teléfono para elegir qué app abre.
 * Mantén presionada una de tus apps para moverla o quitarla.
 */
class SideDock(
    private val act: Activity,
    private val list: LinearLayout,
    private val wifiBtn: ImageButton?,
    private val btBtn: ImageButton?,
    private val actions: Dock.Actions
) : HomeDock {

    private val d = act.resources.displayMetrics.density
    private var accent = Prefs.accent(act)
    private var started = false
    private val prefs = act.getSharedPreferences(DockSlots.PREFS, Context.MODE_PRIVATE)

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

    override fun build() {
        list.removeAllViews()
        val size = act.resources.getDimensionPixelSize(R.dimen.cp_icon)

        fun squircle(v: View) {
            v.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, view.width * 0.24f)
                }
            }
            v.clipToOutline = true
            v.elevation = 3 * d
        }

        fun item(icon: View, label: CharSequence, click: () -> Unit, long: () -> Unit) {
            val box = LinearLayout(act)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER_HORIZONTAL
            box.setPadding(0, px(4f), 0, px(4f))
            box.isClickable = true
            box.setBackgroundResource(R.drawable.chip_bg)
            box.contentDescription = label
            box.setOnClickListener { click() }
            box.setOnLongClickListener { long(); true }
            squircle(icon)
            box.addView(icon, LinearLayout.LayoutParams(size, size))
            val t = TextView(act)
            t.text = label
            t.textSize = 10f
            t.maxLines = 1
            t.ellipsize = TextUtils.TruncateAt.END
            t.gravity = Gravity.CENTER
            t.setTextColor(act.getColor(R.color.cp_muted))
            box.addView(t, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = px(3f) })
            list.addView(box, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = px(4f)
                leftMargin = px(4f); rightMargin = px(4f)
            })
        }

        /** Ícono estilo iPhone: cuadro con degradado y el dibujo en blanco. */
        fun glyph(res: Int, top: Int, bottom: Int): ImageView = ImageView(act).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom))
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            val p = (size * 0.22f).toInt()
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        /** Ícono real de la app (ej. ZLink), si está instalada. */
        fun appIcon(slot: Int): Drawable? {
            val i = DockSlots.resolve(act, prefs, slot) ?: return null
            return try {
                act.packageManager.getActivityIcon(i)
            } catch (e: Exception) {
                null
            }
        }

        fun real(dr: Drawable): ImageView = ImageView(act).apply {
            setImageDrawable(dr)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.WHITE)
        }

        // ZLink
        val z = appIcon(DockSlots.ZLINK)
        item(
            if (z != null) real(z) else glyph(R.drawable.ic_g_zlink, 0xFF5BE36C.toInt(), 0xFF1FAE3B.toInt()),
            act.getString(R.string.d_zlink),
            { openSlot(DockSlots.ZLINK) },
            { actions.pickSlot(DockSlots.ZLINK) }
        )
        item(glyph(R.drawable.ic_g_nav, 0xFF6FD0FF.toInt(), 0xFF2B7BFF.toInt()), act.getString(R.string.d_maps),
            { actions.openMaps() }, { actions.pickSlot(DockSlots.MAPS) })
        item(glyph(R.drawable.ic_g_music, 0xFFFF6A7E.toInt(), 0xFFF0263F.toInt()), act.getString(R.string.d_music),
            { actions.openMusic() }, { actions.pickSlot(DockSlots.MUSIC) })
        item(glyph(R.drawable.ic_g_phone, 0xFF7CF08A.toInt(), 0xFF29C24A.toInt()), act.getString(R.string.d_phone),
            { actions.openPhone() }, { actions.pickSlot(DockSlots.PHONE) })

        // Tus apps
        val apps = Dock.apps(act)
        val pm = act.packageManager
        for ((i, s) in apps.withIndex()) {
            val cn = ComponentName.unflattenFromString(s) ?: continue
            val dr = try { pm.getActivityIcon(cn) } catch (e: Exception) { null } ?: continue
            val name = try { pm.getActivityInfo(cn, 0).loadLabel(pm) } catch (e: Exception) { s }
            item(real(dr), name, { launch(cn) }, { edit(i) })
        }
        if (apps.size < Dock.MAX_APPS) {
            val plus = ImageView(act).apply {
                setImageResource(R.drawable.ic_plus)
                imageTintList = ColorStateList.valueOf(act.getColor(R.color.cp_muted))
                val p = (size * 0.28f).toInt()
                setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    cornerRadius = size * 0.24f
                    setStroke(px(1.3f), act.getColor(R.color.cp_muted), 4 * d, 3 * d)
                }
            }
            item(plus, " ", {
                act.startActivity(Intent(act, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_ADD_DOCK, true))
            }, {})
            (plus.parent as? View)?.let { it.elevation = 0f }
            plus.elevation = 0f
        }
        applyColors(accent)
    }

    private fun openSlot(slot: Int) {
        val i = DockSlots.resolve(act, prefs, slot)
        if (i == null) {
            Toast.makeText(act, R.string.hint_first, Toast.LENGTH_LONG).show()
            actions.pickSlot(slot)
            return
        }
        try {
            act.startActivity(i)
        } catch (e: Exception) {
            actions.pickSlot(slot)
        }
    }

    override fun applyColors(color: Int) {
        accent = color
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
        val off = (act.getColor(R.color.cp_muted) and 0x00FFFFFF) or 0x66000000
        wifiBtn?.imageTintList = ColorStateList.valueOf(if (wifiOn()) act.getColor(R.color.cp_text) else off)
        btBtn?.imageTintList = ColorStateList.valueOf(if (btOn()) act.getColor(R.color.cp_text) else off)
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
            val l = Dock.apps(act)
            if (i !in l.indices) return@setItems
            when (w) {
                0 -> if (i > 0) { val x = l.removeAt(i); l.add(i - 1, x) }
                1 -> if (i < l.size - 1) { val x = l.removeAt(i); l.add(i + 1, x) }
                2 -> l.removeAt(i)
            }
            Prefs.setDockApps(act, l)
            build()
        }.show()
    }
}
