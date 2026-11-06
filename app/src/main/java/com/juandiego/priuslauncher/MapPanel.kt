package com.juandiego.priuslauncher

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * El mapa de la pantalla de inicio.
 * Usa Google Maps en vivo si la app tiene clave de Google y el equipo tiene Google Play;
 * si no, usa el mapa de respaldo (OpenStreetMap) para que nunca quede vacío.
 */
class MapPanel(
    private val act: Activity,
    private val container: FrameLayout,
    private val sport: Boolean,
    private val day: Boolean,
    accent: Int,
    private val onTap: () -> Unit
) {
    companion object {
        fun googleAvailable(act: Activity): Boolean =
            BuildConfig.MAPS_API_KEY.isNotBlank() && try {
                GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(act) == ConnectionResult.SUCCESS
            } catch (e: Exception) {
                false
            }
    }

    var accent: Int = accent
        set(v) {
            field = v
            live?.accent = v
            applyGoogleStyle()
        }

    val usingGoogle = googleAvailable(act)
    private var mapView: MapView? = null
    private var gmap: GoogleMap? = null
    private var marker: Marker? = null
    private var live: LiveMapView? = null
    private var lastPos: LatLng? = null
    private var lastBearing = 0f
    private var lastMove = 0L

    fun create(state: Bundle?) {
        val lp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (usingGoogle) {
            try {
                val mv = MapView(act)
                mv.onCreate(state?.getBundle("map"))
                container.addView(mv, lp)
                mapView = mv
                mv.getMapAsync { m -> setup(m) }
                return
            } catch (e: Exception) {
                mapView = null
            }
        }
        val lv = LiveMapView(act)
        lv.accent = accent
        lv.setOnClickListener { onTap() }
        container.addView(lv, lp)
        live = lv
    }

    private fun setup(m: GoogleMap) {
        gmap = m
        m.uiSettings.setAllGesturesEnabled(false)
        m.uiSettings.isMapToolbarEnabled = false
        m.uiSettings.isCompassEnabled = false
        m.uiSettings.isZoomControlsEnabled = false
        m.uiSettings.isIndoorLevelPickerEnabled = false
        m.isBuildingsEnabled = !sport
        m.isTrafficEnabled = false
        m.setOnMapClickListener { onTap() }
        m.setOnMarkerClickListener { onTap(); true }
        applyGoogleStyle()
        lastPos?.let { moveTo(it, lastBearing, false) }
    }

    private fun applyGoogleStyle() {
        val m = gmap ?: return
        try {
            val json = when {
                sport -> sportStyle(accent)
                day -> DAY_STYLE
                else -> NIGHT_STYLE
            }
            m.setMapStyle(MapStyleOptions(json))
        } catch (e: Exception) {
            // estilo no válido: se queda el normal
        }
        marker?.setIcon(BitmapDescriptorFactory.fromBitmap(arrow(accent)))
    }

    fun setLocation(lat: Double, lon: Double, bearing: Float?) {
        live?.setLocation(lat, lon, bearing)
        val pos = LatLng(lat, lon)
        if (bearing != null) lastBearing = bearing
        val first = lastPos == null
        lastPos = pos
        val now = SystemClock.elapsedRealtime()
        if (!first && now - lastMove < 1500) {
            marker?.position = pos
            return
        }
        lastMove = now
        moveTo(pos, lastBearing, !first)
    }

    private fun moveTo(pos: LatLng, bearing: Float, animate: Boolean) {
        val m = gmap ?: return
        val cam = CameraPosition.Builder().target(pos).zoom(if (sport) 15.5f else 16.5f)
            .bearing(bearing).tilt(if (sport) 0f else 35f).build()
        if (animate) m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 1200, null)
        else m.moveCamera(CameraUpdateFactory.newCameraPosition(cam))
        val mk = marker
        if (mk == null) {
            marker = m.addMarker(
                MarkerOptions().position(pos).flat(true).anchor(0.5f, 0.5f)
                    .icon(BitmapDescriptorFactory.fromBitmap(arrow(accent)))
            )
        } else {
            mk.position = pos
        }
        marker?.rotation = 0f
    }

    /** Flecha de tu posición, del color del tema. */
    private fun arrow(color: Int): Bitmap {
        val d = act.resources.displayMetrics.density
        val s = (34 * d).toInt()
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = (color and 0x00FFFFFF) or 0x40000000
        c.drawCircle(s / 2f, s / 2f, s / 2f, p)
        val path = Path().apply {
            moveTo(s / 2f, s * 0.18f)
            lineTo(s * 0.74f, s * 0.78f)
            lineTo(s / 2f, s * 0.64f)
            lineTo(s * 0.26f, s * 0.78f)
            close()
        }
        p.color = Color.WHITE
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * d
        p.strokeJoin = Paint.Join.ROUND
        c.drawPath(path, p)
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
        return b
    }

    // ----- ciclo de vida del MapView de Google -----
    fun onStart() = mapView?.onStart()
    fun onResume() = mapView?.onResume()
    fun onPause() = mapView?.onPause()
    fun onStop() = mapView?.onStop()
    fun onDestroy() = mapView?.onDestroy()
    fun onLowMemory() = mapView?.onLowMemory()
    fun onSave(out: Bundle) {
        val b = Bundle()
        mapView?.onSaveInstanceState(b)
        out.putBundle("map", b)
    }
}

private fun hex(c: Int) = String.format("#%06X", c and 0xFFFFFF)
private fun scale(c: Int, f: Float): Int {
    val r = (Color.red(c) * f).toInt().coerceIn(0, 255)
    val g = (Color.green(c) * f).toInt().coerceIn(0, 255)
    val b = (Color.blue(c) * f).toInt().coerceIn(0, 255)
    return Color.rgb(r, g, b)
}

/** Mapa negro con calles y nombres del color del tema (estilo Sport). */
private fun sportStyle(accent: Int): String {
    val road = hex(scale(accent, 0.28f))
    val high = hex(scale(accent, 0.5f))
    val label = hex(scale(accent, 0.7f))
    return """[
 {"elementType":"geometry","stylers":[{"color":"#050505"}]},
 {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"$label"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#000000"}]},
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"administrative","elementType":"geometry","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"$road"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"$high"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0A0A0A"}]},
 {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#080808"}]}
]"""
}

private const val NIGHT_STYLE = """[
 {"elementType":"geometry","stylers":[{"color":"#1b1d21"}]},
 {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
 {"elementType":"labels.text.fill","stylers":[{"color":"#8a8f98"}]},
 {"elementType":"labels.text.stroke","stylers":[{"color":"#121418"}]},
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]},
 {"featureType":"road","elementType":"geometry","stylers":[{"color":"#30343c"}]},
 {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#4a4f58"}]},
 {"featureType":"water","elementType":"geometry","stylers":[{"color":"#0e1824"}]},
 {"featureType":"landscape.natural","elementType":"geometry","stylers":[{"color":"#1d2620"}]}
]"""

private const val DAY_STYLE = """[
 {"featureType":"poi","stylers":[{"visibility":"off"}]},
 {"featureType":"transit","stylers":[{"visibility":"off"}]}
]"""
