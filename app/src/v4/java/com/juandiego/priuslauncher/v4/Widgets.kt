package com.juandiego.priuslauncher.v4

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.format.DateFormat
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.juandiego.priuslauncher.AppsActivity
import com.juandiego.priuslauncher.DockSlots
import com.juandiego.priuslauncher.MapPanel
import com.juandiego.priuslauncher.Prefs
import com.juandiego.priuslauncher.R
import com.juandiego.priuslauncher.Weather
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- utilidades de posición (unidades del diseño) ----------

fun lpAt(u: U, x: Float, y: Float, w: Float, h: Float): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(if (w < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(w), if (h < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(h)).apply {
        gravity = Gravity.TOP or Gravity.START; leftMargin = u.i(x); topMargin = u.i(y)
    }

/** Ancho completo con márgenes izquierda/derecha, pegado abajo. */
fun lpBottom(u: U, side: Float, bottom: Float, h: Float): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (h < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(h)).apply {
        gravity = Gravity.BOTTOM; leftMargin = u.i(side); rightMargin = u.i(side); bottomMargin = u.i(bottom)
    }

fun lpTopFull(u: U, side: Float, top: Float, h: Float): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, if (h < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(h)).apply {
        gravity = Gravity.TOP; leftMargin = u.i(side); rightMargin = u.i(side); topMargin = u.i(top)
    }

fun lpTopRight(u: U, right: Float, top: Float, w: Float, h: Float): FrameLayout.LayoutParams =
    FrameLayout.LayoutParams(u.i(w), u.i(h)).apply { gravity = Gravity.TOP or Gravity.END; rightMargin = u.i(right); topMargin = u.i(top) }

fun startSafe(act: Activity, i: Intent?): Boolean {
    if (i == null) return false
    return try {
        act.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }
}

/** Dirección en 8 puntos (N, NE, E…) en el idioma elegido. */
fun cardinal(c: Context, bearing: Float): String {
    val ids = intArrayOf(R.string.v4_dir_n, R.string.v4_dir_ne, R.string.v4_dir_e, R.string.v4_dir_se, R.string.v4_dir_s, R.string.v4_dir_sw, R.string.v4_dir_w, R.string.v4_dir_nw)
    val i = (((bearing % 360f + 360f) % 360f + 22.5f) / 45f).toInt() % 8
    return c.getString(ids[i])
}

/** Color del puntito del clima según la condición. */
fun weatherDot(p: Pal, code: Int, day: Boolean): Int = when (code) {
    0, 1 -> if (day) p.yellow else p.sub
    in 51..67, in 80..82, in 95..99 -> p.accent
    else -> p.faint
}

// ---------- Mapa ----------

class MapCard(
    u: U, private val act: Activity, state: android.os.Bundle?,
    onTapMap: () -> Unit, onLong: () -> Unit
) : Card(u) {

    /** Google Maps (si la pantalla tiene Google Play y hay clave) o el mapa de respaldo OpenStreetMap. */
    val panel: MapPanel?
    private val osm: OsmMapView?
    private val pill = LinearLayout(u.ctx)
    private val pillText = u.text(13f, 600, u.pal.tx, u.ctx.getString(R.string.v4_nav))
    private val loc = LinearLayout(u.ctx)
    private val street = u.text(16f, 700, u.pal.tx, u.ctx.getString(R.string.v4_locating))
    private val city = u.text(12f, 500, u.pal.sub, "")
    private var compact = false
    private var streetName: String? = null
    private var cityLine: String = ""
    var located = false
        set(v) { if (field != v) { field = v; refreshText() } }

    private val go = LinearLayout(u.ctx)
    private val nav = LinearLayout(u.ctx)
    private val navIcon = ImageView(u.ctx)
    private val navTitle = u.text(18f, 700, u.pal.tx, "")
    private val navSub = u.text(13f, 600, u.pal.sub, "")
    private var navLine: String? = null

    init {
        val holder = FrameLayout(u.ctx)
        addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (MapPanel.googleAvailable(act)) {
            val mp = MapPanel(act, holder, false, !u.pal.dark, u.pal.accent, onTapMap, onLong)
            mp.create(state)
            panel = mp
            osm = null
        } else {
            panel = null
            osm = OsmMapView(u)
            holder.addView(osm, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.background = glassPill(u, 16f)
        pill.setPadding(u.i(12f), u.i(7f), u.i(12f), u.i(7f))
        pill.addView(u.icon(R.drawable.v4_ic_nav, u.pal.accent, 14f, 14f), LinearLayout.LayoutParams(u.i(14f), u.i(14f)))
        pill.addView(pillText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(8f) })
        addView(pill, lpAt(u, 12f, 12f, -1f, -1f))

        loc.orientation = LinearLayout.VERTICAL
        loc.background = glassPill(u, 14f)
        loc.setPadding(u.i(14f), u.i(11f), u.i(14f), u.i(11f))
        loc.addView(street)
        loc.addView(city, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(3f) })
        addView(loc, lpBottom(u, 12f, 66f, -1f))

        go.orientation = LinearLayout.HORIZONTAL
        addView(go, lpBottom(u, 12f, 12f, 44f))
        buildButtons()

        // navegando: siguiente vuelta (lo que dice la app de mapas en su notificación)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = glassPill(u, 16f)
        nav.setPadding(u.i(12f), u.i(12f), u.i(12f), u.i(12f))
        val box = FrameLayout(u.ctx).apply { background = u.round(u.pal.accent, 12f) }
        navIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        box.addView(navIcon, FrameLayout.LayoutParams(u.i(28f), u.i(28f), Gravity.CENTER))
        nav.addView(box, LinearLayout.LayoutParams(u.i(48f), u.i(48f)))
        val txt = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL }
        navTitle.isSingleLine = false
        navTitle.maxLines = 2
        txt.addView(navTitle)
        txt.addView(navSub, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(2f) })
        nav.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = u.i(12f) })
        nav.visibility = View.GONE
        addView(nav, lpTopFull(u, 12f, 12f, -1f))
    }

    /** Botones de abajo: la app elegida en Ajustes, o Waze y Google Maps. */
    fun buildButtons() {
        go.removeAllViews()
        val choice = V4Prefs.mapApp(act)
        if (choice.isEmpty()) {
            go.addView(goButton(u, act.getString(R.string.v4_waze), 0xFF33CCFF.toInt()) { openNav(V4Prefs.MAP_WAZE, "Waze") },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            go.addView(goButton(u, act.getString(R.string.v4_gmaps), 0xFF34A853.toInt()) { openNav(V4Prefs.MAP_GMAPS, "Google Maps") },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = u.i(8f) })
            return
        }
        val name = chosenName(choice)
        val color = when (choice) { V4Prefs.MAP_WAZE -> 0xFF33CCFF.toInt(); V4Prefs.MAP_GMAPS -> 0xFF34A853.toInt(); else -> u.pal.accent }
        go.addView(goButton(u, act.getString(R.string.v4_open_app, name), color) { openChosen() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
    }

    private fun chosenName(choice: String): String = when (choice) {
        V4Prefs.MAP_WAZE -> "Waze"
        V4Prefs.MAP_GMAPS -> "Google Maps"
        else -> DockSlots.labelFor(act, act.getSharedPreferences(DockSlots.PREFS, Context.MODE_PRIVATE), DockSlots.MAPS)
    }

    /** Abre la app de mapas elegida (o la primera que haya instalada). */
    fun openChosen() {
        val choice = V4Prefs.mapApp(act)
        when (choice) {
            "" -> if (!startSafe(act, act.packageManager.getLaunchIntentForPackage(V4Prefs.MAP_GMAPS)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) openNav(V4Prefs.MAP_WAZE, "Waze")
            V4Prefs.MAP_SLOT -> {
                val i = DockSlots.resolve(act, act.getSharedPreferences(DockSlots.PREFS, Context.MODE_PRIVATE), DockSlots.MAPS)
                if (!startSafe(act, i)) android.widget.Toast.makeText(act, R.string.v4_cant_open, android.widget.Toast.LENGTH_SHORT).show()
            }
            else -> openNav(choice, chosenName(choice))
        }
    }

    /** Navegación activa (o null): se muestra arriba del mapa sin abrir otra pantalla. */
    fun showNav(info: com.juandiego.priuslauncher.NavState.Info?) {
        if (info == null) {
            nav.visibility = View.GONE
            pill.visibility = View.VISIBLE
            navLine = null
            refreshText()
            return
        }
        val big = info.title.ifBlank { info.text }
        val small = if (info.title.isBlank()) "" else info.text
        navTitle.text = big
        navSub.text = small
        navSub.visibility = if (small.isBlank()) View.GONE else View.VISIBLE
        if (info.icon != null) {
            navIcon.setImageDrawable(info.icon.constantState?.newDrawable()?.mutate() ?: info.icon)
            navIcon.setColorFilter(android.graphics.Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN)
        } else {
            navIcon.setImageResource(R.drawable.v4_ic_nav)
            navIcon.setColorFilter(android.graphics.Color.WHITE)
        }
        navLine = info.sub.ifBlank { null }
        nav.visibility = View.VISIBLE
        pill.visibility = View.GONE
        refreshText()
    }

    private fun goButton(u: U, label: String, color: Int, click: () -> Unit): View = LinearLayout(u.ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = glassPill(u, 13f)
        addView(View(u.ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } },
            LinearLayout.LayoutParams(u.i(10f), u.i(10f)))
        addView(u.text(13f, 600, u.pal.tx, label),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(8f) })
        setOnClickListener { click() }
    }

    private fun openNav(pkg: String, name: String) {
        val i = act.packageManager.getLaunchIntentForPackage(pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startSafe(act, i)) android.widget.Toast.makeText(act, act.getString(R.string.v4_not_installed, name), android.widget.Toast.LENGTH_SHORT).show()
    }

    fun setLocation(lat: Double, lon: Double, bearing: Float?) {
        panel?.setLocation(lat, lon, bearing)
        osm?.setLocation(lat, lon, bearing)
    }

    fun setCompact(c: Boolean) {
        compact = c
        loc.visibility = if (c) View.GONE else View.VISIBLE
        refreshText()
    }

    fun setPlace(streetName: String?, cityLine: String) {
        this.streetName = streetName
        this.cityLine = cityLine
        refreshText()
    }

    private fun refreshText() {
        val st = streetName
        if (compact) {
            pillText.text = if (st != null) listOf(st, cityLine.substringBefore(" · ").substringBefore(",")).filter { it.isNotBlank() }.joinToString(" · ")
            else u.ctx.getString(R.string.v4_nav)
        } else {
            pillText.text = u.ctx.getString(R.string.v4_nav)
            street.text = st ?: u.ctx.getString(if (located) R.string.v4_here else R.string.v4_locating)
            val line = navLine ?: cityLine
            city.text = line
            city.visibility = if (line.isBlank()) View.GONE else View.VISIBLE
        }
    }
}

// ---------- Clima ----------

/** Sol del render: círculo y 8 rayos. */
class SunView(ctx: Context, private val color: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND }
    override fun onDraw(c: Canvas) {
        val k = width / 60f
        val cx = width / 2f
        val cy = height / 2f
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, 15f * k, p)
        p.strokeWidth = 3f * k
        for (a in 0 until 360 step 45) {
            val r = Math.toRadians(a.toDouble())
            c.drawLine(cx + 21f * k * cos(r).toFloat(), cy + 21f * k * sin(r).toFloat(), cx + 27f * k * cos(r).toFloat(), cy + 27f * k * sin(r).toFloat(), p)
        }
    }
}

class WeatherCard(u: U) : Card(u) {
    private val title = u.text(14f, 600, u.pal.tx, u.ctx.getString(R.string.v4_weather))
    private val iconBox = FrameLayout(u.ctx)
    private val city = u.text(12f, 500, u.pal.faint, "")
    private val temp = u.text(52f, 300, u.pal.tx, "--°")
    private val desc = u.text(14f, 600, u.pal.tx, "")
    private val hilo = u.text(12f, 500, u.pal.faint, "")
    private val hours = LinearLayout(u.ctx)
    private val daysBox = FrameLayout(u.ctx)
    private var tall = false

    init {
        addView(title, lpAt(u, 18f, 16f, -1f, -1f))
        addView(iconBox, lpTopRight(u, 14f, 44f, 60f, 60f))
        addView(city, lpAt(u, 18f, 46f, -1f, -1f))
        addView(temp, lpAt(u, 16f, 60f, -1f, -1f))
        val row = LinearLayout(u.ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        row.addView(desc)
        row.addView(hilo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(6f) })
        addView(row, lpAt(u, 18f, 126f, -1f, -1f))
        addView(View(u.ctx).apply { setBackgroundColor(u.pal.line) }, lpTopFull(u, 18f, 156f, 1f))
        hours.orientation = LinearLayout.HORIZONTAL
        addView(hours, lpTopFull(u, 12f, 168f, -1f))
        addView(daysBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setTall(t: Boolean) {
        tall = t
        refresh()
    }

    private fun hourLabel(h: Int): String =
        if (DateFormat.is24HourFormat(u.ctx)) String.format(Locale.US, "%02d:00", h)
        else "${if (h % 12 == 0) 12 else h % 12} ${if (h < 12) "AM" else "PM"}"

    fun refresh() {
        Weather.load(u.ctx)
        val n = Weather.now
        iconBox.removeAllViews()
        if (n == null) {
            temp.text = "--°"
            desc.text = ""
            hilo.text = ""
            city.text = ""
            hours.removeAllViews()
            daysBox.removeAllViews()
            return
        }
        if ((n.code == 0 || n.code == 1) && n.isDay) {
            iconBox.addView(SunView(u.ctx, u.pal.yellow), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val iv = ImageView(u.ctx).apply {
                setImageResource(Weather.iconRes(n))
                setColorFilter(if (n.code in 51..67 || n.code in 80..82 || n.code in 95..99) u.pal.accent else u.pal.sub)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            iconBox.addView(iv, FrameLayout.LayoutParams(u.i(40f), u.i(40f), Gravity.CENTER))
        }
        city.text = n.city ?: ""
        temp.text = Weather.degrees(u.ctx, n.tempC)
        desc.setText(Weather.descRes(n.code))
        val mx = n.maxC
        val mn = n.minC
        hilo.text = if (mx != null && mn != null) u.ctx.getString(R.string.v4_hi_lo, Weather.degrees(u.ctx, mx), Weather.degrees(u.ctx, mn)) else ""

        hours.removeAllViews()
        for (h in n.hours.take(4)) {
            val col = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(u.text(10f, 500, u.pal.faint, hourLabel(h.hour)))
            col.addView(View(u.ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(weatherDot(u.pal, h.code, h.isDay)) } },
                LinearLayout.LayoutParams(u.i(9f), u.i(9f)).apply { topMargin = u.i(6f); bottomMargin = u.i(6f) })
            col.addView(u.text(12f, 600, u.pal.tx, Weather.degrees(u.ctx, h.tempC)))
            hours.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        daysBox.removeAllViews()
        if (tall) {
            Forecast.load(u.ctx)
            val days = Forecast.days.take(5)
            if (days.isNotEmpty()) {
                daysBox.addView(View(u.ctx).apply { setBackgroundColor(u.pal.line) }, lpTopFull(u, 18f, 240f, 1f))
                daysBox.addView(u.text(12f, 600, u.pal.faint, u.ctx.getString(R.string.v4_next_days)), lpAt(u, 18f, 256f, -1f, -1f))
                val fmtIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val fmtOut = SimpleDateFormat("EEEE", Locale(Prefs.lang(u.ctx)))
                days.forEachIndexed { i, d ->
                    val name = try { fmtOut.format(fmtIn.parse(d.date) ?: Date()) } catch (e: Exception) { d.date }
                    val row = LinearLayout(u.ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(u.text(13f, 600, u.pal.tx, name.replaceFirstChar { it.uppercase() }), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    row.addView(View(u.ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(weatherDot(u.pal, d.code, true)) } },
                        LinearLayout.LayoutParams(u.i(9f), u.i(9f)).apply { rightMargin = u.i(20f) })
                    row.addView(u.text(13f, 600, u.pal.tx, Weather.degrees(u.ctx, d.maxC)).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(u.i(40f), ViewGroup.LayoutParams.WRAP_CONTENT))
                    row.addView(u.text(13f, 600, u.pal.faint, Weather.degrees(u.ctx, d.minC)).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(u.i(40f), ViewGroup.LayoutParams.WRAP_CONTENT))
                    daysBox.addView(row, lpTopFull(u, 18f, 284f + i * 42f, -1f))
                }
            }
        }
    }
}

// ---------- Sección intercambiable ----------

/** Velocímetro: arco de 270°, número grande y unidad (mph o km/h). */
class SpeedView(private val u: U) : View(u.ctx) {
    var mps = 0f
        set(v) { field = v; invalidate() }
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = u.pal.line }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = u.pal.accent }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.w200(u.ctx); color = u.pal.tx; textAlign = Paint.Align.CENTER }
    private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.w500(u.ctx); color = u.pal.sub; textAlign = Paint.Align.CENTER }
    private val gps = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.w500(u.ctx); color = u.pal.faint; textAlign = Paint.Align.CENTER }
    private val oval = RectF()

    override fun onDraw(c: Canvas) {
        val kmh = Prefs.useKmh(u.ctx)
        val v = if (kmh) mps * 3.6f else mps * 2.23694f
        val max = if (kmh) 200f else 120f
        val cx = width / 2f
        val cy = u.px(100f)
        val r = u.px(76f)
        track.strokeWidth = u.px(6f)
        bar.strokeWidth = u.px(6f)
        oval.set(cx - r, cy - r, cx + r, cy + r)
        // 0 abajo a la izquierda (135°) hasta el máximo abajo a la derecha (405°)
        c.drawArc(oval, 135f, 270f, false, track)
        val frac = (v / max).coerceIn(0f, 1f)
        if (frac > 0.003f) c.drawArc(oval, 135f, 270f * frac, false, bar)
        big.textSize = u.px(58f)
        unit.textSize = u.px(13f)
        gps.textSize = u.px(11f)
        c.drawText((if (v < 1f) 0 else v.roundToInt()).toString(), cx, u.px(112f), big)
        c.drawText(u.ctx.getString(if (kmh) R.string.unit_kmh_small else R.string.unit_mph_small), cx, u.px(136f), unit)
        c.drawText(u.ctx.getString(R.string.v4_gps), cx, u.px(182f), gps)
    }
}

class CompassView(private val u: U) : View(u.ctx) {
    var bearing = 0f
        set(v) { field = v; invalidate() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = u.pal.line }
    private val north = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF3B30.toInt() }
    private val south = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = u.pal.faint }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.w700(u.ctx); color = u.pal.sub; textAlign = Paint.Align.CENTER }
    private val path = Path()

    override fun onDraw(c: Canvas) {
        val k = width / 80f
        val cx = width / 2f
        val cy = height / 2f
        ring.strokeWidth = 2f * k
        c.drawCircle(cx, cy, 34f * k, ring)
        label.textSize = 9f * k
        c.drawText(u.ctx.getString(R.string.v4_dir_n), cx, 9f * k, label)
        c.save()
        c.rotate(bearing, cx, cy)
        path.reset(); path.moveTo(cx, cy - 28f * k); path.lineTo(cx + 7f * k, cy); path.lineTo(cx, cy - 4f * k); path.lineTo(cx - 7f * k, cy); path.close()
        c.drawPath(path, north)
        path.reset(); path.moveTo(cx, cy + 28f * k); path.lineTo(cx + 7f * k, cy); path.lineTo(cx, cy + 4f * k); path.lineTo(cx - 7f * k, cy); path.close()
        c.drawPath(path, south)
        c.restore()
    }
}

/**
 * Widget que cambia con ⇄ o deslizando: Velocidad, Apps, Reloj y brújula, Idioma.
 * [kind] fijo (para el panel central) o el guardado en V4Prefs.
 */
class SwapCard(
    u: U, private val act: Activity, private val fixedKind: String?,
    private val onLangChange: (String) -> Unit
) : Card(u) {

    private companion object {
        /** ZLink, Mapas, Música, Teléfono, YouTube, Radio (los botones de la versión aprobada). */
        val APP_SLOTS = intArrayOf(DockSlots.ZLINK, DockSlots.MAPS, DockSlots.MUSIC, DockSlots.PHONE, 2, 0)
    }

    private val content = FrameLayout(u.ctx)
    private val title = u.text(14f, 600, u.pal.tx, "")
    private val swapBtn = u.icon(R.drawable.v4_ic_swap, u.pal.faint, 16f, 16f)
    private val dots = LinearLayout(u.ctx)
    var kind: String = fixedKind ?: V4Prefs.swapKind(u.ctx)
        private set
    private var speed: SpeedView? = null
    private var compass: CompassView? = null
    private var clockText: TextView? = null
    private var dateText: TextView? = null
    private var headText: TextView? = null
    private var lastMps = 0f
    private var lastBearing = 0f
    private var offsetY = 0f
    var editing = false

    private val gestures = GestureDetector(u.ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (fixedKind != null || editing) return false
            if (abs(vx) > abs(vy) && abs(vx) > 600f) {
                step(if (vx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    init {
        addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(title, lpAt(u, 18f, 16f, -1f, -1f))
        if (fixedKind == null) {
            addView(swapBtn, lpTopRight(u, 16f, 16f, 16f, 16f))
            // zona táctil más grande que el ícono
            addView(View(u.ctx).apply { setOnClickListener { if (!editing) step(1) } }, lpTopRight(u, 4f, 4f, 40f, 40f))
            dots.orientation = LinearLayout.HORIZONTAL
            dots.gravity = Gravity.CENTER
            addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(5f), Gravity.BOTTOM).apply { bottomMargin = u.i(10f) })
        }
        build()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (fixedKind == null && !editing) gestures.onTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }

    private fun step(d: Int) {
        val list = V4Prefs.SWAP_KINDS
        val i = (list.indexOf(kind).coerceAtLeast(0) + d + list.size) % list.size
        kind = list[i]
        V4Prefs.setSwapKind(u.ctx, kind)
        build()
    }

    fun setTall(tall: Boolean) {
        // en un espacio alto el contenido se centra para que se vea alineado
        offsetY = if (tall) (512f - 254f) / 2f else 0f
        build()
    }

    private fun titleRes(k: String) = when (k) {
        V4Prefs.K_APPS -> R.string.v4_apps
        V4Prefs.K_CLOCK -> R.string.v4_clock
        V4Prefs.K_LANG -> R.string.v4_language
        else -> R.string.v4_speed
    }

    fun build() {
        content.removeAllViews()
        speed = null; compass = null; clockText = null; dateText = null; headText = null
        title.setText(titleRes(kind))
        val oy = offsetY
        when (kind) {
            V4Prefs.K_APPS -> buildApps(oy)
            V4Prefs.K_CLOCK -> buildClock(oy)
            V4Prefs.K_LANG -> buildLang(oy)
            else -> {
                val sv = SpeedView(u)
                sv.mps = lastMps
                content.addView(sv, lpTopFull(u, 0f, 40f + oy, 200f))
                speed = sv
            }
        }
        dots.removeAllViews()
        for (k in V4Prefs.SWAP_KINDS) {
            dots.addView(View(u.ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (k == kind) u.pal.sub else u.pal.line) }
            }, LinearLayout.LayoutParams(u.i(5f), u.i(5f)).apply { leftMargin = u.i(2.5f); rightMargin = u.i(2.5f) })
        }
        tick()
    }

    private fun buildApps(oy: Float) {
        val prefs = act.getSharedPreferences(DockSlots.PREFS, Context.MODE_PRIVATE)
        val grid = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL }
        for (r in 0 until 2) {
            val row = LinearLayout(u.ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 3) {
                val slot = APP_SLOTS[r * 3 + col]
                val intent = DockSlots.resolve(act, prefs, slot)
                val cell = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
                val iconView = ImageView(u.ctx)
                val icon: Drawable? = intent?.component?.let { cn -> try { act.packageManager.getActivityIcon(cn) } catch (e: Exception) { null } }
                    ?: intent?.`package`?.let { pkg -> try { act.packageManager.getApplicationIcon(pkg) } catch (e: Exception) { null } }
                if (icon != null) {
                    iconView.setImageDrawable(icon)
                    iconView.scaleType = ImageView.ScaleType.FIT_CENTER
                } else {
                    iconView.setImageResource(DockSlots.ALL[slot].iconRes)
                    iconView.setColorFilter(u.pal.sub)
                    iconView.background = u.round(u.pal.btn, 12f)
                    val pad = u.i(11f)
                    iconView.setPadding(pad, pad, pad, pad)
                }
                iconView.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, u.px(12f))
                }
                iconView.clipToOutline = true
                cell.addView(iconView, LinearLayout.LayoutParams(u.i(46f), u.i(46f)))
                cell.addView(u.text(10f, 500, u.pal.sub, appLabel(prefs, slot, intent)).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(5f) })
                cell.setOnClickListener {
                    if (editing) return@setOnClickListener
                    if (!startSafe(act, DockSlots.resolve(act, prefs, slot))) pick(slot)
                }
                cell.setOnLongClickListener { if (editing) false else { pick(slot); true } }
                row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = u.i(3f); rightMargin = u.i(3f)
                })
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = u.i(12f)
            })
        }
        content.addView(grid, lpTopFull(u, 14f, 52f + oy, -1f))
    }

    /** Nombre de la app instalada; si no hay, un nombre corto del botón. */
    private fun appLabel(prefs: android.content.SharedPreferences, slot: Int, intent: Intent?): String {
        if (prefs.contains(DockSlots.key(slot))) return DockSlots.labelFor(act, prefs, slot)
        val pkg = intent?.component?.packageName ?: intent?.`package`
        if (pkg != null) {
            try {
                return act.packageManager.getApplicationLabel(act.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                // sigue con el nombre del botón
            }
        }
        return when (slot) {
            DockSlots.PHONE -> act.getString(R.string.v4_phone)
            DockSlots.MUSIC -> act.getString(R.string.v4_music)
            else -> DockSlots.labelFor(act, prefs, slot)
        }
    }

    private fun pick(slot: Int) {
        startSafe(act, Intent(act, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK_SLOT, slot))
    }

    private fun buildClock(oy: Float) {
        val ct = u.text(58f, 200, u.pal.tx, "")
        val dt = u.text(14f, 500, u.pal.sub, "")
        val box = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL }
        box.addView(ct)
        box.addView(dt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(2f) })
        content.addView(box, lpAt(u, 18f, 56f + oy, -1f, -1f))
        val cv = CompassView(u)
        cv.bearing = lastBearing
        content.addView(cv, FrameLayout.LayoutParams(u.i(80f), u.i(80f), Gravity.TOP or Gravity.END).apply {
            rightMargin = u.i(16f); topMargin = u.i(254f - 18f - 80f + oy)
        })
        val ht = u.text(22f, 600, u.pal.tx, "")
        content.addView(ht, lpAt(u, 18f, 254f - 26f - 27f + oy, -1f, -1f))
        clockText = ct; dateText = dt; compass = cv; headText = ht
    }

    private fun buildLang(oy: Float) {
        val box = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL }
        val cur = Prefs.lang(u.ctx)
        for ((code, label) in listOf("es" to "Español", "en" to "English")) {
            val on = code == cur
            val opt = LinearLayout(u.ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(u.i(16f), 0, u.i(16f), 0)
                background = GradientDrawable().apply {
                    setColor(u.pal.btn); cornerRadius = u.px(13f)
                    if (on) setStroke(u.i(2f), u.pal.accent)
                }
                addView(u.text(15f, 600, u.pal.tx, label), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (on) addView(u.text(15f, 700, u.pal.accent, "✓"))
                setOnClickListener { if (!editing && !on) onLangChange(code) }
            }
            box.addView(opt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(48f)).apply { bottomMargin = u.i(10f) })
        }
        content.addView(box, lpTopFull(u, 18f, 62f + oy, -1f))
    }

    fun setSpeed(mps: Float) {
        lastMps = mps
        speed?.mps = mps
    }

    private var headingKnown = false

    /** known = el carro ya se movió (con el carro parado el GPS no da rumbo real). */
    fun setBearing(b: Float, known: Boolean) {
        lastBearing = b
        headingKnown = known
        compass?.bearing = b
        headText?.let { setHeading(it, b) }
    }

    private fun setHeading(t: TextView, b: Float) {
        if (!headingKnown) {
            t.text = "—"
            return
        }
        val sp = android.text.SpannableStringBuilder(cardinal(u.ctx, b))
        val start = sp.length
        sp.append(" · ${((b % 360f + 360f) % 360f).roundToInt()}°")
        sp.setSpan(android.text.style.AbsoluteSizeSpan(u.i(14f)), start, sp.length, 0)
        sp.setSpan(android.text.style.ForegroundColorSpan(u.pal.sub), start, sp.length, 0)
        t.text = sp
    }

    /** Se llama cada segundo (reloj). */
    fun tick() {
        val ct = clockText ?: return
        val now = Calendar.getInstance()
        val fmt = if (DateFormat.is24HourFormat(u.ctx)) "HH:mm" else "h:mm"
        ct.text = SimpleDateFormat(fmt, Locale.US).format(now.time)
        val loc = Locale(Prefs.lang(u.ctx))
        val pattern = if (Prefs.lang(u.ctx) == "en") "EEEE, MMMM d" else "EEEE, d 'de' MMMM"
        dateText?.text = SimpleDateFormat(pattern, loc).format(now.time).replaceFirstChar { it.uppercase() }
        headText?.let { setHeading(it, lastBearing) }
    }
}

/** Contorno punteado del modo editar (alrededor de cada tarjeta). */
class DashOutline(private val u: U, private val solid: Boolean = false) : View(u.ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = if (solid) u.pal.accent else alpha(u.pal.accent, 0.53f)
        strokeWidth = u.px(2f)
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(u.px(6f), u.px(4f)), 0f)
    }
    private val fill = Paint().apply { color = alpha(u.pal.accent, 0.08f) }
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val i = u.px(1f)
        r.set(i, i, width - i, height - i)
        if (solid) c.drawRoundRect(r, u.px(22f), u.px(22f), fill)
        c.drawRoundRect(r, u.px(24f), u.px(24f), p)
    }
}

fun transparent(): Int = Color.TRANSPARENT
