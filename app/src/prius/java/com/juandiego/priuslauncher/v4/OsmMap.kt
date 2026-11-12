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
 * Mapa en vivo de respaldo (cuando la pantalla no tiene Google Play):
 * colores apagados para que combine con el diseño minimalista (claro de día, oscuro de noche).
 */
class OsmMapView(private val u: U) : View(u.ctx) {
    private companion object {
        const val ZOOM = 16
    }

    private var lat = Double.NaN
    private var lon = Double.NaN
    private var bearing = Float.NaN
    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix(u.pal.dark)) }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(u.pal.accent, 0.16f) }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val attrib = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = u.pal.faint; typeface = Fonts.w500(u.ctx) }
    private val dst = RectF()
    private val arrow = Path()
    private val redraw: () -> Unit = { invalidate() }
    private val bg = if (u.pal.dark) 0xFF17181B.toInt() else 0xFFE9EBEC.toInt()

    /** Día: casi sin color. Noche: invertido y apagado (calles claras sobre fondo oscuro). */
    private fun matrix(dark: Boolean): ColorMatrix {
        val m = ColorMatrix()
        m.setSaturation(if (dark) 0.10f else 0.18f)
        if (dark) {
            val inv = ColorMatrix(floatArrayOf(
                -0.72f, 0f, 0f, 0f, 196f,
                0f, -0.72f, 0f, 0f, 198f,
                0f, 0f, -0.72f, 0f, 202f,
                0f, 0f, 0f, 1f, 0f
            ))
            m.postConcat(inv)
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

    fun setLocation(latitude: Double, longitude: Double, bearingDeg: Float?) {
        lat = latitude
        lon = longitude
        if (bearingDeg != null) bearing = bearingDeg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(bg)
        if (lat.isNaN()) return
        val ts = 256f * u.s * 1.1f
        val n = 1 shl ZOOM
        val latR = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val worldX = (lon + 180.0) / 360.0 * n * ts
        val worldY = (1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n * ts
        val cyF = 0.56f
        val left = worldX - width / 2.0
        val top = worldY - height * cyF
        var shown = 0
        for (ty in floor(top / ts).toInt()..floor((top + height) / ts).toInt()) {
            if (ty < 0 || ty >= n) continue
            for (tx in floor(left / ts).toInt()..floor((left + width) / ts).toInt()) {
                val wx = ((tx % n) + n) % n
                val bmp = OsmTiles.get(context, ZOOM, wx, ty, redraw) ?: continue
                val x = (tx * ts - left).toFloat()
                val y = (ty * ts - top).toFloat()
                dst.set(x, y, x + ts, y + ts)
                canvas.drawBitmap(bmp, null, dst, tilePaint)
                shown++
            }
        }
        // tu posición (como en el render: halo, círculo blanco y flecha azul)
        val cx = width / 2f
        val cy = height * cyF
        canvas.drawCircle(cx, cy, u.px(34f), haloPaint)
        canvas.drawCircle(cx, cy, u.px(16f), ringPaint)
        arrowPaint.color = u.pal.accent
        canvas.drawCircle(cx, cy, u.px(12f), arrowPaint)
        canvas.save()
        canvas.translate(cx, cy)
        if (!bearing.isNaN()) canvas.rotate(bearing)
        arrow.reset()
        arrow.moveTo(0f, -u.px(8f)); arrow.lineTo(u.px(6f), u.px(7f)); arrow.lineTo(0f, u.px(4f)); arrow.lineTo(-u.px(6f), u.px(7f)); arrow.close()
        arrowPaint.color = Color.WHITE
        canvas.drawPath(arrow, arrowPaint)
        canvas.restore()
        if (shown > 0) {
            attrib.textSize = u.px(8f)
            canvas.drawText("© OpenStreetMap", u.px(10f), height - u.px(3f), attrib)
        }
    }
}
