package com.juandiego.priuslauncher.v4

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.juandiego.priuslauncher.R
import kotlin.math.atan2
import kotlin.math.max

/**
 * El carro sobre el fondo minimalista: pared y piso lisos sin línea dura, luz suave detrás,
 * sombra de la huella de las 4 llantas, vidrios polarizados y faros (5 PM–6 AM o según Ajustes).
 * Todas las medidas salen del dibujo del carro (909x454), igual que en el render aprobado.
 */
class CarStageView(ctx: Context, private val u: U) : View(ctx) {

    private val car: Bitmap? = load(R.drawable.v4_car)
    /** Medidas del carro en su dibujo (cada carro pone las suyas en res/values/v4_car.xml). */
    private val g: FloatArray = try {
        ctx.getString(R.string.v4_geom).split(",").map { it.trim().toFloat() }.toFloatArray()
    } catch (e: Exception) {
        floatArrayOf(909f, 454f, 444f, 425f, 844f, 321f, -229.5f, -34f, 0.72f, 40f, 25f, 0f)
    }
    private val imgW get() = g[0]
    private val imgH get() = g[1]
    private var galleryFocusX = 0.5f
    private val lights: Bitmap? = load(R.drawable.v4_lights)
    private var scene: Bitmap? = null
    private var gallery: Bitmap? = null

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val lightPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val carDst = RectF()
    private var lightLevel = 0f
    private var lightAnim: ValueAnimator? = null

    var lightsOn = false
        set(v) {
            if (field == v) return
            field = v
            fadeLights(v)
        }

    private fun load(res: Int): Bitmap? = try {
        BitmapFactory.decodeResource(resources, res, BitmapFactory.Options().apply { inScaled = false })
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Foto de la galería a pantalla completa (o null para mostrar el carro). */
    fun setGallery(b: Bitmap?, focusX: Float = 0.5f) {
        gallery = b
        galleryFocusX = focusX
        invalidate()
    }

    fun showLightsNow(on: Boolean) {
        lightAnim?.cancel()
        lightsOn = on
        lightAnim?.cancel()
        lightLevel = if (on) 1f else 0f
        invalidate()
    }

    private fun fadeLights(on: Boolean) {
        lightAnim?.cancel()
        lightAnim = ValueAnimator.ofFloat(lightLevel, if (on) 1f else 0f).apply {
            duration = 1200
            addUpdateListener { lightLevel = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildScene()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lightAnim?.cancel()
    }

    /** Donde va el carro dentro de la tarjeta (en píxeles). */
    private fun layoutCar(w: Float, h: Float): FloatArray {
        val floor = h * 0.60f
        val cw = w - u.px(28f)
        val sc = cw / imgW
        val ch = imgH * sc
        val ct = max((h - ch) / 2f - u.px(10f), floor - g[5] * sc + u.px(12f))
        return floatArrayOf((w - cw) / 2f, ct, cw, ch, sc, floor)
    }

    /** Fondo + sombra: se dibujan una sola vez (con desenfoque real) y se guardan. */
    private fun buildScene() {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return
        val bmp = try {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return
        val c = Canvas(bmp)
        val dark = u.pal.dark
        val fw = w.toFloat()
        val fh = h.toFloat()
        val L = layoutCar(fw, fh)
        val ox = L[0]; val ct = L[1]; val cw = L[2]; val sc = L[4]; val floor = L[5]

        val st = u.pal.stage
        val wall0 = st[0]
        val wall1 = st[1]
        val fl0 = st[2]
        val fl1 = st[3]
        val p = Paint(Paint.ANTI_ALIAS_FLAG)

        // pared
        p.shader = LinearGradient(0f, 0f, 0f, fh, wall0, wall1, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, fw, fh, p)
        // piso
        p.shader = LinearGradient(0f, floor, 0f, fh, fl0, fl1, Shader.TileMode.CLAMP)
        c.drawRect(0f, floor, fw, fh, p)
        // curva infinita: une pared y piso sin línea
        val cove = u.px(45f)
        p.shader = LinearGradient(
            0f, floor - cove, 0f, floor + cove,
            intArrayOf(alpha(wall1, 0f), fl0, alpha(fl0, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        c.drawRect(0f, floor - cove, fw, floor + cove, p)
        // luz suave detrás del carro
        val ch = L[3]
        ellipseGlow(c, fw / 2f, floor - u.px(20f), fw * 0.62f, fh * 0.36f, st[4], if (dark) 0.55f else 0.85f)
        ellipseGlow(c, fw / 2f, ct + 0.374f * ch, cw * 0.58f, 0.33f * ch, if (dark) 0xFF4A4F58.toInt() else Color.WHITE, if (dark) 0.35f else 0.55f)
        // brillo del piso bajo el carro
        val y0 = ct + g[3] * sc
        p.shader = null
        p.color = if (dark) 0xFF3A3E46.toInt() else Color.WHITE
        p.alpha = ((if (dark) 0.40f else 0.5f) * 255).toInt()
        p.maskFilter = BlurMaskFilter(blur(14f), BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(fw / 2f - cw * 0.5f, y0 + u.px(6f) - u.px(18f), fw / 2f + cw * 0.5f, y0 + u.px(6f) + u.px(18f)), p)
        p.maskFilter = null

        drawShadow(c, ox, ct, sc, dark)
        scene?.recycle()
        scene = bmp
        invalidate()
    }

    private fun blur(designSigma: Float): Float = max(0.5f, u.px(designSigma) * 1.73f)

    private fun ellipseGlow(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, color: Int, a: Float) {
        if (rx <= 0f || ry <= 0f) return
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(0f, 0f, rx, alpha(color, a), alpha(color, 0f), Shader.TileMode.CLAMP)
        c.save()
        c.translate(cx, cy)
        c.scale(1f, ry / rx)
        c.drawCircle(0f, 0f, rx, p)
        c.restore()
    }

    /** Sombra: huella de las 4 llantas en el piso (difusa) + el contacto de cada llanta visible. */
    private fun drawShadow(c: Canvas, ox: Float, ct: Float, sc: Float, dark: Boolean) {
        fun P(x: Float, y: Float) = floatArrayOf(ox + x * sc, ct + y * sc)
        val k = sc * imgW / 909f / (0.47f * u.s)            // 1.0 en el tamaño del render
        val fr = floatArrayOf(g[2], g[3])
        val rr = floatArrayOf(g[4], g[5])
        val latX = g[6]
        val latY = g[7]
        val fl = floatArrayOf(fr[0] + latX, fr[1] + latY)
        val rl = floatArrayOf(rr[0] + latX * g[8], rr[1] + latY * g[8])
        val quad = listOf(P(fr[0], fr[1]), P(rr[0], rr[1]), P(rl[0], rl[1]), P(fl[0], fl[1]))
        val cx = quad.sumOf { it[0].toDouble() }.toFloat() / 4f
        val cy = quad.sumOf { it[1].toDouble() }.toFloat() / 4f
        fun poly(f: Float): Path = Path().apply {
            quad.forEachIndexed { i, q ->
                val x = cx + (q[0] - cx) * f
                val y = cy + (q[1] - cy) * f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        val o1 = if (dark) 0.70f else 0.50f
        val o2 = if (dark) 0.60f else 0.45f
        val o3 = if (dark) 0.85f else 0.62f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        p.alpha = (o1 * 255).toInt(); p.maskFilter = BlurMaskFilter(blur(9f * k), BlurMaskFilter.Blur.NORMAL)
        c.drawPath(poly(1.15f), p)
        p.alpha = (o2 * 255).toInt(); p.maskFilter = BlurMaskFilter(blur(4f * k), BlurMaskFilter.Blur.NORMAL)
        c.drawPath(poly(0.80f), p)
        val ang = Math.toDegrees(atan2((rr[1] - fr[1]).toDouble(), (rr[0] - fr[0]).toDouble())).toFloat()
        p.alpha = (o3 * 255).toInt(); p.maskFilter = BlurMaskFilter(blur(2f * k), BlurMaskFilter.Blur.NORMAL)
        val tires = mutableListOf(P(fr[0], fr[1]) to g[9], P(rr[0], rr[1]) to g[10])
        if (g[11] > 0f) tires.add(P(fl[0], fl[1]) to g[11])     // llanta delantera del otro lado (si se ve)
        for ((pt, wd) in tires) {
            c.save()
            c.rotate(ang, pt[0], pt[1])
            val rx = wd * sc
            val ry = u.px(4f) * k
            c.drawOval(RectF(pt[0] - rx, pt[1] - ry, pt[0] + rx, pt[1] + ry), p)
            c.restore()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val g = gallery
        if (g != null) {
            // Foto completa: llena la tarjeta (recorte al centro)
            val s = max(width / g.width.toFloat(), height / g.height.toFloat())
            val m = Matrix()
            m.setScale(s, s)
            val tx = (width / 2f - g.width * s * galleryFocusX).coerceIn(width - g.width * s, 0f)
            m.postTranslate(tx, (height - g.height * s) / 2f)
            canvas.drawBitmap(g, m, bmpPaint)
            return
        }
        scene?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val b = car ?: return
        val L = layoutCar(width.toFloat(), height.toFloat())
        carDst.set(L[0], L[1], L[0] + L[2], L[1] + L[3])
        canvas.drawBitmap(b, null, carDst, bmpPaint)
        val lb = lights
        if (lb != null && lightLevel > 0.01f) {
            lightPaint.alpha = (lightLevel * 255).toInt().coerceIn(0, 255)
            canvas.drawBitmap(lb, Rect(0, 0, lb.width, lb.height), carDst, lightPaint)
        }
    }
}
