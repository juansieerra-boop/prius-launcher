package com.juandiego.priuslauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Servicio de accesibilidad: hace funcionar atrás / inicio / multitarea
 * y muestra una barrita flotante minimalista encima de las demás apps.
 * En la pantalla de inicio se esconde (ahí ya están los botones en la barra de abajo).
 */
class NavService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    companion object {
        @Volatile
        var instance: NavService? = null
            private set
    }

    private var overlay: View? = null
    private var onHome = true

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Prefs.prefs(this).registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.android.systemui") return
        val home = pkg == packageName
        if (home != onHome) {
            onHome = home
            refresh()
        }
    }

    override fun onInterrupt() {}

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key in setOf("accent", "light", "style", "nav_on", "nav_bottom")) {
            removeOverlay()
            refresh()
        }
    }

    override fun onDestroy() {
        instance = null
        Prefs.prefs(this).unregisterOnSharedPreferenceChangeListener(this)
        removeOverlay()
        super.onDestroy()
    }

    fun back() { performGlobalAction(GLOBAL_ACTION_BACK) }
    fun home() { performGlobalAction(GLOBAL_ACTION_HOME) }
    fun recents() { performGlobalAction(GLOBAL_ACTION_RECENTS) }

    private fun refresh() {
        val show = Prefs.navOverlay(this) && !onHome
        if (!show) {
            overlay?.visibility = View.GONE
            return
        }
        if (overlay == null) addOverlay()
        overlay?.visibility = View.VISIBLE
    }

    private fun removeOverlay() {
        val v = overlay ?: return
        try { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } catch (e: Exception) { }
        overlay = null
    }

    private fun addOverlay() {
        val d = resources.displayMetrics.density
        val bottom = Prefs.navBottom(this)
        val accent = Prefs.accent(this)
        val sport = Prefs.isSport(this)
        val light = Prefs.lightTheme(this)
        val box = LinearLayout(this)
        box.orientation = if (bottom) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        box.gravity = Gravity.CENTER
        val pad = (6 * d).toInt()
        box.setPadding(pad, pad, pad, pad)
        box.background = GradientDrawable().apply {
            cornerRadius = 24 * d
            setColor(if (light) 0xEBFFFFFF.toInt() else 0xE0111316.toInt())
            setStroke((1 * d).toInt(), if (sport) scaleColor(accent, 0.4f) else if (light) 0x1F000000 else 0x1AFFFFFF)
        }
        val tint = ColorStateList.valueOf(if (sport) accent else if (light) 0xFF2A2D33.toInt() else 0xFFE6E8EB.toInt())
        val size = (42 * d).toInt()
        fun btn(icon: Int, click: () -> Unit) {
            val b = ImageButton(this)
            b.setImageResource(icon)
            b.imageTintList = tint
            b.setBackgroundResource(R.drawable.chip_bg)
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            val p = (10 * d).toInt()
            b.setPadding(p, p, p, p)
            b.setOnClickListener { click() }
            box.addView(b, LinearLayout.LayoutParams(size, size))
        }
        btn(R.drawable.ic_back) { back() }
        btn(R.drawable.ic_home) { home() }
        btn(R.drawable.ic_recents) { recents() }
        btn(R.drawable.ic_menu) {
            startActivity(Intent(this, AppsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = if (bottom) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.START or Gravity.CENTER_VERTICAL
        lp.x = (6 * d).toInt()
        lp.y = (6 * d).toInt()
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(box, lp)
            overlay = box
        } catch (e: Exception) {
            overlay = null
        }
    }
}
