package com.cargo.launcher

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Velocímetro circular: arco de 270° con marcas, progreso con degradado y resplandor de neón.
 * El número grande lo pone el TextView `speedValue` encima (así conserva el autoajuste de tamaño).
 * La aguja/arco se anima suave entre lecturas del OBD2.
 */
class SpeedGaugeView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var maxValue = 80f
        set(v) { field = v; invalidate() }
    private var shown = 0f
    private var anim: ValueAnimator? = null

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val oval = RectF()
    private var accent = 0xFF22D3EE.toInt()
    private var accentSoft = 0xFF0EA5B7.toInt()

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        track.color = 0x33FFFFFF
        tick.color = 0x66FFFFFF
    }   // BlurMaskFilter necesita capa por software

    fun setColors(accent: Int, accentSoft: Int, trackColor: Int, tickColor: Int) {
        this.accent = accent; this.accentSoft = accentSoft
        track.color = trackColor; tick.color = tickColor
        arc.shader = null
        invalidate()
    }

    fun setValue(v: Float) {
        val target = v.coerceIn(0f, maxValue)
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, target).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDetachedFromWindow() { anim?.cancel(); super.onDetachedFromWindow() }

    override fun onDraw(c: Canvas) {
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val sw = size * 0.075f
        val cx = width / 2f; val cy = height / 2f
        val r = size / 2f - sw * 1.4f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        track.strokeWidth = sw; arc.strokeWidth = sw
        halo.strokeWidth = sw * 1.6f
        halo.maskFilter = BlurMaskFilter(sw, BlurMaskFilter.Blur.NORMAL)

        c.drawArc(oval, 135f, 270f, false, track)

        // marcas cada 10 (largas cada 20)
        val steps = (maxValue / 10f).toInt()
        for (i in 0..steps) {
            val a = Math.toRadians((135.0 + 270.0 * i / steps))
            val long = i % 2 == 0
            val r1 = r - sw * 1.1f; val r2 = r1 - (if (long) sw * 0.9f else sw * 0.5f)
            tick.strokeWidth = if (long) size * 0.012f else size * 0.008f
            c.drawLine(cx + r1 * cos(a).toFloat(), cy + r1 * sin(a).toFloat(), cx + r2 * cos(a).toFloat(), cy + r2 * sin(a).toFloat(), tick)
        }

        val sweep = 270f * (shown / maxValue)
        if (sweep > 0.5f) {
            if (arc.shader == null) {
                arc.shader = SweepGradient(cx, cy, intArrayOf(accentSoft, accent, accent), floatArrayOf(0f, 0.6f, 1f)).apply {
                    setLocalMatrix(Matrix().apply { setRotate(135f, cx, cy) })
                }
            }
            halo.color = accent; halo.alpha = 110
            c.drawArc(oval, 135f, sweep, false, halo)
            c.drawArc(oval, 135f, sweep, false, arc)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        arc.shader = null   // se recrea centrado en el nuevo tamaño
    }
}
