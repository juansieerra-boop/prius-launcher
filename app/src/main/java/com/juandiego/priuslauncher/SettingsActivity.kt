package com.juandiego.priuslauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Pantalla "Personalizar": estilo, color, modo, velocidad, idioma, animación, foto, barra y Android. */
class SettingsActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Themer.wrap(newBase))
    }

    private val d by lazy { resources.displayMetrics.density }
    private fun px(v: Float) = (v * d).toInt()
    private var accent = 0
    private var appliedKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appliedKey = key()
        build()
        hideSystemUi()
    }

    override fun onResume() {
        super.onResume()
        if (key() != appliedKey) recreate() else build()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun key() = "${Prefs.style(this)}|${Prefs.lightTheme(this)}|${Prefs.lang(this)}"

    /** Cambios que necesitan volver a dibujar con otro tema o idioma. */
    private fun change(block: () -> Unit) {
        block()
        if (key() != appliedKey) recreate() else build()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CarPhoto.REQ_PICK && resultCode == RESULT_OK && CarPhoto.handleResult(this, data)) {
            Toast.makeText(this, R.string.photo_changed, Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- UI ----------

    private val sport get() = Prefs.isSport(this)
    private fun fg() = if (sport) accent else getColor(R.color.text_main)
    private fun soft() = if (sport) scaleColor(accent, 0.62f) else getColor(R.color.text_muted)

    private fun build() {
        accent = Prefs.accent(this)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(if (sport) Color.BLACK else getColor(R.color.bg_dark))
        val page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(px(24f), px(16f), px(24f), px(24f))
        scroll.addView(page)

        // Encabezado
        val head = LinearLayout(this)
        head.gravity = Gravity.CENTER_VERTICAL
        val bar = View(this)
        bar.background = GradientDrawable().apply { cornerRadius = 3 * d; setColor(accent) }
        head.addView(bar, LinearLayout.LayoutParams(px(6f), px(28f)).apply { marginEnd = px(14f) })
        val title = TextView(this)
        title.setText(R.string.settings_title)
        title.textSize = 26f
        title.typeface = Typeface.create("sans-serif-black", Typeface.ITALIC)
        title.setTextColor(fg())
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val close = ImageButton(this)
        close.setImageResource(R.drawable.ic_close)
        close.imageTintList = android.content.res.ColorStateList.valueOf(fg())
        close.setBackgroundResource(R.drawable.chip_bg)
        close.setPadding(px(12f), px(12f), px(12f), px(12f))
        close.scaleType = ImageView.ScaleType.FIT_CENTER
        close.contentDescription = getString(R.string.close)
        close.setOnClickListener { finish() }
        head.addView(close, LinearLayout.LayoutParams(px(50f), px(50f)))
        page.addView(head)

        // Tres columnas
        val cols = LinearLayout(this)
        cols.orientation = LinearLayout.HORIZONTAL
        page.addView(cols, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(12f) })
        val c1 = column(cols, 1.15f, false)
        val c2 = column(cols, 1f, true)
        val c3 = column(cols, 1f, true)

        // Columna 1: estilo y color
        card(c1, R.string.style_section).apply {
            addView(choices(
                R.string.style_art to !sport, R.string.style_sport to sport
            ) { i -> change { Prefs.setStyle(this@SettingsActivity, if (i == 0) "art" else "sport") } })
        }
        card(c1, R.string.accent_section).apply {
            val row1 = LinearLayout(this@SettingsActivity)
            val row2 = LinearLayout(this@SettingsActivity)
            Prefs.PRESETS.forEachIndexed { i, color ->
                val v = View(this@SettingsActivity)
                v.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                    if (color == accent) setStroke(px(3f), if (sport) Color.WHITE else getColor(R.color.text_main))
                }
                v.setOnClickListener { Prefs.setAccent(this@SettingsActivity, color); build() }
                val lp = LinearLayout.LayoutParams(px(42f), px(42f))
                if (i % 5 != 0) lp.marginStart = px(10f)
                (if (i < 5) row1 else row2).addView(v, lp)
            }
            addView(row1, lpTop(12f)); addView(row2, lpTop(10f))
            addView(text(R.string.custom_color, 13f, soft()), lpTop(16f))
            val hue = HueBar(this@SettingsActivity)
            val hsv = FloatArray(3); Color.colorToHSV(accent, hsv); hue.hue = hsv[0]
            hue.onHueChanged = { h -> Prefs.setAccent(this@SettingsActivity, Color.HSVToColor(floatArrayOf(h, 0.85f, 1f))) }
            hue.setOnTouchListener { v, e ->
                if (e.action == android.view.MotionEvent.ACTION_UP) v.post { build() }
                false
            }
            addView(hue, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40f)))
        }

        // Columna 2: modo, velocidad, idioma, animación
        card(c2, R.string.theme_section).apply {
            val light = Prefs.lightSetting(this@SettingsActivity)
            addView(choices(R.string.theme_dark to !light, R.string.theme_light to light) { i ->
                change { Prefs.setLightTheme(this@SettingsActivity, i == 1) }
            })
            if (sport) addView(text(R.string.theme_sport_note, 11f, soft()), lpTop(8f))
        }
        card(c2, R.string.speed_section).apply {
            val kmh = Prefs.useKmh(this@SettingsActivity)
            addView(choices(R.string.mph to !kmh, R.string.kmh to kmh) { i -> Prefs.setKmh(this@SettingsActivity, i == 1); build() })
        }
        card(c2, R.string.lang_section).apply {
            val en = Prefs.lang(this@SettingsActivity) == "en"
            addView(choices(R.string.lang_es to !en, R.string.lang_en to en) { i ->
                change { Prefs.setLang(this@SettingsActivity, if (i == 0) "es" else "en") }
            })
        }
        card(c2, R.string.anim_section).apply {
            val on = Prefs.carAnimation(this@SettingsActivity)
            addView(choices(R.string.anim_on to on, R.string.anim_off to !on) { i -> Prefs.setCarAnimation(this@SettingsActivity, i == 0); build() })
        }

        // Columna 3: barra de navegación, Android, mapa, foto
        card(c3, R.string.nav_section).apply {
            val active = NavService.instance != null
            val st = text(if (active) R.string.nav_on else R.string.nav_off, 13f, if (active) getColor(R.color.gps_ok) else soft())
            addView(st, lpTop(8f))
            val shown = Prefs.navOverlay(this@SettingsActivity)
            addView(choices(R.string.nav_show to shown, R.string.nav_hide to !shown) { i -> Prefs.setNavOverlay(this@SettingsActivity, i == 0); build() })
            val bottom = Prefs.navBottom(this@SettingsActivity)
            addView(choices(R.string.nav_left to !bottom, R.string.nav_bottom to bottom) { i -> Prefs.setNavBottom(this@SettingsActivity, i == 1); build() })
            addView(button(R.string.nav_open_settings) { open(Settings.ACTION_ACCESSIBILITY_SETTINGS) }, lpTop(8f))
        }
        card(c3, R.string.android_section).apply {
            addView(button(R.string.android_settings) { open(Settings.ACTION_SETTINGS) }, lpTop(10f))
        }
        card(c3, R.string.maps_section).apply {
            addView(text(if (MapPanel.googleAvailable(this@SettingsActivity)) R.string.maps_status_google else R.string.maps_status_fallback, 13f, soft()), lpTop(8f))
        }
        if (!sport) card(c3, R.string.photo_section).apply {
            addView(button(R.string.pick_photo) {
                try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(CarPhoto.pickIntent(), CarPhoto.REQ_PICK)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(this@SettingsActivity, R.string.cant_open, Toast.LENGTH_SHORT).show()
                }
            }, lpTop(10f))
            addView(button(R.string.reset_photo) {
                CarPhoto.reset(this@SettingsActivity)
                Toast.makeText(this@SettingsActivity, R.string.photo_reset, Toast.LENGTH_SHORT).show()
            }, lpTop(8f))
        }

        setContentView(scroll)
    }

    private fun lpTop(dp: Float) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = px(dp) }

    private fun column(parent: LinearLayout, weight: Float, gap: Boolean): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        parent.addView(c, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply { if (gap) marginStart = px(12f) })
        return c
    }

    private fun cardBg(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 16 * d
        if (sport) {
            setColor(Color.BLACK)
            setStroke(px(1f), scaleColor(accent, 0.3f))
        } else {
            setColor(getColor(R.color.tile_top))
            setStroke(px(1f), getColor(R.color.stroke))
        }
    }

    private fun card(col: LinearLayout, label: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.background = cardBg()
        c.setPadding(px(16f), px(14f), px(16f), px(16f))
        val l = text(label, 11f, soft())
        l.letterSpacing = 0.22f
        l.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        c.addView(l)
        col.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            if (col.childCount > 0) topMargin = px(12f)
        })
        return c
    }

    private fun text(res: Int, size: Float, color: Int): TextView {
        val t = TextView(this)
        t.setText(res)
        t.textSize = size
        t.setTextColor(color)
        return t
    }

    private fun chip(res: Int, selected: Boolean): TextView {
        val t = TextView(this)
        t.setText(res)
        t.gravity = Gravity.CENTER
        t.textSize = 14f
        t.typeface = Typeface.DEFAULT_BOLD
        t.isClickable = true
        t.background = GradientDrawable().apply {
            cornerRadius = 14 * d
            if (selected) {
                setColor(if (sport) scaleColor(accent, 0.25f) else accent)
                if (sport) setStroke(px(1f), accent)
            } else {
                setColor(if (sport) Color.BLACK else getColor(R.color.tile_bottom))
                setStroke(px(1f), if (sport) scaleColor(accent, 0.3f) else getColor(R.color.stroke))
            }
        }
        t.setTextColor(
            when {
                sport -> if (selected) accent else scaleColor(accent, 0.62f)
                selected -> Color.WHITE
                else -> getColor(R.color.text_soft)
            }
        )
        return t
    }

    private fun choices(vararg opts: Pair<Int, Boolean>, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        opts.forEachIndexed { i, (res, sel) ->
            val c = chip(res, sel)
            c.setOnClickListener { if (!sel) onPick(i) }
            row.addView(c, LinearLayout.LayoutParams(0, px(44f), 1f).apply { if (i > 0) marginStart = px(8f) })
        }
        row.layoutParams = lpTop(10f)
        return row
    }

    private fun button(res: Int, click: () -> Unit): TextView {
        val b = chip(res, false)
        b.setOnClickListener { click() }
        b.minHeight = px(44f)
        b.setPadding(px(10f), px(10f), px(10f), px(10f))
        return b
    }

    private fun open(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }
}
