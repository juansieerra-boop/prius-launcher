package com.cargo.launcher.viewport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Haces de luz cónicos de los faros sobre el asfalto + destello de faros, niebla y calaveras.
 * Las posiciones llegan en px de esta vista desde VehicleViewport (ya convertidas desde el dibujo
 * del auto), así que siempre coinciden con los faros aunque cambie el tamaño de la pantalla.
 * La visibilidad / alpha la anima VehicleViewport (solo modo noche). Se usan dos instancias:
 * una con los conos debajo del auto y otra con los destellos encima.
 */
class HeadlightBeamView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    class Lamp(val x: Float, val y: Float, val r: Float)

    /** CONES: conos sobre el asfalto (capa debajo del auto). FLARES: destello de los focos (encima). */
    enum class Layer { CONES, FLARES }
    var layer = Layer.CONES
        set(v) { field = v; invalidate() }

    private var heads: List<Lamp> = emptyList()
    private var fogs: List<Lamp> = emptyList()
    private var tails: List<Lamp> = emptyList()
    private var dirX = -1f; private var dirY = 0.4f   // hacia dónde alumbran (unitario)
    private var reach = 300f

    private val beam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    fun setLamps(heads: List<Lamp>, fogs: List<Lamp>, tails: List<Lamp>, dirX: Float, dirY: Float, reach: Float) {
        val n = hypot(dirX, dirY).takeIf { it > 0f } ?: 1f
        this.heads = heads; this.fogs = fogs; this.tails = tails
        this.dirX = dirX / n; this.dirY = dirY / n; this.reach = reach
        invalidate()
    }

    private fun cone(c: Canvas, l: Lamp, length: Float, spreadDeg: Float, alpha: Int) {
        val a = Math.toRadians(spreadDeg.toDouble())
        val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        // dos rayos girados ±spread respecto a la dirección del haz
        val lx = dirX * ca - dirY * sa; val ly = dirX * sa + dirY * ca
        val rx = dirX * ca + dirY * sa; val ry = -dirX * sa + dirY * ca
        path.reset()
        path.moveTo(l.x - dirY * l.r * 0.6f, l.y + dirX * l.r * 0.6f)
        path.lineTo(l.x + lx * length, l.y + ly * length)
        path.quadTo(l.x + dirX * length * 1.12f, l.y + dirY * length * 1.12f, l.x + rx * length, l.y + ry * length)
        path.lineTo(l.x + dirY * l.r * 0.6f, l.y - dirX * l.r * 0.6f)
        path.close()
        beam.shader = LinearGradient(l.x, l.y, l.x + dirX * length, l.y + dirY * length,
            intArrayOf(Color.argb(alpha, 235, 244, 255), Color.argb(alpha / 3, 210, 230, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, beam)
    }

    private fun flare(c: Canvas, l: Lamp, scale: Float, color: Int) {
        val r = l.r * scale
        dot.shader = RadialGradient(l.x, l.y, r, intArrayOf(color, Color.argb(Color.alpha(color) / 3, Color.red(color), Color.green(color), Color.blue(color)), Color.TRANSPARENT),
            floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(l.x, l.y, r, dot)
    }

    override fun onDraw(c: Canvas) {
        if (layer == Layer.CONES) {
            heads.forEach { cone(c, it, reach, 16f, 95) }
            fogs.forEach { cone(c, it, reach * 0.55f, 24f, 60) }
            return
        }
        heads.forEach { flare(c, it, 3.2f, Color.argb(235, 255, 255, 255)) }
        fogs.forEach { flare(c, it, 3f, Color.argb(200, 255, 250, 235)) }
        tails.forEach { flare(c, it, 4f, Color.argb(200, 255, 40, 50)) }
    }
}
