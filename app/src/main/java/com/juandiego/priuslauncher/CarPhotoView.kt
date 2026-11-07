package com.juandiego.priuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * El carro de la pantalla de inicio.
 *
 * Para que sea rápido, todo lo que no se mueve (el carro, los cables del color del tema,
 * el brillo de Sport y la sombra) se dibuja UNA sola vez en una imagen del tamaño exacto
 * de la pantalla. Cada cuadro solo copia esa imagen y, si el carro se mueve, gira las llantas.
 * Si el carro está parado no se redibuja nada (no gasta batería ni procesador).
 *
 *  - Llantas: giran solo cuando el GPS detecta movimiento, más rápido a más velocidad.
 *  - Luces: se encienden de 5 PM a 6 AM (MainActivity decide la hora).
 */
class CarPhotoView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    enum class Mode { DAY, NIGHT, SPORT }

    var accent: Int = Color.RED
        set(v) {
            if (field == v) return
            field = v
            rebuild()
            invalidate()
        }

    /** Velocidad del GPS en metros por segundo. */
    var speedMps: Float = 0f
        set(v) {
            val was = moving()
            field = v
            if (was != moving()) {
                lastFrame = 0L
                invalidate()
            }
        }

    var animationEnabled: Boolean = true
        set(v) {
            field = v
            invalidate()
        }

    private var body: Bitmap? = null
    private var pipes: Bitmap? = null
    private var wheelSpec = FloatArray(0)
    private var lampSpec = FloatArray(0)
    private var mode = Mode.NIGHT
    private var custom = false

    private var composed: Bitmap? = null
    private val dst = RectF()

    private class Wheel(val bmp: Bitmap, val left: Float, val top: Float, val cx: Float, val cy: Float, val k: Float, val clip: Path)
    private val wheels = ArrayList<Wheel>()

    private class Lamp(val x: Float, val y: Float, val r: Float, val shader: Shader)
    private val lamps = ArrayList<Lamp>()

    private val filterPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wheelPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val d = resources.displayMetrics.density

    private var running = false
    private var angle = 0f
    private var roadOffset = 0f
    private var lastFrame = 0L

    private var lightsTarget = 0f
    private var lightLevel = 0f

    private fun moving() = speedMps * 2.23694f > 2f

    /**
     * body: el dibujo del carro. pipes: capa de cables (gris) que se pinta con el color del tema, o null.
     * wheels / lamps: cx,cy,rx,ry (fracción del dibujo) de cada rin y cada faro.
     * isCustom: foto elegida por el usuario (sin llantas ni luces animadas).
     */
    fun setCar(body: Bitmap?, pipes: Bitmap?, wheels: FloatArray, lamps: FloatArray, mode: Mode, isCustom: Boolean) {
        this.body = body
        this.pipes = pipes
        this.wheelSpec = wheels
        this.lampSpec = lamps
        this.mode = mode
        this.custom = isCustom
        rebuild()
        invalidate()
    }

    /** Enciende o apaga las luces (con un fundido suave si [fade]). */
    fun setLights(on: Boolean, fade: Boolean) {
        val t = if (on) 1f else 0f
        if (t == lightsTarget && (fade || lightLevel == t)) return
        lightsTarget = t
        if (!fade) lightLevel = t
        lastFrame = 0L
        invalidate()
    }

    fun start() {
        if (running) return
        running = true
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

    private fun parse(spec: FloatArray, i: Int) = floatArrayOf(
        dst.left + spec[i] * dst.width(), dst.top + spec[i + 1] * dst.height(),
        spec[i + 2] * dst.width(), spec[i + 3] * dst.height()
    )

    /** Dibuja una sola vez todo lo que no se mueve. */
    private fun rebuild() {
        for (wh in wheels) wh.bmp.recycle()
        wheels.clear()
        lamps.clear()
        val b = body
        if (b == null || width == 0 || height == 0) {
            composed?.recycle()
            composed = null
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        val scale = min(w / b.width, h * 0.95f / b.height)
        val dw = b.width * scale
        val dh = b.height * scale
        val left = (w - dw) / 2f
        val top = (h - dh) / 2f
        dst.set(left, top, left + dw, top + dh)

        val out = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return
        val c = Canvas(out)

        if (custom) {
            drawCustom(c, b)
        } else {
            drawShadow(c)
            val scaled = try {
                Bitmap.createScaledBitmap(b, dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1), true)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (scaled != null) {
                if (mode == Mode.SPORT) {
                    // Borde de luz del color del tema
                    val off = IntArray(2)
                    val glow = scaled.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL) }, off)
                    val gp = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = (accent and 0x00FFFFFF) or (0x9A shl 24) }
                    c.drawBitmap(glow, left + off[0], top + off[1], gp)
                    glow.recycle()
                    filterPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.85f, 0.85f, 0.85f, 1f) })
                    c.drawBitmap(scaled, left, top, filterPaint)
                    filterPaint.colorFilter = null
                } else {
                    c.drawBitmap(scaled, left, top, filterPaint)
                }
                if (scaled != b) scaled.recycle()
            }
            pipes?.let { p ->
                filterPaint.colorFilter = PorterDuffColorFilter(accent, PorterDuff.Mode.MULTIPLY)
                c.drawBitmap(p, null, dst, filterPaint)
                filterPaint.colorFilter = null
            }
            buildWheels(out)
            buildLamps()
        }
        composed?.recycle()
        composed = out
    }

    /** Sombra (día) o resplandor del color del tema (noche y Sport) bajo el carro. */
    private fun drawShadow(c: Canvas) {
        val cx = dst.centerX()
        val cy = dst.top + dst.height() * 0.95f
        val rx = dst.width() * 0.44f
        val ry = dst.height() * 0.06f
        val color = when (mode) {
            Mode.DAY -> 0x38000000
            Mode.NIGHT -> (accent and 0x00FFFFFF) or (0x40 shl 24)
            Mode.SPORT -> (accent and 0x00FFFFFF) or (0x30 shl 24)
        }
        val g = RadialGradient(cx, cy, rx, intArrayOf(color, color and 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g }
        c.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, p)
    }

    /** Foto propia: se difuminan las orillas para que no se vea el recorte. */
    private fun drawCustom(c: Canvas, b: Bitmap) {
        c.drawBitmap(b, null, dst, filterPaint)
        val cx = dst.centerX()
        val cy = dst.centerY()
        val rx = dst.width() * 0.54f
        val ry = dst.height() * 0.52f
        val g = RadialGradient(cx, cy, rx, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.66f, 1f), Shader.TileMode.CLAMP)
        g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = g
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        })
    }

    /** Recorta cada rin una vez; al andar solo se giran esos pedacitos. */
    private fun buildWheels(src: Bitmap) {
        var i = 0
        while (i + 3 < wheelSpec.size) {
            val (cx, cy, rx, ry) = parse(wheelSpec, i).toList()
            i += 4
            if (rx < 4f || ry < 4f) continue
            val r = Rect((cx - rx - 2).toInt(), (cy - ry - 2).toInt(), (cx + rx + 2).toInt(), (cy + ry + 2).toInt())
            if (!r.intersect(0, 0, src.width, src.height) || r.width() < 4 || r.height() < 4) continue
            val bmp = try { Bitmap.createBitmap(src, r.left, r.top, r.width(), r.height()) } catch (e: Exception) { null } ?: continue
            val clip = Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) }
            wheels.add(Wheel(bmp, r.left.toFloat(), r.top.toFloat(), cx, cy, ry / rx, clip))
        }
    }

    private fun buildLamps() {
        var i = 0
        while (i + 3 < lampSpec.size) {
            val (x, y, rx, ry) = parse(lampSpec, i).toList()
            i += 4
            val r = max(rx, ry) * 2.6f + 6f * d
            val sh = RadialGradient(
                x, y, r,
                intArrayOf(0xFFFFFFFF.toInt(), 0xE6FFF8EC.toInt(), 0x55FFF0D8, 0x00FFF0D8),
                floatArrayOf(0f, 0.18f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            lamps.add(Lamp(x, y, r, sh))
        }
    }

    override fun onDraw(canvas: Canvas) {
        val img = composed ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now

        canvas.drawBitmap(img, 0f, 0f, null)

        val animate = animationEnabled && running
        val mph = speedMps * 2.23694f
        val spinning = animate && moving() && wheels.isNotEmpty()
        if (spinning) {
            // Gira hacia adelante (el carro mira a la izquierda): más rápido a más velocidad
            angle = (angle + dt * min(60f + mph * 9f, 540f)) % 360f
            for (wh in wheels) {
                canvas.save()
                canvas.clipPath(wh.clip)
                canvas.translate(wh.cx, wh.cy)
                canvas.scale(1f / wh.k, 1f)
                canvas.rotate(-angle)
                canvas.scale(wh.k, 1f)
                canvas.translate(-wh.cx, -wh.cy)
                canvas.drawBitmap(wh.bmp, wh.left, wh.top, wheelPaint)
                canvas.restore()
            }
        }
        if (animate && moving()) {
            roadOffset += dt * 260f * d * (mph / 60f).coerceIn(0.2f, 1.6f)
            drawRoad(canvas)
        }

        // Luces
        var fading = false
        if (lightLevel != lightsTarget) {
            val step = if (running) dt * 1.4f else 1f
            lightLevel = if (lightLevel < lightsTarget) min(lightsTarget, lightLevel + step) else max(lightsTarget, lightLevel - step)
            fading = lightLevel != lightsTarget
        }
        if (lightLevel > 0.01f && lamps.isNotEmpty()) {
            lampPaint.alpha = (lightLevel * 255).toInt().coerceIn(0, 255)
            for (l in lamps) {
                lampPaint.shader = l.shader
                canvas.drawCircle(l.x, l.y, l.r, lampPaint)
            }
            lampPaint.shader = null
        }

        if ((animate && moving()) || (fading && running)) postInvalidateOnAnimation()
    }

    /** Rayas de velocidad a los lados del carro. */
    private fun drawRoad(canvas: Canvas) {
        val base = if (mode == Mode.DAY) 0x00000000 else 0x00FFFFFF
        val rows = floatArrayOf(0.42f, 0.52f, 0.62f, 0.72f)
        dashPaint.strokeWidth = 2.2f * d
        for ((k, fy) in rows.withIndex()) {
            for (side in 0..1) {
                val x0 = if (side == 0) dst.left else dst.right - dst.width() * 0.16f
                val x1 = if (side == 0) dst.left + dst.width() * 0.16f else dst.right
                val len = 20f * d
                val period = 46f * d
                var x = x0 - period + ((roadOffset * (1f + k * 0.15f)) % period)
                while (x < x1) {
                    val mid = x + len / 2f
                    val e = 1f - (abs(mid - (x0 + x1) / 2f) / ((x1 - x0) / 2f)).coerceIn(0f, 1f)
                    val a = (e * (if (mode == Mode.DAY) 90 else 150)).toInt()
                    if (a > 2) {
                        dashPaint.color = base or (a shl 24)
                        val y = dst.top + dst.height() * fy
                        canvas.drawLine(x, y, x + len, y - len * 0.18f, dashPaint)
                    }
                    x += period
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }
}
