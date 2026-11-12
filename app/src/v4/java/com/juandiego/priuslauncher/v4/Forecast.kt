package com.juandiego.priuslauncher.v4

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Pronóstico de los próximos días (Open-Meteo, sin clave). Se guarda para verlo sin internet. */
object Forecast {
    class Day(val date: String, val maxC: Double, val minC: Double, val code: Int)

    private const val PREFS = "v4_forecast"
    private const val EVERY_MS = 60 * 60 * 1000L

    @Volatile var days: List<Day> = emptyList()
        private set
    private var lastFetch = 0L
    @Volatile private var busy = false
    private var loaded = false
    private val main = Handler(Looper.getMainLooper())
    var onChange: (() -> Unit)? = null

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        days = decode(p.getString("days", null))
        lastFetch = p.getLong("time", 0L)
    }

    fun update(c: Context, loc: Location) {
        val app = c.applicationContext
        load(app)
        if (busy || System.currentTimeMillis() - lastFetch < EVERY_MS) return
        busy = true
        val lat = loc.latitude
        val lon = loc.longitude
        Thread {
            try {
                val url = String.format(
                    Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                        "&daily=temperature_2m_max,temperature_2m_min,weather_code&forecast_days=6&timezone=auto",
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
                    val d = JSONObject(body).getJSONObject("daily")
                    val t = d.getJSONArray("time")
                    val mx = d.getJSONArray("temperature_2m_max")
                    val mn = d.getJSONArray("temperature_2m_min")
                    val cd = d.getJSONArray("weather_code")
                    val list = ArrayList<Day>()
                    for (i in 1 until t.length()) list.add(Day(t.getString(i), mx.getDouble(i), mn.getDouble(i), cd.getInt(i)))
                    days = list
                    lastFetch = System.currentTimeMillis()
                    app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                        .putString("days", encode(list)).putLong("time", lastFetch).apply()
                    main.post { onChange?.invoke() }
                }
            } catch (e: Exception) {
                // sin internet: se queda el último pronóstico guardado
            } finally {
                busy = false
            }
        }.start()
    }

    private fun encode(l: List<Day>) = l.joinToString(";") { "${it.date},${it.maxC},${it.minC},${it.code}" }

    private fun decode(s: String?): List<Day> = try {
        if (s.isNullOrBlank()) emptyList() else s.split(";").map {
            val p = it.split(",")
            Day(p[0], p[1].toDouble(), p[2].toDouble(), p[3].toInt())
        }
    } catch (e: Exception) {
        emptyList()
    }
}
