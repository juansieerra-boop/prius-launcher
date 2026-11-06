package com.juandiego.priuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * La foto del carro con las orillas difuminadas y animada:
 *  - Encendido: los faros parpadean al arrancar y luego "respiran"; un resplandor
 *    del color elegido pulsa debajo del carro.
 *  - Andando (con velocidad del GPS): líneas de carretera que pasan bajo el carro
 *    y un ligero rebote de suspensión, más rápido mientras más rápido vas.
 */
class CarPhotoView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var accent: Int = Color.RED
    /** Velocidad del GPS en metros por segundo. */
    var speedMps: Float = 0f
    var animationEnabled: Boolean = true
        set(v) {
            field = v
            invalidate()
        }

    private var source: Bitmap? = null
    private var composed: Bitmap? = null
    private var showHeadlights = true
    private val dst = RectF()

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val isDay = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_NO

    /** Faros: x1,y1,x2,y2 como fracción de la foto (cambia según el carro). */
    private val headlights: FloatArray = try {
        ctx.getString(R.string.headlights).split(",").map { it.trim().toFloat() }.toFloatArray()
    } catch (e: Exception) {
        FloatArray(0)
    }

    private val floorY: Float = try {
        ctx.getString(R.string.floor_y).trim().toFloat()
    } catch (e: Exception) {
        0.86f
    }

    private var running = false
    private var startTime = SystemClock.uptimeMillis()
    private var roadOffset = 0f
    private var lastFrame = 0L

    private val d = resources.displayMetrics.density

    /** isDefault = la foto original del carro (ahí sabemos dónde están los faros). */
    fun setImageBitmap(b: Bitmap?, isDefault: Boolean) {
        source = b
        showHeadlights = isDefault
        rebuild()
        startTime = SystemClock.uptimeMillis()
        invalidate()
    }

    fun start() {
        if (running) return
        running = true
        startTime = SystemClock.uptimeMillis()
        lastFrame = 0L
        invalidate()
    }

    fun stop() {
        running = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    /** Dibuja una sola vez la foto con su máscara; cada cuadro de la animación solo la copia. */
    private fun rebuild() {
        val b = source
        if (b == null || width == 0 || height == 0) {
            composed = null
            return
        }
        val scale = min(width.toFloat() / b.width, height.toFloat() / b.height)
        val w = b.width * scale
        val h = b.height * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        dst.set(left, top, left + w, top + h)

        val out = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return
        val c = Canvas(out)
        c.drawBitmap(b, null, dst, bmpPaint)
        val cx = dst.centerX()
        val cy = dst.top + h * 0.54f
        val rx = w * 0.54f
        val ry = h * 0.52f
        val g = RadialGradient(
            cx, cy, rx,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, 0.66f, 1f),
            Shader.TileMode.CLAMP
        )
        g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = g
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), mask)
        composed?.recycle()
        composed = out
    }

    override fun onDraw(canvas: Canvas) {
        val img = composed ?: return
        val now = SystemClock.uptimeMillis()
        val t = (now - startTime) / 1000f
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now

        val animate = animationEnabled && running
        val mph = speedMps * 2.23694f
        val moving = animate && mph > 3f
        val speedFactor = (mph / 60f).coerceIn(0.2f, 1.6f)

        // Rebote de suspensión al andar
        val bob = if (moving) sin(t * 2f * PI.toFloat() * (1.6f + speedFactor)) * 0.9f * d * min(1f, mph / 15f) else 0f

        canvas.drawBitmap(img, 0f, bob, null)
        if (animate) drawUnderglow(canvas, t, moving, speedFactor, bob)
        if (moving) {
            roadOffset += dt * 260f * d * speedFactor
            drawRoad(canvas)
        }
        if (animate && showHeadlights && headlights.size >= 4) drawHeadlights(canvas, t, moving, bob)

        if (animate) postInvalidateOnAnimation()
    }

    /** Resplandor del color elegido bajo el carro. */
    private fun drawUnderglow(canvas: Canvas, t: Float, moving: Boolean, speedFactor: Float, bob: Float) {
        val period = if (moving) 1.2f / speedFactor else 2.6f
        val pulse = 0.5f + 0.5f * sin(t * 2f * PI.toFloat() / period)
        val alpha = ((if (isDay) 30 else 55) + pulse * (if (isDay) 35 else 60)).toInt()
        val cx = dst.centerX()
        val cy = dst.top + dst.height() * (floorY - 0.01f) + bob
        val rx = dst.width() * 0.34f
        val ry = dst.height() * 0.05f
        val g = RadialGradient(
            cx, cy, rx,
            intArrayOf((accent and 0x00FFFFFF) or (alpha shl 24), accent and 0x00FFFFFF),
            null, Shader.TileMode.CLAMP
        )
        g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
        glowPaint.shader = g
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, glowPaint)
        glowPaint.shader = null
    }

    /** Líneas de carretera que pasan bajo el carro. */
    private fun drawRoad(canvas: Canvas) {
        val base = if (isDay) 0x00000000 else 0x00FFFFFF
        val rows = arrayOf(floatArrayOf(floorY + 0.02f, 0.8f, 2f), floatArrayOf(floorY + 0.07f, 1.25f, 3f))
        for (row in rows) {
            val y = dst.top + dst.height() * row[0]
            val dash = 26f * d * row[1]
            val gap = 34f * d * row[1]
            val period = dash + gap
            dashPaint.strokeWidth = row[2] * d
            var x = dst.left - period + (roadOffset * row[1]) % period
            while (x < dst.right) {
                val mid = x + dash / 2f
                val edge = 1f - (abs(mid - dst.centerX()) / (dst.width() / 2f)).coerceIn(0f, 1f)
                val a = (edge * edge * (if (isDay) 70 else 110)).toInt()
                if (a > 2) {
                    dashPaint.color = base or (a shl 24)
                    canvas.drawLine(x, y, x + dash, y, dashPaint)
                }
                x += period
            }
        }
    }

    /** Faros: parpadean dos veces al arrancar y luego quedan encendidos con un brillo suave. */
    private fun drawHeadlights(canvas: Canvas, t: Float, moving: Boolean, bob: Float) {
        val intro = when {
            t < 0.12f -> 1f
            t < 0.28f -> 0f
            t < 0.40f -> 1f
            t < 0.55f -> 0.15f
            t < 1.3f -> (t - 0.55f) / 0.75f
            else -> 1f
        }
        val breathe = 0.85f + 0.15f * sin(t * 2f * PI.toFloat() / 3f)
        val power = intro * breathe * (if (moving) 1.1f else 1f) * (if (isDay) 0.75f else 1f)
        if (power <= 0.01f) return
        val r = dst.width() * 0.05f
        var i = 0
        while (i + 1 < headlights.size) {
            val x = dst.left + dst.width() * headlights[i]
            val y = dst.top + dst.height() * headlights[i + 1] + bob
            val a = (210 * power).toInt().coerceIn(0, 255)
            val g = RadialGradient(
                x, y, r,
                intArrayOf((a shl 24) or 0xFFF8EC, ((a / 3) shl 24) or 0xFFF0D8, 0x00FFF0D8),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP
            )
            glowPaint.shader = g
            canvas.drawCircle(x, y, r, glowPaint)
            i += 2
        }
        glowPaint.shader = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }
}
