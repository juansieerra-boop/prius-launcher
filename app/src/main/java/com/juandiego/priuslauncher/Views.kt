package com.juandiego.priuslauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun View.dp(v: Float) = v * resources.displayMetrics.density
private fun withAlpha(color: Int, alpha: Int) = (color and 0x00FFFFFF) or (alpha shl 24)

/** Dos franjas diagonales de carreras, del color elegido. */
class StripesView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var accent: Int = Color.RED
        set(v) { field = v; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val slant = h * 0.27f
        val x0 = width * 0.87f
        band(canvas, x0, dp(14f), slant, h, 0x26)
        band(canvas, x0 + dp(24f), dp(9f), slant, h, 0x17)
    }

    private fun band(canvas: Canvas, x: Float, bw: Float, slant: Float, h: Float, alpha: Int) {
        path.reset()
        path.moveTo(x, 0f)
        path.lineTo(x + bw, 0f)
        path.lineTo(x + bw - slant, h)
        path.lineTo(x - slant, h)
        path.close()
        paint.color = withAlpha(accent, alpha)
        canvas.drawPath(path, paint)
    }
}

/** Barras inclinadas tipo tacómetro. level va de 0 a 1. */
class SegmentBar @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var color: Int = Color.RED
        set(v) { field = v; invalidate() }
    var level: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val count = 10
    private val trackColor = ctx.getColor(R.color.hero_track)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val gap = dp(3f)
        val h = height.toFloat()
        val skew = h * 0.32f
        val segW = (width - gap * (count - 1)) / count
        val lit = (level * count).roundToInt()
        for (i in 0 until count) {
            val l = i * (segW + gap)
            path.reset()
            path.moveTo(l + skew, 0f)
            path.lineTo(l + segW, 0f)
            path.lineTo(l + segW - skew, h)
            path.lineTo(l, h)
            path.close()
            paint.color = if (i < lit) color else trackColor
            canvas.drawPath(path, paint)
        }
    }
}

/** Línea de progreso fina con puntas redondas. */
class ProgressLine @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    private val trackColor = ctx.getColor(R.color.track)
    var color: Int = Color.RED
        set(v) { field = v; invalidate() }
    var level: Float = 0f
        set(v) { field = v.coerceIn(0f, 1f); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        val rad = height / 2f
        r.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = trackColor
        canvas.drawRoundRect(r, rad, rad, paint)
        if (level > 0f) {
            r.right = max(height.toFloat(), width * level)
            paint.color = color
            canvas.drawRoundRect(r, rad, rad, paint)
        }
    }
}

/** Dibujo de mapa oscuro con una ruta del color elegido (abre la app de mapas al tocarlo). */
open class MapTileView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var accent: Int = Color.RED
        set(v) { field = v; invalidate() }

    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = ctx.getColor(R.color.map_road)
    }
    protected val mapBg = ctx.getColor(R.color.map_bg)
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    // Coordenadas en un lienzo de 440x130
    private val roads = listOf(
        5f to floatArrayOf(-10f, 40f, 80f, 32f, 150f, 55f, 230f, 45f, 310f, 30f, 380f, 26f, 460f, 38f),
        4f to floatArrayOf(-10f, 95f, 90f, 100f, 170f, 82f, 260f, 92f, 330f, 102f, 400f, 106f, 460f, 96f),
        3f to floatArrayOf(60f, -10f, 68f, 40f, 52f, 80f, 75f, 140f),
        3f to floatArrayOf(150f, -10f, 170f, 140f),
        4f to floatArrayOf(290f, -10f, 282f, 50f, 304f, 90f, 295f, 140f),
        2.5f to floatArrayOf(380f, -10f, 360f, 140f),
        2f to floatArrayOf(0f, 65f, 460f, 70f),
        2f to floatArrayOf(220f, 0f, 240f, 140f)
    )
    private val route = floatArrayOf(20f, 120f, 60f, 104f, 90f, 96f, 120f, 92f, 170f, 82f, 240f, 66f, 300f, 50f, 340f, 40f)

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(mapBg)
        val s = max(width / 440f, height / 130f)
        val ox = (width - 440f * s) / 2f
        val oy = (height - 130f * s) / 2f
        for ((w, pts) in roads) {
            roadPaint.strokeWidth = w * s
            canvas.drawPath(smooth(pts, s, ox, oy), roadPaint)
        }
        routePaint.color = accent
        routePaint.strokeWidth = 4f * s
        canvas.drawPath(smooth(route, s, ox, oy), routePaint)

        fill.color = Color.WHITE
        canvas.drawCircle(ox + 120f * s, oy + 92f * s, 7f * s, fill)
        fill.color = accent
        canvas.drawCircle(ox + 120f * s, oy + 92f * s, 4f * s, fill)
        canvas.drawCircle(ox + 340f * s, oy + 40f * s, 6f * s, fill)
    }

    private fun smooth(p: FloatArray, s: Float, ox: Float, oy: Float): Path {
        path.reset()
        val n = p.size / 2
        fun x(i: Int) = ox + p[i * 2] * s
        fun y(i: Int) = oy + p[i * 2 + 1] * s
        path.moveTo(x(0), y(0))
        if (n == 2) {
            path.lineTo(x(1), y(1))
            return path
        }
        for (i in 1 until n - 1) {
            path.quadTo(x(i), y(i), (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f)
        }
        path.lineTo(x(n - 1), y(n - 1))
        return path
    }
}

/** Barra arcoíris para elegir cualquier color. */
class HueBar @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {
    var onHueChanged: ((Float) -> Unit)? = null
    var hue: Float = 0f
        set(v) { field = v.coerceIn(0f, 360f); invalidate() }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = height / 2f
        val barH = dp(10f)
        val cy = height / 2f
        r.set(pad, cy - barH / 2, width - pad, cy + barH / 2)
        val colors = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f, 0.85f, 1f)) }
        barPaint.shader = LinearGradient(r.left, 0f, r.right, 0f, colors, null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(r, barH / 2, barH / 2, barPaint)

        val tx = r.left + r.width() * hue / 360f
        thumbPaint.color = Color.HSVToColor(floatArrayOf(hue, 0.85f, 1f))
        canvas.drawCircle(tx, cy, pad * 0.62f, thumbPaint)
        ringPaint.strokeWidth = dp(3f)
        canvas.drawCircle(tx, cy, pad * 0.62f, ringPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val pad = height / 2f
                val f = ((event.x - pad) / (width - 2 * pad)).coerceIn(0f, 1f)
                hue = f * 360f
                onHueChanged?.invoke(hue)
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}
