package com.cargo.launcher

import android.graphics.Color

/** Colores de un tema. Se aplican en código, sin recrear la pantalla. */
data class Palette(
    val bg: Int, val panel: Int, val card: Int, val stroke: Int,
    val text: Int, val muted: Int, val accent: Int, val accent2: Int,
    val roadGrid: Int, val roadLane: Int, val roadDash: Int, val roadGlow: Boolean
)

/** Qué dibujo y qué colores usa cada vehículo según tema y vista. */
object VehicleAssets {

    fun carDrawable(v: Vehicle, theme: ThemeMode, view: CarView): Int = when (v) {
        Vehicle.PRIUS_2011 -> when (view) {
            CarView.XRAY -> R.drawable.prius_xray
            CarView.WIREFRAME -> R.drawable.prius_wire
            CarView.CLEAN, CarView.HUD_ROAD -> R.drawable.prius_clean
        }
        Vehicle.JEEP_SAHARA_2021 -> when (theme) {
            ThemeMode.XMAS -> R.drawable.jeep_white
            ThemeMode.DAY, ThemeMode.NIGHT -> R.drawable.jeep_cherry  // gráfico "Cherry & Vine" en las puertas
        }
    }

    /** Vistas que tiene cada vehículo (el Jeep cambia por tema, no por vista). */
    fun views(v: Vehicle): List<CarView> = when (v) {
        Vehicle.PRIUS_2011 -> listOf(CarView.CLEAN, CarView.XRAY, CarView.HUD_ROAD, CarView.WIREFRAME)
        Vehicle.JEEP_SAHARA_2021 -> listOf(CarView.CLEAN, CarView.HUD_ROAD)
    }

    fun showRoad(v: Vehicle, view: CarView): Boolean =
        view == CarView.HUD_ROAD || (v == Vehicle.PRIUS_2011 && view == CarView.WIREFRAME)

    /** Punto del piso bajo la llanta delantera (fracción del dibujo): de ahí sale la carretera. */
    fun roadAnchor(v: Vehicle): FloatArray = when (v) {
        Vehicle.PRIUS_2011 -> floatArrayOf(0.484f, 0.982f)
        Vehicle.JEEP_SAHARA_2021 -> floatArrayOf(0.429f, 0.989f)
    }

    fun palette(v: Vehicle, t: ThemeMode): Palette = when {
        v == Vehicle.JEEP_SAHARA_2021 && t == ThemeMode.DAY -> Palette(   // Sunset Rose Gold
            bg = c("#F8EEE8"), panel = c("#FFF9F6"), card = c("#FFFAF7"), stroke = c("#E8CFC6"),
            text = c("#4A1A28"), muted = c("#9C7480"), accent = c("#B3123A"), accent2 = c("#C98B7B"),
            roadGrid = c("#ECD4CD"), roadLane = c("#C98B7B"), roadDash = c("#B3123A"), roadGlow = false
        )
        v == Vehicle.JEEP_SAHARA_2021 && t == ThemeMode.NIGHT -> Palette( // Midnight Rose & Cherry
            bg = c("#05060C"), panel = c("#0E1326"), card = c("#141A30"), stroke = c("#3A2E3A"),
            text = c("#F6E9E4"), muted = c("#A48E95"), accent = c("#E0244E"), accent2 = c("#E8A598"),
            roadGrid = c("#262D50"), roadLane = c("#E8A598"), roadDash = c("#E0244E"), roadGlow = true
        )
        t == ThemeMode.XMAS -> Palette(                                     // Navidad rosa
            bg = c("#2A1220"), panel = c("#33202B"), card = c("#3A2532"), stroke = c("#5A3A4A"),
            text = c("#FFF1F6"), muted = c("#C79AB0"), accent = c("#FF6FAE"), accent2 = c("#FFB3D1"),
            roadGrid = c("#782D6E"), roadLane = c("#FF6FAE"), roadDash = c("#F02850"), roadGlow = true
        )
        t == ThemeMode.DAY -> Palette(                                      // Prius día (neumórfico)
            bg = c("#E3E6ED"), panel = c("#E3E6ED"), card = c("#ECEFF4"), stroke = c("#D3D8E1"),
            text = c("#292C33"), muted = c("#6E7480"), accent = c("#C7F063"), accent2 = c("#2B7BFF"),
            roadGrid = c("#C8D5E6"), roadLane = c("#2B8CF5"), roadDash = c("#3CC878"), roadGlow = false
        )
        else -> Palette(                                                    // Prius noche / HUD
            bg = c("#000000"), panel = c("#12151A"), card = c("#1C1F24"), stroke = c("#2A2E35"),
            text = c("#FFFFFF"), muted = c("#9AA0A8"), accent = c("#3E9BFF"), accent2 = c("#34C759"),
            roadGrid = c("#1E558C"), roadLane = c("#2896FF"), roadDash = c("#3CFF78"), roadGlow = true
        )
    }

    private fun c(hex: String) = Color.parseColor(hex)
}
