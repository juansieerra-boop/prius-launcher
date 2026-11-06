package com.juandiego.priuslauncher

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.roundToInt

/**
 * Clima actual de Open-Meteo (gratis, sin clave). Se actualiza cada 15 minutos
 * o si te mueves más de 10 km. Se guarda para mostrarlo aunque no haya internet.
 */
object Weather {
    class Now(val tempC: Double, val code: Int, val isDay: Boolean, val city: String?)

    private const val PREFS = "weather"
    private const val EVERY_MS = 15 * 60 * 1000L

    @Volatile var now: Now? = null
        private set
    private var lastFetch = 0L
    private var lastLoc: Location? = null
    @Volatile private var busy = false
    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    fun load(c: Context) {
        if (now != null) return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("t")) return
        now = Now(p.getFloat("t", 0f).toDouble(), p.getInt("code", 0), p.getBoolean("day", true), p.getString("city", null))
        lastFetch = p.getLong("time", 0L)
    }

    fun update(c: Context, loc: Location) {
        val app = c.applicationContext
        load(app)
        val moved = lastLoc?.distanceTo(loc)?.let { it > 10_000 } ?: true
        if (busy || (!moved && System.currentTimeMillis() - lastFetch < EVERY_MS)) return
        busy = true
        val lat = loc.latitude
        val lon = loc.longitude
        Thread {
            try {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code,is_day",
                    lat, lon
                )
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                val body = try {
                    if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
                } finally {
                    conn.disconnect()
                }
                if (body != null) {
                    val cur = JSONObject(body).getJSONObject("current")
                    val city = try {
                        @Suppress("DEPRECATION")
                        Geocoder(app, Locale(Prefs.lang(app))).getFromLocation(lat, lon, 1)
                            ?.firstOrNull()?.let { it.locality ?: it.subAdminArea }
                    } catch (e: Exception) {
                        null
                    }
                    val n = Now(cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1, city ?: now?.city)
                    now = n
                    lastFetch = System.currentTimeMillis()
                    lastLoc = loc
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putFloat("t", n.tempC.toFloat()).putInt("code", n.code).putBoolean("day", n.isDay)
                        .putString("city", n.city).putLong("time", lastFetch).apply()
                    main.post { listeners.forEach { it() } }
                }
            } catch (e: Exception) {
                // sin internet: se queda el último clima guardado
            } finally {
                busy = false
            }
        }.start()
    }

    fun tempText(c: Context, n: Now): String {
        val t = if (Prefs.useKmh(c)) n.tempC else n.tempC * 9.0 / 5.0 + 32.0
        return "${t.roundToInt()}°"
    }

    fun descRes(code: Int): Int = when (code) {
        0 -> R.string.weather_clear
        1, 2 -> R.string.weather_partly
        3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_fog
        in 51..57 -> R.string.weather_drizzle
        in 61..67 -> R.string.weather_rain
        in 71..77, 85, 86 -> R.string.weather_snow
        in 80..82 -> R.string.weather_showers
        in 95..99 -> R.string.weather_storm
        else -> R.string.weather_cloudy
    }

    fun iconRes(n: Now): Int = when (n.code) {
        0, 1 -> if (n.isDay) R.drawable.ic_sun else R.drawable.ic_moon
        in 51..67, in 80..82 -> R.drawable.ic_w_rain
        in 95..99 -> R.drawable.ic_w_storm
        else -> R.drawable.ic_w_cloud
    }
}

/** Datos del viaje para Sport: distancia, tiempo en movimiento y velocidad máxima. */
object Trip {
    private const val PREFS = "trip"
    private const val RESET_AFTER_MS = 45 * 60 * 1000L

    var distanceM = 0.0
        private set
    var movingMs = 0L
        private set
    var maxMps = 0f
        private set
    private var lastMove = 0L
    private var last: Location? = null
    private var lastFix = 0L
    private var loaded = false
    private var unsaved = 0

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        distanceM = p.getFloat("d", 0f).toDouble()
        movingMs = p.getLong("t", 0L)
        maxMps = p.getFloat("max", 0f)
        lastMove = p.getLong("last", 0L)
    }

    fun onLocation(c: Context, loc: Location) {
        load(c)
        val now = System.currentTimeMillis()
        val speed = if (loc.hasSpeed()) loc.speed else 0f
        if (speed > 2f && lastMove > 0 && now - lastMove > RESET_AFTER_MS) {
            distanceM = 0.0; movingMs = 0L; maxMps = 0f
        }
        val prev = last
        if (prev != null && loc.accuracy < 35f) {
            val dt = now - lastFix
            if (dt in 1..10_000 && speed > 1f) {
                distanceM += prev.distanceTo(loc)
                movingMs += dt
                lastMove = now
            }
        }
        if (speed > maxMps && loc.accuracy < 35f) maxMps = speed
        last = loc
        lastFix = now
        if (++unsaved >= 10) save(c)
    }

    fun save(c: Context) {
        unsaved = 0
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("d", distanceM.toFloat()).putLong("t", movingMs).putFloat("max", maxMps).putLong("last", lastMove).apply()
    }
}
