package com.cargo.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/**
 * Carretera HUD en perspectiva debajo del carro: cuadrícula, carriles, rayas y flechas.
 * Solo se anima cuando hay velocidad (OBD o GPS); parado no se redibuja.
 * La posición la fija MainActivity con [setAnchor] (el piso bajo la llanta delantera, en px).
 */
class RoadHudView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    // Direcciones sobre el piso, medidas en el dibujo del carro (hacia atrás y a lo ancho)
    private val ax = 0.966f; private val ay = -0.257f
    private val bx = 0.988f; private val by = 0.154f

    private var p0x = 0f; private var p0y = 0f; private var k = 1f
    private var dist = 0f
    private var mph = 0f
    private var lastFrame = 0L
    var running = false
        set(v) { field = v; lastFrame = 0L; invalidate() }

    private val grid = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lane = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val path = Path()
    private var useGlow = true

    fun setColors(p: Palette) {
        grid.color = p.roadGrid; lane.color = p.roadLane; glow.color = p.roadLane
        dash.color = p.roadDash; arrow.color = p.roadDash; useGlow = p.roadGlow
        invalidate()
    }

    /** [x],[y]: punto del piso en px de esta vista; [carWidthPx]: ancho del carro dibujado. */
    fun setAnchor(x: Float, y: Float, carWidthPx: Float) {
        p0x = x; p0y = y; k = carWidthPx / 735f
        grid.strokeWidth = maxOf(1f, 1.1f * k); lane.strokeWidth = 4f * k; glow.strokeWidth = 14f * k; dash.strokeWidth = 5f * k
        val fx = p0x + 21f * k; val fy = p0y + 22f * k; val rx = 600f * k; val ry = 260f * k
        fade.shader = RadialGradient(fx, fy, rx, intArrayOf(Color.BLACK, Color.BLACK, 0x60000000, Color.TRANSPARENT),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, fx, fy); postRotate(-8f, fx, fy) })
        }
        invalidate()
    }

    fun setSpeedMph(v: Float) {
        val was = mph > 0.5f
        mph = v.coerceIn(0f, 90f)
        if (!was && mph > 0.5f) { lastFrame = 0L; invalidate() }
    }

    private fun line(c: Canvas, px: Float, py: Float, dx: Float, dy: Float, a0: Float, a1: Float, p: Paint) =
        c.drawLine(px + dx * a0, py + dy * a0, px + dx * a1, py + dy * a1, p)

    override fun onDraw(c: Canvas) {
        if (k <= 0f || fade.shader == null) return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now
        if (running) dist += dt * mph * 6f * k

        val save = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        val far = 1600f * k; val cell = 40f * k
        for (j in -30..30) line(c, p0x + j * cell * bx, p0y + j * cell * by, ax, ay, -far, far, grid)
        val off = dist % cell
        for (j in -40..40) { val t = j * cell + off; line(c, p0x + t * ax, p0y + t * ay, bx, by, -far, far, grid) }
        for (lat in floatArrayOf(60f, -350f)) {
            val x = p0x + lat * k * bx; val y = p0y + lat * k * by
            if (useGlow) { glow.alpha = 0x40; line(c, x, y, ax, ay, -far, far, glow) }
            line(c, x, y, ax, ay, -far, far, lane)
        }
        val period = 120f * k; val ds = (dist * 1.6f) % period
        for (lat in floatArrayOf(190f, -560f)) for (j in -12..12) {
            val t = j * period + ds
            line(c, p0x + lat * k * bx + t * ax, p0y + lat * k * by + t * ay, ax, ay, 0f, 55f * k, dash)
        }
        val ap = 260f * k; val ao = (dist * 3.2f) % ap
        for (j in -4..4) {
            val t = j * ap + ao
            val cx = p0x + 330f * k * bx + t * ax; val cy = p0y + 330f * k * by + t * ay
            path.reset()
            path.moveTo(cx - 38f * k * ax, cy - 38f * k * ay)
            path.lineTo(cx + 20f * k * ax - 26f * k * bx, cy + 20f * k * ay - 26f * k * by)
            path.lineTo(cx, cy)
            path.lineTo(cx + 20f * k * ax + 26f * k * bx, cy + 20f * k * ay + 26f * k * by)
            path.close()
            c.drawPath(path, arrow)
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fade)
        c.restoreToCount(save)

        if (running && min(mph, 1f) > 0.5f) postInvalidateOnAnimation()
    }
}
