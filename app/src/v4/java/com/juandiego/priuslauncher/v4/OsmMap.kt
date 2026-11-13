package com.juandiego.priuslauncher.v4

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.View
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Cuadritos de OpenStreetMap (sin clave). Se guardan en la memoria del launcher
 * para que el mapa se vea aunque se pierda el internet.
 */
object OsmTiles {
    private const val URL_FMT = "https://tile.openstreetmap.org/%d/%d/%d.png"
    private const val MAX_DISK_FILES = 800
    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private val failedAt = Collections.synchronizedMap(HashMap<String, Long>())
    private val pool = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private var writes = 0

    fun get(ctx: Context, z: Int, x: Int, y: Int, onReady: () -> Unit): Bitmap? {
        val k = "${z}_${x}_$y"
        memory.get(k)?.let { return it }
        val last = failedAt[k]
        if (last != null && System.currentTimeMillis() - last < 30_000) return null
        if (!pending.add(k)) return null
        val dir = File(ctx.cacheDir, "tiles_osm")
        pool.execute {
            var bmp: Bitmap? = null
            try {
                dir.mkdirs()
                val f = File(dir, "$k.png")
                if (f.exists()) bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp == null) {
                    val c = URL(String.format(URL_FMT, z, x, y)).openConnection() as HttpURLConnection
                    val bytes = try {
                        c.connectTimeout = 8000
                        c.readTimeout = 8000
                        c.setRequestProperty("User-Agent", "CarGO/4.0 (car head unit launcher)")
                        if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
                    } finally {
                        c.disconnect()
                    }
                    if (bytes != null) {
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            f.writeBytes(bytes)
                            if (++writes % 50 == 0) prune(dir)
                        }
                    }
                }
            } catch (e: Exception) {
                bmp = null
            } catch (e: OutOfMemoryError) {
                bmp = null
            }
            val result = bmp
            if (result != null) {
                memory.put(k, result)
                failedAt.remove(k)
            } else {
                failedAt[k] = System.currentTimeMillis()
            }
            pending.remove(k)
            if (result != null) main.post(onReady)
        }
        return null
    }

    private fun prune(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_DISK_FILES).forEach { it.delete() }
    }
}

/**
 * Mapa en vivo: gira con el carro (el camino siempre hacia adelante), se mueve suave entre cada lectura
 * del GPS (60 cuadros por segundo) y acerca o aleja según la velocidad. Calles reales de OpenStreetMap,
 * con colores apagados para que combine con el diseño (claro de día, oscuro de noche).
 */
class OsmMapView(private val u: U) : View(u.ctx) {
    private companion object {
        const val GLIDE_MS = 1100f          // tiempo para ir de una lectura del GPS a la siguiente
    }

    // posición mostrada (se acerca suave a la última del GPS)
    private var lat = Double.NaN
    private var lon = Double.NaN
    private var rot = 0f                    // rumbo mostrado (grados)
    private var zoom = 17f
    // de dónde a dónde va la animación
    private var fromLat = 0.0; private var fromLon = 0.0; private var toLat = 0.0; private var toLon = 0.0
    private var fromRot = 0f; private var toRot = 0f
    private var fromZoom = 17f; private var toZoom = 17f
    private var animStart = 0L
    private var hasHeading = false

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(u.pal.dark)) }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE; strokeJoin = Paint.Join.ROUND }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(u.pal.accent, 0.13f) }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30000000 }
    private val attrib = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = u.pal.faint; typeface = Fonts.w500(u.ctx) }
    private val dst = RectF()
    private val arrow = Path()
    private val redraw: () -> Unit = { invalidate() }
    private val bg = u.pal.card

    /** Avisa el rumbo mostrado en cada cuadro (para la brújula). */
    var onBearing: ((Float) -> Unit)? = null

    /** Día: casi sin color. Noche: invertido y apagado (calles claras sobre fondo oscuro). */
    private fun matrix(dark: Boolean): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(if (dark) 0.10f else 0.18f)
        if (dark) {
            m.postConcat(ColorMatrix(floatArrayOf(
                -0.72f, 0f, 0f, 0f, 196f,
                0f, -0.72f, 0f, 0f, 198f,
                0f, 0f, -0.72f, 0f, 202f,
                0f, 0f, 0f, 1f, 0f
            )))
        } else {
            m.postConcat(ColorMatrix(floatArrayOf(
                0.96f, 0f, 0f, 0f, 8f,
                0f, 0.96f, 0f, 0f, 9f,
                0f, 0f, 0.96f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        return m
    }

    /** Nueva lectura del GPS. speed en m/s. */
    fun setLocation(latitude: Double, longitude: Double, bearingDeg: Float?, speedMps: Float = 0f) {
        val now = android.os.SystemClock.uptimeMillis()
        if (lat.isNaN()) {
            lat = latitude; lon = longitude
            fromLat = latitude; fromLon = longitude; toLat = latitude; toLon = longitude
            if (bearingDeg != null) { rot = bearingDeg; fromRot = rot; toRot = rot; hasHeading = true }
            animStart = now
            invalidate()
            return
        }
        stepAnimation(now)
        fromLat = lat; fromLon = lon; toLat = latitude; toLon = longitude
        fromRot = rot
        if (bearingDeg != null) {
            // el giro más corto (de 350° a 10° son 20°, no 340°)
            var d = (bearingDeg - rot) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            toRot = rot + d
            hasHeading = true
        } else {
            toRot = rot
        }
        // zoom por velocidad: calle (17) parado o despacio, 16 en avenida, 15 en freeway
        val mph = speedMps * 2.23694f
        val z = when { mph < 25f -> 17f; mph < 50f -> 16.2f; else -> 15.4f }
        fromZoom = zoom; toZoom = z
        animStart = now
        postInvalidateOnAnimation()
    }

    private fun stepAnimation(now: Long): Boolean {
        val t = ((now - animStart) / GLIDE_MS).coerceIn(0f, 1f)
        val e = t * t * (3f - 2f * t)           // suave al empezar y al llegar
        lat = fromLat + (toLat - fromLat) * t   // posición lineal: velocidad constante, sin "tirones"
        lon = fromLon + (toLon - fromLon) * t
        rot = fromRot + (toRot - fromRot) * e
        zoom = fromZoom + (toZoom - fromZoom) * e
        return t < 1f
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        if (lat.isNaN()) return
        val moving = stepAnimation(android.os.SystemClock.uptimeMillis())
        val zi = floor(zoom).toInt().coerceIn(3, 18)
        val scale = Math.pow(2.0, (zoom - zi).toDouble()).toFloat()
        val ts = 256f * u.s * 1.1f * scale
        val n = 1 shl zi
        val latR = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val worldX = (lon + 180.0) / 360.0 * n * ts
        val worldY = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n * ts
        val cx = width / 2f
        val cy = height * 0.72f
        // radio que hay que cubrir al girar (la esquina más lejana desde la flecha)
        val r = Math.hypot(maxOf(cx, width - cx).toDouble(), maxOf(cy, height - cy).toDouble())
        canvas.save()
        canvas.rotate(-rot, cx, cy)
        val left = worldX - r
        val top = worldY - r
        var shown = 0
        for (ty in floor(top / ts).toInt()..floor((worldY + r) / ts).toInt()) {
            if (ty < 0 || ty >= n) continue
            for (tx in floor(left / ts).toInt()..floor((worldX + r) / ts).toInt()) {
                val wx = ((tx % n) + n) % n
                val bmp = OsmTiles.get(context, zi, wx, ty, redraw) ?: continue
                val x = (tx * ts - worldX + cx).toFloat()
                val y = (ty * ts - worldY + cy).toFloat()
                dst.set(x, y, x + ts, y + ts)
                canvas.drawBitmap(bmp, null, dst, tilePaint)
                shown++
            }
        }
        canvas.restore()

        // flecha del carro: siempre hacia arriba (el mapa es el que gira)
        canvas.drawCircle(cx, cy, u.px(38f), haloPaint)
        canvas.drawOval(cx - u.px(22f), cy + u.px(-4f), cx + u.px(22f), cy + u.px(12f), shadowPaint)
        arrow.reset()
        arrow.moveTo(cx, cy - u.px(19f)); arrow.lineTo(cx + u.px(14f), cy + u.px(14f))
        arrow.lineTo(cx, cy + u.px(7f)); arrow.lineTo(cx - u.px(14f), cy + u.px(14f)); arrow.close()
        if (!hasHeading) {
            // sin rumbo todavía: punto en lugar de flecha
            arrowPaint.color = Color.WHITE
            canvas.drawCircle(cx, cy, u.px(15f), arrowPaint)
            arrowPaint.color = u.pal.accent
            canvas.drawCircle(cx, cy, u.px(11f), arrowPaint)
        } else {
            arrowPaint.color = u.pal.accent
            canvas.drawPath(arrow, arrowPaint)
            strokePaint.strokeWidth = u.px(3f)
            canvas.drawPath(arrow, strokePaint)
        }
        if (shown > 0) {
            attrib.textSize = u.px(8f)
            canvas.drawText("© OpenStreetMap", u.px(10f), height - u.px(3f), attrib)
        }
        onBearing?.invoke(rot)
        if (moving) postInvalidateOnAnimation()
    }
}
