package com.juandiego.priuslauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.LruCache
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
 * Descarga y guarda los cuadritos del mapa (mapa oscuro de CARTO, datos de OpenStreetMap).
 * Se guardan en la memoria del launcher para que se vean aunque se pierda el internet.
 */
object TileCache {
    private const val URL_FMT = "https://a.basemaps.cartocdn.com/%s/%d/%d/%d@2x.png"
    private const val MAX_DISK_FILES = 600

    private val memory = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pending = Collections.synchronizedSet(HashSet<String>())
    private val failedAt = Collections.synchronizedMap(HashMap<String, Long>())
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private var writes = 0

    private fun key(style: String, z: Int, x: Int, y: Int) = "${style}_${z}_${x}_$y"

    /** Devuelve el cuadrito si ya está listo; si no, lo pide y avisa con onReady cuando llegue. */
    fun get(ctx: Context, style: String, z: Int, x: Int, y: Int, onReady: () -> Unit): Bitmap? {
        val k = key(style, z, x, y)
        memory.get(k)?.let { return it }
        val last = failedAt[k]
        if (last != null && System.currentTimeMillis() - last < 30_000) return null
        if (!pending.add(k)) return null
        val dir = File(ctx.cacheDir, "tiles")
        pool.execute {
            var bmp: Bitmap? = null
            try {
                dir.mkdirs()
                val f = File(dir, "$k.png")
                if (f.exists()) bmp = BitmapFactory.decodeFile(f.absolutePath)
                if (bmp == null) {
                    val bytes = download(String.format(URL_FMT, style, z, x, y))
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

    private fun download(url: String): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "PriusLauncher/1.0 (personal car head unit)")
            if (c.responseCode == 200) c.inputStream.use { it.readBytes() } else null
        } finally {
            c.disconnect()
        }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles() ?: return
        if (files.size <= MAX_DISK_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_DISK_FILES).forEach { it.delete() }
    }
}

/**
 * Mapa en vivo: sigue tu ubicación con una flecha del color elegido.
 * Mientras no hay ubicación muestra el dibujo de mapa de siempre.
 */
class LiveMapView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : MapTileView(ctx, attrs) {

    private companion object {
        const val ZOOM = 16
    }

    private var lat = Double.NaN
    private var lon = Double.NaN
    private var bearing = Float.NaN
    private var tilesShown = 0

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val attribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.getColor(R.color.map_attrib) }
    private val style = if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_NO) "light_all" else "dark_all"
    private val dst = RectF()
    private val arrow = Path()
    private val redraw: () -> Unit = { invalidate() }

    val hasLocation get() = !lat.isNaN()

    fun setLocation(latitude: Double, longitude: Double, bearingDeg: Float?) {
        lat = latitude
        lon = longitude
        if (bearingDeg != null) bearing = bearingDeg
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (lat.isNaN()) {
            super.onDraw(canvas)
            return
        }
        val d = resources.displayMetrics.density
        val ts = 256f * d
        val n = 1 shl ZOOM
        val latR = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        val worldX = ((lon + 180.0) / 360.0 * n * ts)
        val worldY = ((1.0 - ln(tan(latR) + 1.0 / cos(latR)) / PI) / 2.0 * n * ts)
        val left = worldX - width / 2.0
        val top = worldY - height * 0.55

        canvas.drawColor(mapBg)
        tilesShown = 0
        val tx0 = floor(left / ts).toInt()
        val tx1 = floor((left + width) / ts).toInt()
        val ty0 = floor(top / ts).toInt()
        val ty1 = floor((top + height) / ts).toInt()
        for (ty in ty0..ty1) {
            if (ty < 0 || ty >= n) continue
            for (tx in tx0..tx1) {
                val wx = ((tx % n) + n) % n
                val bmp = TileCache.get(context, style, ZOOM, wx, ty, redraw) ?: continue
                val x = (tx * ts - left).toFloat()
                val y = (ty * ts - top).toFloat()
                dst.set(x, y, x + ts, y + ts)
                canvas.drawBitmap(bmp, null, dst, tilePaint)
                tilesShown++
            }
        }

        // Tu posición
        val cx = width / 2f
        val cy = height * 0.55f
        haloPaint.color = (accent and 0x00FFFFFF) or 0x33000000
        canvas.drawCircle(cx, cy, 18f * d, haloPaint)
        canvas.save()
        canvas.translate(cx, cy)
        if (!bearing.isNaN()) canvas.rotate(bearing)
        arrow.reset()
        arrow.moveTo(0f, -11f * d)
        arrow.lineTo(8f * d, 9f * d)
        arrow.lineTo(0f, 4.5f * d)
        arrow.lineTo(-8f * d, 9f * d)
        arrow.close()
        arrowPaint.color = Color.WHITE
        arrowPaint.style = Paint.Style.STROKE
        arrowPaint.strokeWidth = 3f * d
        arrowPaint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(arrow, arrowPaint)
        arrowPaint.color = accent
        arrowPaint.style = Paint.Style.FILL
        canvas.drawPath(arrow, arrowPaint)
        canvas.restore()

        if (tilesShown > 0) {
            attribPaint.textSize = 8f * d
            canvas.drawText("© OpenStreetMap © CARTO", 8f * d, height - 6f * d, attribPaint)
        }
    }
}
