package com.juandiego.priuslauncher

import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Pantalla "Personalizar": color, unidades de velocidad y foto del carro. */
class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Themer.wrap(newBase))
    }

    private lateinit var hueBar: HueBar
    private lateinit var unitMph: TextView
    private lateinit var unitKmh: TextView
    private lateinit var titleAccent: View
    private val swatches = mutableListOf<Pair<View, Int>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        hideSystemUi()

        hueBar = findViewById(R.id.hueBar)
        unitMph = findViewById(R.id.unitMph)
        unitKmh = findViewById(R.id.unitKmh)
        titleAccent = findViewById(R.id.titleAccent)

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }

        buildSwatches()
        val hsv = FloatArray(3)
        Color.colorToHSV(Prefs.accent(this), hsv)
        hueBar.hue = hsv[0]
        hueBar.onHueChanged = { hue -> setAccent(Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))) }

        findViewById<View>(R.id.themeDark).setOnClickListener { setLight(false) }
        findViewById<View>(R.id.themeLight).setOnClickListener { setLight(true) }

        unitMph.setOnClickListener {
            Prefs.setKmh(this, false)
            refresh()
        }
        unitKmh.setOnClickListener {
            Prefs.setKmh(this, true)
            refresh()
        }

        findViewById<View>(R.id.btnPickPhoto).setOnClickListener {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(CarPhoto.pickIntent(), CarPhoto.REQ_PICK)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.btnResetPhoto).setOnClickListener {
            CarPhoto.reset(this)
            Toast.makeText(this, R.string.photo_reset, Toast.LENGTH_SHORT).show()
        }

        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CarPhoto.REQ_PICK && resultCode == RESULT_OK && CarPhoto.handleResult(this, data)) {
            Toast.makeText(this, R.string.photo_changed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun buildSwatches() {
        val row1 = findViewById<LinearLayout>(R.id.swatchRow1)
        val row2 = findViewById<LinearLayout>(R.id.swatchRow2)
        Prefs.PRESETS.forEachIndexed { i, color ->
            val v = View(this)
            val lp = LinearLayout.LayoutParams(dp(46f), dp(46f))
            if (i % 5 != 0) lp.marginStart = dp(12f)
            v.layoutParams = lp
            v.isClickable = true
            v.setOnClickListener {
                setAccent(color)
                val hsv = FloatArray(3)
                Color.colorToHSV(color, hsv)
                hueBar.hue = hsv[0]
            }
            (if (i < 5) row1 else row2).addView(v)
            swatches.add(v to color)
        }
    }

    private fun setLight(light: Boolean) {
        if (Prefs.lightTheme(this) == light) return
        Prefs.setLightTheme(this, light)
        recreate()
    }

    private fun setAccent(color: Int) {
        Prefs.setAccent(this, color)
        refresh()
    }

    private fun refresh() {
        val accent = Prefs.accent(this)
        for ((v, color) in swatches) {
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == accent) setStroke(dp(3f), getColor(R.color.text_main))
            }
        }
        titleAccent.backgroundTintList = ColorStateList.valueOf(accent)

        val kmh = Prefs.useKmh(this)
        styleChoice(unitMph, !kmh, accent)
        styleChoice(unitKmh, kmh, accent)
        val light = Prefs.lightTheme(this)
        styleChoice(findViewById(R.id.themeDark), !light, accent)
        styleChoice(findViewById(R.id.themeLight), light, accent)
    }

    private fun styleChoice(v: TextView, selected: Boolean, accent: Int) {
        if (selected) {
            v.background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(accent)
            }
            v.setTextColor(Color.WHITE)
        } else {
            v.setBackgroundResource(R.drawable.tile_bg)
            v.setTextColor(getColor(R.color.text_soft))
        }
    }
}
