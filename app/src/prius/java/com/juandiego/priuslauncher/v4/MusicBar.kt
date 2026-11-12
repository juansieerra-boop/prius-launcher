package com.juandiego.priuslauncher.v4

import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.juandiego.priuslauncher.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Línea de progreso de 3 px (fondo + avance). */
class ProgressBar3(private val u: U) : View(u.ctx) {
    var level = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = u.pal.line }
    private val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = u.pal.tx }
    override fun onDraw(c: Canvas) {
        val r = height / 2f
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, bg)
        if (level > 0f) c.drawRoundRect(0f, 0f, width * level, height.toFloat(), r, r, fg)
    }
}

/**
 * Barra fija de arriba: apps · carátula · canción · ⏮ ⏯ ⏭ · progreso · ⚙ · hora y estado.
 * Las posiciones son las del render (barra de 1047 de ancho), repartidas según el ancho real.
 */
class MusicBar(
    u: U, barW: Float,
    onApps: () -> Unit, onSettings: () -> Unit,
    private val media: () -> MediaHub?
) : Card(u) {

    private val art = ImageView(u.ctx)
    private val artNote = u.icon(R.drawable.v4_ic_note, u.pal.sub, 20f, 20f)
    private val title = u.text(15f, 600, u.pal.tx, "")
    private val artist = u.text(12f, 500, u.pal.sub, "")
    private val play = u.icon(R.drawable.v4_ic_play, u.pal.tx, 18f, 20f)
    private val progress = ProgressBar3(u)
    private val tPos = u.text(10f, 500, u.pal.faint, "")
    private val tLeft = u.text(10f, 500, u.pal.faint, "")
    private val clock = u.text(17f, 600, u.pal.tx, "")
    private val status = u.text(10f, 600, u.pal.sub, "")
    private val gpsDot = View(u.ctx)
    private var playing = false

    init {
        radius = 18f
        // apps
        val apps = u.icon(R.drawable.v4_ic_apps, u.pal.sub, 20f, 20f)
        addView(apps, lpAt(u, 18f, 19f, 20f, 20f))
        addView(View(u.ctx).apply { setOnClickListener { onApps() } }, lpAt(u, 6f, 6f, 44f, 46f))
        addView(View(u.ctx).apply { setBackgroundColor(u.pal.line) }, lpAt(u, 44f, 14f, 1f, 30f))

        // carátula
        val artBox = FrameLayout(u.ctx).apply {
            background = u.round(u.pal.btn, 9f)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, u.px(9f))
            }
            clipToOutline = true
        }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        artBox.addView(artNote, FrameLayout.LayoutParams(u.i(20f), u.i(20f), Gravity.CENTER))
        artBox.addView(art, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(artBox, lpAt(u, 58f, 9f, 40f, 40f))

        val ctlX = 453f * barW / 1047f
        val prX = 611f * barW / 1047f
        val gearX = barW - 120f
        val textW = ctlX - 110f - 24f
        addView(title, lpAt(u, 110f, 12f, textW, -1f))
        addView(artist, lpAt(u, 110f, 32f, textW, -1f))
        // tocar la canción abre la app de música
        addView(View(u.ctx).apply { setOnClickListener { media()?.openApp() } }, lpAt(u, 52f, 4f, ctlX - 60f, 50f))

        // ⏮ ⏯ ⏭ (separación 38 como en el diseño) con zonas táctiles grandes
        val prev = u.icon(R.drawable.v4_ic_prev, u.pal.tx, 22f, 16f)
        val next = u.icon(R.drawable.v4_ic_next, u.pal.tx, 22f, 16f)
        addView(prev, lpAt(u, ctlX, 21f, 22f, 16f))
        addView(play, lpAt(u, ctlX + 22f + 38f, 19f, 18f, 20f))
        addView(next, lpAt(u, ctlX + 22f + 38f + 18f + 38f, 21f, 22f, 16f))
        addView(View(u.ctx).apply { setOnClickListener { media()?.prev() } }, lpAt(u, ctlX - 14f, 4f, 50f, 50f))
        addView(View(u.ctx).apply { setOnClickListener { media()?.playPause() } }, lpAt(u, ctlX + 22f + 38f - 16f, 4f, 50f, 50f))
        addView(View(u.ctx).apply { setOnClickListener { media()?.next() } }, lpAt(u, ctlX + 22f + 38f + 18f + 38f - 14f, 4f, 50f, 50f))

        // progreso
        val prW = gearX - 26f - prX
        addView(progress, lpAt(u, prX, 28f, prW, 3f))
        addView(tPos, lpAt(u, prX, 36f, -1f, -1f))
        tLeft.gravity = Gravity.END
        addView(tLeft, lpAt(u, prX, 36f, prW, -1f))

        // ⚙ Ajustes
        val gear = FrameLayout(u.ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(u.pal.btn) }
            addView(u.icon(R.drawable.v4_ic_gear, u.pal.sub, 17f, 17f), FrameLayout.LayoutParams(u.i(17f), u.i(17f), Gravity.CENTER))
            setOnClickListener { onSettings() }
        }
        addView(gear, lpAt(u, gearX, 13f, 32f, 32f))

        // hora y estado
        val st = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        clock.gravity = Gravity.END
        st.addView(clock)
        val stRow = LinearLayout(u.ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status.letterSpacing = 0.03f
        stRow.addView(status)
        gpsDot.background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(u.pal.faint) }
        stRow.addView(gpsDot, LinearLayout.LayoutParams(u.i(6f), u.i(6f)).apply { leftMargin = u.i(4f) })
        st.addView(stRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(2f) })
        addView(st, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = u.i(18f); topMargin = u.i(13f)
        })
        tick()
    }

    private fun fmt(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return "${s / 60}:${String.format(Locale.US, "%02d", s % 60)}"
    }

    fun setStatus(wifi: Boolean, gpsColor: Int) {
        status.text = listOfNotNull(if (wifi) u.ctx.getString(R.string.v4_wifi_short) else null, u.ctx.getString(R.string.v4_gps)).joinToString(" · ")
        (gpsDot.background as? android.graphics.drawable.GradientDrawable)?.setColor(gpsColor)
    }

    /** Actualiza canción, carátula, botón y progreso. */
    fun showMedia(n: MediaHub.Now?, source: String?) {
        if (n == null || n.title == null) {
            val access = n?.hasAccess ?: false
            title.setText(if (access) R.string.v4_no_music else R.string.v4_no_access)
            artist.text = if (access) "" else u.ctx.getString(R.string.v4_no_access_sub)
            art.setImageDrawable(null)
            artNote.visibility = View.VISIBLE
            setPlaying(n?.playing ?: false)
            progress.level = 0f
            tPos.text = ""
            tLeft.text = ""
            return
        }
        title.text = n.title
        artist.text = listOfNotNull(source, n.artist).filter { it.isNotBlank() }.distinct().joinToString(" · ")
        if (n.art != null) {
            art.setImageBitmap(n.art)
            artNote.visibility = View.GONE
        } else {
            art.setImageDrawable(null)
            artNote.visibility = View.VISIBLE
        }
        setPlaying(n.playing)
        if (n.durationMs > 0) {
            progress.level = n.positionMs.toFloat() / n.durationMs
            tPos.text = fmt(n.positionMs)
            tLeft.text = "-" + fmt(n.durationMs - n.positionMs)
        } else {
            progress.level = 0f
            tPos.text = ""
            tLeft.text = ""
        }
    }

    private fun setPlaying(p: Boolean) {
        if (p == playing && play.drawable != null) return
        playing = p
        play.setImageResource(if (p) R.drawable.v4_ic_pause else R.drawable.v4_ic_play)
        play.setColorFilter(u.pal.tx)
    }

    fun tick() {
        val f = if (DateFormat.is24HourFormat(u.ctx)) "HH:mm" else "h:mm"
        val t = SimpleDateFormat(f, Locale.US).format(Date())
        if (clock.text.toString() != t) clock.text = t
    }
}
