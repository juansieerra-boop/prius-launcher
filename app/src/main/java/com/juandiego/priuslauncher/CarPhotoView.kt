package com.juandiego.priuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * El carro de la pantalla de inicio, con:
 *  - la carretera estilo HUD debajo (cuadrícula, carril azul, rayas y flechas verdes; en Sport, del color del tema),
 *  - los rines que giran (un rin dibujado aparte que gira; la llanta y su sombra no se mueven),
 *  - las luces, que se encienden de 5 PM a 6 AM (MainActivity decide la hora).
 *
 * Para que sea rápido: el carro se escala UNA sola vez; los rines y el brillo de las luces se
 * preparan una sola vez. Cada cuadro solo dibuja líneas y copia imágenes ya listas.
 * Si el carro está parado no se redibuja nada: la carretera se queda quieta y se ve el rin original.
 */
class CarPhotoView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    enum class Mode { DAY, NIGHT, SPORT }

    var accent: Int = Color.GREEN
        set(v) {
            if (field == v) return
            field = v
            setupColors()
            invalidate()
        }

    /** Velocidad del GPS en metros por segundo. */
    var speedMps: Float = 0f
        set(v) {
            field = v
            kick()
        }

    var animationEnabled: Boolean = true
        set(v) {
            field = v
            kick()
        }

    /** Ancho del carro respecto a la vista, y dónde queda su parte de abajo (fracción de la altura). */
    var carWidthFrac = 0.96f
    var carBottomFrac = 0.78f
    var carMaxHeightFrac = 0.64f

    private var body: Bitmap? = null
    private var wheelSpec = FloatArray(0)
    private var lampSpec = FloatArray(0)
    private var anchor = floatArrayOf(0.484f, 0.982f)
    private var mode = Mode.NIGHT
    private var custom = false

    private var scaled: Bitmap? = null
    private val dst = RectF()
    private val d = resources.displayMetrics.density

    // ---------- Carretera ----------
    private val ax = 0.966f   // dirección "hacia atrás del carro" sobre el piso (323,-86 normalizado)
    private val ay = -0.257f
    private val bx = 0.988f   // dirección "a lo ancho" (365,57 normalizado)
    private val by = 0.154f
    private var k = 1f
    private var p0x = 0f
    private var p0y = 0f
    private var roadDist = 0f
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val fadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val arrowPath = Path()
    private var glow = true

    // ---------- Rines ----------
    private class Rim(val cx: Float, val cy: Float, val sx: Float, val sy: Float, val tilt: Float)
    private val rims = ArrayList<Rim>()
    private var rimBase: Bitmap? = null
    private var rimSpokes: Bitmap? = null
    private var rimLight: Bitmap? = null
    private val rimR = 128f
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var rimAngle = 0f
    private var rimAlpha = 0f

    // ---------- Luces ----------
    private class Lamp(val x: Float, val y: Float, val r: Float, val shader: Shader)
    private val lamps = ArrayList<Lamp>()
    private val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lightsTarget = 0f
    private var lightLevel = 0f

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var running = false
    private var lastFrame = 0L
    private var mph = 0f

    private fun targetMph() = if (animationEnabled) (speedMps * 2.23694f).coerceIn(0f, 60f) else 0f
    private fun moving() = mph > 1.5f || targetMph() > 2f

    init {
        setupColors()
    }

    /**
     * body: el dibujo del carro. wheels: cx,cy,rx,ry,inclinación de cada rin; lamps: cx,cy,rx,ry de cada faro
     * (fracciones del dibujo). isCustom: foto elegida por el usuario (sin carretera, rines ni luces).
     */
    fun setCar(body: Bitmap?, wheels: FloatArray, lamps: FloatArray, roadAnchor: FloatArray, mode: Mode, isCustom: Boolean) {
        this.body = body
        this.wheelSpec = wheels
        this.lampSpec = lamps
        if (roadAnchor.size >= 2) this.anchor = roadAnchor
        this.mode = mode
        this.custom = isCustom
        setupColors()
        rebuild()
        invalidate()
    }

    /** Enciende o apaga las luces (con un fundido suave si [fade]). */
    fun setLights(on: Boolean, fade: Boolean) {
        val t = if (on) 1f else 0f
        if (t == lightsTarget && (fade || lightLevel == t)) return
        lightsTarget = t
        if (!fade) lightLevel = t
        kick()
    }

    fun start() {
        if (running) return
        running = true
        kick()
    }

    fun stop() {
        running = false
    }

    private fun kick() {
        lastFrame = 0L
        invalidate()
    }

    private fun setupColors() {
        val grid: Int
        val lane: Int
        val dash: Int
        when (mode) {
            Mode.NIGHT -> { grid = Color.rgb(30, 85, 140); lane = Color.rgb(40, 150, 255); dash = Color.rgb(60, 255, 120); glow = true }
            Mode.DAY -> { grid = Color.rgb(150, 190, 225); lane = Color.rgb(40, 140, 245); dash = Color.rgb(60, 200, 110); glow = false }
            Mode.SPORT -> { grid = scaleColor(accent, 0.35f); lane = accent; dash = accent; glow = true }
        }
        gridPaint.color = grid
        lanePaint.color = lane
        dashPaint.color = dash
        arrowPaint.color = dash
        glowPaint.color = lane
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    /** Prepara una sola vez: el carro escalado, los rines y el brillo de las luces. */
    private fun rebuild() {
        rims.clear()
        lamps.clear()
        val b = body
        if (b == null || width == 0 || height == 0) {
            scaled = null
            return
        }
        val w = width.toFloat()
        val h = height.toFloat()
        var dw = w * carWidthFrac
        var dh = dw * b.height / b.width
        if (dh > h * carMaxHeightFrac) {
            dh = h * carMaxHeightFrac
            dw = dh * b.width / b.height
        }
        val left = (w - dw) / 2f
        val bottom = h * carBottomFrac
        dst.set(left, bottom - dh, left + dw, bottom)

        scaled?.let { if (it != b) it.recycle() }
        scaled = try {
            Bitmap.createScaledBitmap(b, dw.toInt().coerceAtLeast(1), dh.toInt().coerceAtLeast(1), true)
        } catch (e: OutOfMemoryError) {
            b
        }

        // Carretera: el punto del piso bajo la llanta delantera, y la escala (el diseño se hizo con el carro a 735 px)
        k = dw / 735f
        p0x = dst.left + anchor[0] * dw
        p0y = dst.top + anchor[1] * dh
        val fx = p0x + 21f * k
        val fy = p0y + 22f * k
        val rx = 600f * k
        val ry = 260f * k
        val g = RadialGradient(fx, fy, rx, intArrayOf(Color.BLACK, Color.BLACK, 0x60000000, Color.TRANSPARENT), floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
        g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, fx, fy); postRotate(-8f, fx, fy) })
        fadePaint.shader = g
        gridPaint.strokeWidth = max(1f, 1.1f * k)
        lanePaint.strokeWidth = 4f * k
        glowPaint.strokeWidth = 14f * k
        dashPaint.strokeWidth = 5f * k

        if (!custom) {
            var i = 0
            while (i + 4 < wheelSpec.size) {
                val cx = dst.left + wheelSpec[i] * dw
                val cy = dst.top + wheelSpec[i + 1] * dh
                rims.add(Rim(cx, cy, wheelSpec[i + 2] * dw / rimR, wheelSpec[i + 3] * dh / rimR, wheelSpec[i + 4]))
                i += 5
            }
            if (rimBase == null) buildRim()
            i = 0
            while (i + 3 < lampSpec.size) {
                val x = dst.left + lampSpec[i] * dw
                val y = dst.top + lampSpec[i + 1] * dh
                val r = max(lampSpec[i + 2] * dw, lampSpec[i + 3] * dh) * 2.8f + 6f * d
                val sh = RadialGradient(
                    x, y, r,
                    intArrayOf(0xFFFFFFFF.toInt(), 0xE6FFF8EC.toInt(), 0x55FFF0D8, 0x00FFF0D8),
                    floatArrayOf(0f, 0.16f, 0.42f, 1f), Shader.TileMode.CLAMP
                )
                lamps.add(Lamp(x, y, r, sh))
                i += 4
            }
        }
    }

    // ---------- El rin dibujado (5 rayos en Y, como el del Prius) ----------

    private fun buildRim() {
        val s = (rimR * 2).toInt()
        val c = rimR
        // Fondo fijo: disco oscuro, labio y orilla clara
        val base = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(base).apply {
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(32, 33, 34); drawCircle(c, c, rimR, p)
            p.color = Color.rgb(66, 68, 70); drawCircle(c, c, rimR * 0.925f, p)
            p.color = Color.rgb(15, 15, 16); drawCircle(c, c, rimR * 0.90f, p)
        }
        // Rayos, centro y birlos (esto es lo que gira)
        val spokes = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        Canvas(spokes).apply {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(59, 61, 64) }
            val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(90, 92, 96); style = Paint.Style.STROKE; strokeWidth = rimR * 0.016f; strokeCap = Paint.Cap.ROUND
            }
            fun bar(r0: Float, a0: Float, r1: Float, a1: Float, w0: Float, w1: Float) {
                val A0 = Math.toRadians(a0.toDouble()); val A1 = Math.toRadians(a1.toDouble())
                val x0 = c + cos(A0).toFloat() * rimR * r0; val y0 = c + sin(A0).toFloat() * rimR * r0
                val x1 = c + cos(A1).toFloat() * rimR * r1; val y1 = c + sin(A1).toFloat() * rimR * r1
                val l = hypot(x1 - x0, y1 - y0)
                val nx = -(y1 - y0) / l; val ny = (x1 - x0) / l
                val p = Path().apply {
                    moveTo(x0 + nx * w0, y0 + ny * w0); lineTo(x1 + nx * w1, y1 + ny * w1)
                    lineTo(x1 - nx * w1, y1 - ny * w1); lineTo(x0 - nx * w0, y0 - ny * w0); close()
                }
                drawPath(p, fill)
                drawLine(x0 + nx * w0, y0 + ny * w0, x1 + nx * w1, y1 + ny * w1, hl)
            }
            for (i in 0 until 5) {
                val a = i * 72f
                bar(0.2f, a, 0.52f, a, rimR * 0.085f, rimR * 0.07f)
                bar(0.48f, a, 0.9f, a - 11f, rimR * 0.05f, rimR * 0.045f)
                bar(0.48f, a, 0.9f, a + 11f, rimR * 0.05f, rimR * 0.045f)
            }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(53, 55, 58); drawCircle(c, c, rimR * 0.24f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = rimR * 0.016f; p.color = Color.rgb(84, 86, 90); drawCircle(c, c, rimR * 0.24f, p)
            p.style = Paint.Style.FILL; p.color = Color.rgb(37, 38, 40); drawCircle(c, c, rimR * 0.10f, p)
            p.color = Color.rgb(144, 146, 150)
            for (i in 0 until 5) {
                val a = Math.toRadians((36 + i * 72).toDouble())
                drawCircle(c + cos(a).toFloat() * rimR * 0.165f, c + sin(a).toFloat() * rimR * 0.165f, rimR * 0.03f, p)
            }
        }
        // Luz fija: más clara arriba a la izquierda, más oscura abajo a la derecha (no gira)
        val px = IntArray(s * s)
        for (y in 0 until s) for (x in 0 until s) {
            val dx = x - c; val dy = y - c
            if (sqrt(dx * dx + dy * dy) > rimR) continue
            val f = (1f + 0.35f * (-dx - dy) / (rimR * 1.4f)).coerceIn(0.75f, 1.35f)
            px[y * s + x] = if (f >= 1f) Color.argb(((f - 1f) * 0.33f * 255).toInt(), 255, 255, 255)
            else Color.argb(((1f - f) * 255).toInt(), 0, 0, 0)
        }
        rimBase = base
        rimSpokes = spokes
        rimLight = Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888)
    }

    // ---------- Dibujo ----------

    override fun onDraw(canvas: Canvas) {
        val img = scaled ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1000f).coerceAtMost(0.1f)
        lastFrame = now

        // Velocidad suavizada (el GPS cambia cada segundo; así no da brincos)
        val target = targetMph()
        mph += (target - mph) * min(1f, dt * 2.5f)
        if (target == 0f && mph < 0.3f) mph = 0f
        val isMoving = running && moving()
        if (isMoving) {
            roadDist += dt * mph * 6f * k
            rimAngle = (rimAngle + dt * mph * 7f) % 360f
        }
        val rimTarget = if (isMoving && mph > 1.5f) 1f else 0f
        if (rimAlpha != rimTarget) {
            rimAlpha = if (rimAlpha < rimTarget) min(rimTarget, rimAlpha + dt * 2.5f) else max(rimTarget, rimAlpha - dt * 2.5f)
            if (!running) rimAlpha = rimTarget
        }

        if (!custom) drawRoad(canvas)
        canvas.drawBitmap(img, dst.left, dst.top, bmpPaint)
        if (rimAlpha > 0.01f) drawRims(canvas)

        var fading = false
        if (lightLevel != lightsTarget) {
            val step = if (running) dt * 1.2f else 1f
            lightLevel = if (lightLevel < lightsTarget) min(lightsTarget, lightLevel + step) else max(lightsTarget, lightLevel - step)
            fading = lightLevel != lightsTarget
        }
        if (lightLevel > 0.01f && lamps.isNotEmpty()) {
            lampPaint.alpha = (lightLevel * if (mode == Mode.DAY) 200 else 255).toInt().coerceIn(0, 255)
            for (l in lamps) {
                lampPaint.shader = l.shader
                canvas.drawCircle(l.x, l.y, l.r, lampPaint)
            }
            lampPaint.shader = null
        }

        if (running && (isMoving || fading || rimAlpha != rimTarget || mph > 0f)) postInvalidateOnAnimation()
    }

    private fun line(c: Canvas, px: Float, py: Float, dx: Float, dy: Float, a0: Float, a1: Float, p: Paint) {
        c.drawLine(px + dx * a0, py + dy * a0, px + dx * a1, py + dy * a1, p)
    }

    /** La carretera HUD: se dibuja en una capa y se desvanece hacia las orillas. */
    private fun drawRoad(c: Canvas) {
        val save = c.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        val far = 1600f * k
        val cell = 40f * k
        // Cuadrícula: líneas a lo largo (fijas) y de lado (avanzan)
        for (j in -30..30) line(c, p0x + j * cell * bx, p0y + j * cell * by, ax, ay, -far, far, gridPaint)
        val off = roadDist % cell
        for (j in -40..40) {
            val t = j * cell + off
            line(c, p0x + t * ax, p0y + t * ay, bx, by, -far, far, gridPaint)
        }
        // Carriles azules (con brillo de noche)
        for (lat in floatArrayOf(60f, -350f)) {
            val x = p0x + lat * k * bx; val y = p0y + lat * k * by
            if (glow) {
                glowPaint.alpha = 0x40
                line(c, x, y, ax, ay, -far, far, glowPaint)
            }
            line(c, x, y, ax, ay, -far, far, lanePaint)
        }
        // Rayas verdes
        val period = 120f * k
        val ds = (roadDist * 1.6f) % period
        for (lat in floatArrayOf(190f, -560f)) {
            for (j in -12..12) {
                val t = j * period + ds
                val x = p0x + lat * k * bx + t * ax; val y = p0y + lat * k * by + t * ay
                if (glow) {
                    glowPaint.color = dashPaint.color; glowPaint.alpha = 0x3C
                    line(c, x, y, ax, ay, 0f, 55f * k, glowPaint)
                    glowPaint.color = lanePaint.color
                }
                line(c, x, y, ax, ay, 0f, 55f * k, dashPaint)
            }
        }
        // Flechas
        val ap = 260f * k
        val ao = (roadDist * 3.2f) % ap
        for (j in -4..4) {
            val t = j * ap + ao
            val cx = p0x + 330f * k * bx + t * ax; val cy = p0y + 330f * k * by + t * ay
            arrowPath.reset()
            arrowPath.moveTo(cx - 38f * k * ax, cy - 38f * k * ay)
            arrowPath.lineTo(cx + 20f * k * ax - 26f * k * bx, cy + 20f * k * ay - 26f * k * by)
            arrowPath.lineTo(cx, cy)
            arrowPath.lineTo(cx + 20f * k * ax + 26f * k * bx, cy + 20f * k * ay + 26f * k * by)
            arrowPath.close()
            c.drawPath(arrowPath, arrowPaint)
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fadePaint)
        c.restoreToCount(save)
    }

    /** El rin dibujado: solo giran los rayos; el fondo y la luz se quedan fijos. Con estela suave de movimiento. */
    private fun drawRims(c: Canvas) {
        val base = rimBase ?: return
        val spokes = rimSpokes ?: return
        val light = rimLight ?: return
        val a = (rimAlpha * 255).toInt()
        val trail = min(8f, mph * 0.2f)
        for (r in rims) {
            c.save()
            c.translate(r.cx, r.cy)
            c.rotate(r.tilt)
            c.scale(r.sx, r.sy)
            rimPaint.alpha = a
            c.drawBitmap(base, -rimR, -rimR, rimPaint)
            for (j in 2 downTo 0) {
                c.save()
                c.rotate(-rimAngle + j * trail)
                rimPaint.alpha = if (j == 0) a else a * 60 / 255
                c.drawBitmap(spokes, -rimR, -rimR, rimPaint)
                c.restore()
            }
            rimPaint.alpha = a
            c.drawBitmap(light, -rimR, -rimR, rimPaint)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
    }
}
