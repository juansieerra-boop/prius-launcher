package com.cargo.launcher.viewport

import android.animation.ArgbEvaluator
import android.animation.TimeAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Carretera en perspectiva (estilo HUD / cuadro de instrumentos).
 *
 * Geometría: todo sale de un punto de fuga (vp) y llega a un borde cercano centrado en (near) con
 * medio ancho [nearHalfWidth]. Un punto de la carretera es  P(t,u) = vp + t·(near + u·halfW − vp),
 * con t = 1/z (z = distancia "real", 1 = cerca) y u ∈ [-1, 1] la posición lateral.
 * Así las rayas se acortan y se juntan hacia el horizonte de forma correcta.
 *
 * Las rayas avanzan proporcional a la velocidad del OBD2 con un [TimeAnimator]; parado no se redibuja.
 */
class PerspectiveRoadView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    // ---------------------------------------------------------------- geometría (px de esta vista)
    private var vpX = 0f; private var vpY = 0f
    private var nearX = 0f; private var nearY = 0f
    private var halfW = 0f

    fun setGeometry(vpX: Float, vpY: Float, nearX: Float, nearY: Float, nearHalfWidth: Float) {
        this.vpX = vpX; this.vpY = vpY; this.nearX = nearX; this.nearY = nearY; halfW = nearHalfWidth
        shaderDirty = true
        invalidate()
    }

    // ---------------------------------------------------------------- colores día ↔ noche
    private val argb = ArgbEvaluator()
    private var night = 0f            // 0 = día, 1 = noche (se anima desde VehicleViewport)
    private var shaderDirty = true

    private val dayFar = 0xFFB9AEA8.toInt();   private val nightFar = 0xFF1B2133.toInt()
    private val dayNear = 0xFF5F5A58.toInt();  private val nightNear = 0xFF0B0E16.toInt()
    private val dayLine = 0xFFFFFFFF.toInt();  private val nightLine = 0xFFBFEFFF.toInt()
    private var edgeAccentDay = 0xFFFFC36B.toInt()
    private var edgeAccentNight = 0xFF22D3EE.toInt()

    /** Color neón de los bordes del carril (tinte del HUD). */
    fun setAccent(day: Int, night: Int) { edgeAccentDay = day; edgeAccentNight = night; invalidate() }

    fun setNightFraction(f: Float) {
        night = f.coerceIn(0f, 1f)
        shaderDirty = true
        invalidate()
    }

    private fun mix(a: Int, b: Int) = argb.evaluate(night, a, b) as Int

    // ---------------------------------------------------------------- movimiento
    private var mph = 0f
    private var phase = 0f            // en unidades de "z"
    var running = true
        set(v) { field = v; updateTicker() }

    private val ticker = TimeAnimator().apply {
        setTimeListener { _, _, deltaMs ->
            // 0.12 unidades z por mph por segundo: a 35 mph pasa ~1 raya por segundo, como en carretera
            phase += mph * 0.12f * (deltaMs.coerceAtMost(100L) / 1000f)
            if (phase > 1000f) phase -= 1000f
            invalidate()
        }
    }

    fun setSpeedMph(v: Float) {
        mph = v.coerceIn(0f, 140f)
        updateTicker()
    }

    private fun updateTicker() {
        val move = running && mph > 0.5f && isAttachedToWindow && windowVisibility == VISIBLE
        if (move && !ticker.isStarted) ticker.start()
        if (!move && ticker.isStarted) ticker.cancel()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateTicker() }
    override fun onDetachedFromWindow() { ticker.cancel(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateTicker() }

    // ---------------------------------------------------------------- dibujo
    private val asphalt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    private fun px(t: Float, u: Float) = vpX + t * (nearX + u * halfW - vpX)
    private fun py(t: Float) = vpY + t * (nearY - vpY)

    /** Franja entre profundidades t0 (lejos) y t1 (cerca), centrada en u con medio ancho du. */
    private fun quad(c: Canvas, t0: Float, t1: Float, u: Float, du: Float, p: Paint) {
        path.reset()
        path.moveTo(px(t0, u - du), py(t0))
        path.lineTo(px(t0, u + du), py(t0))
        path.lineTo(px(t1, u + du), py(t1))
        path.lineTo(px(t1, u - du), py(t1))
        path.close()
        c.drawPath(path, p)
    }

    private fun rebuildShaders() {
        asphalt.shader = LinearGradient(vpX, vpY, nearX, nearY, mix(dayFar, nightFar), mix(dayNear, nightNear), Shader.TileMode.CLAMP)
        // Piso mojado: reflejo brillante a lo largo del centro, solo de noche
        val a = (night * 70).toInt()
        sheen.shader = LinearGradient(vpX, vpY, nearX, nearY,
            intArrayOf(Color.argb(a, 120, 200, 255), Color.argb(a / 3, 80, 140, 200), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        shaderDirty = false
    }

    override fun onDraw(c: Canvas) {
        if (halfW <= 0f) return
        if (shaderDirty) rebuildShaders()

        // Asfalto
        path.reset()
        path.moveTo(vpX, vpY)
        path.lineTo(px(1.6f, 1.12f), py(1.6f))
        path.lineTo(px(1.6f, -1.12f), py(1.6f))
        path.close()
        c.drawPath(path, asphalt)
        if (night > 0.01f) { quad(c, 0.02f, 1.6f, 0f, 0.55f, sheen) }

        // Bordes del camino (neón tenue de noche, ámbar suave de día)
        val edge = mix(edgeAccentDay, edgeAccentNight)
        glow.color = edge; glow.alpha = (40 + 60 * night).toInt()
        line.color = edge; line.alpha = 230
        for (u in floatArrayOf(-1f, 1f)) {
            quad(c, 0.015f, 1.6f, u, 0.035f, glow)
            quad(c, 0.015f, 1.6f, u, 0.012f, line)
        }

        // Rayas discontinuas de carril (3 carriles) que avanzan con la velocidad
        line.color = mix(dayLine, nightLine)
        val spacing = 4f; val len = 1.8f; val zFar = 60f
        val off = phase % spacing
        for (u in floatArrayOf(-0.33f, 0.33f)) {
            var z = 1f - off - spacing
            while (z < zFar) {
                val zNear = max(z, 0.62f); val zEnd = z + len
                if (zEnd > zNear) {
                    val tNear = 1f / zNear; val tFar = 1f / zEnd
                    line.alpha = (255 * (0.35f + 0.65f * tNear.coerceAtMost(1f))).toInt()
                    quad(c, tFar, tNear, u, 0.014f, line)
                }
                z += spacing
            }
        }

        // Neblina en el horizonte para que la fuga no termine en punta dura
        glow.color = mix(0xFFF7D9C2.toInt(), 0xFF2A3A66.toInt()); glow.alpha = 120
        c.drawCircle(vpX, vpY, halfW * 0.08f, glow)
    }
}
