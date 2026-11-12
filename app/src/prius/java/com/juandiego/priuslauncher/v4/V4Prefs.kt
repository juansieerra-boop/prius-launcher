package com.juandiego.priuslauncher.v4

import android.content.Context
import android.net.Uri
import com.juandiego.priuslauncher.Prefs
import java.util.Calendar

/** Toda la configuración de la pantalla v4 (se guarda con el botón Guardar de Ajustes). */
object V4Prefs {
    private const val NAME = "v4"

    const val PANEL_CAR = "car"
    const val PANEL_GALLERY = "gallery"
    const val PANEL_WIDGET = "widget"

    const val AUTO = "auto"
    const val ALWAYS = "always"
    const val OFF = "off"
    const val LIGHT = "light"
    const val DARK = "dark"

    /** Widgets que se pueden poner en los espacios. */
    const val W_MAP = "map"
    const val W_CAR = "car"
    const val W_WEATHER = "wx"
    const val W_SWAP = "swap"
    val DEFAULT_SLOTS = listOf(W_MAP, W_CAR, W_WEATHER, W_SWAP)

    /** Opciones de la sección intercambiable. */
    const val K_SPEED = "vel"
    const val K_APPS = "apps"
    const val K_CLOCK = "reloj"
    const val K_LANG = "idioma"
    const val K_APPWIDGET = "appwidget"
    val SWAP_KINDS = listOf(K_SPEED, K_APPS, K_CLOCK, K_LANG)

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun carName(c: Context): String = p(c).getString("name", "PRIUS") ?: "PRIUS"
    fun panel(c: Context): String = p(c).getString("panel", PANEL_CAR) ?: PANEL_CAR
    fun panelWidget(c: Context): String = p(c).getString("panel_widget", K_SPEED) ?: K_SPEED
    fun lights(c: Context): String = p(c).getString("lights", AUTO) ?: AUTO
    fun theme(c: Context): String = p(c).getString("theme", AUTO) ?: AUTO
    fun galleryUri(c: Context): Uri? = p(c).getString("gallery", null)?.let { Uri.parse(it) }
    fun appWidgetId(c: Context): Int = p(c).getInt("appwidget_id", -1)

    fun swapKind(c: Context): String = p(c).getString("swap", K_SPEED) ?: K_SPEED
    fun setSwapKind(c: Context, k: String) = p(c).edit().putString("swap", k).apply()

    fun slots(c: Context): List<String> {
        val s = p(c).getString("slots", null)?.split(",")?.filter { it in DEFAULT_SLOTS }
        return if (s != null && s.size == 4 && s.toSet().size == 4) s else DEFAULT_SLOTS
    }
    fun setSlots(c: Context, s: List<String>) = p(c).edit().putString("slots", s.joinToString(",")).apply()

    fun setGalleryUri(c: Context, uri: Uri?) {
        val e = p(c).edit()
        if (uri == null) e.remove("gallery") else e.putString("gallery", uri.toString())
        e.apply()
    }

    fun setAppWidgetId(c: Context, id: Int) = p(c).edit().putInt("appwidget_id", id).apply()

    /** Cambios hechos en Ajustes: se guardan todos juntos al tocar Guardar. */
    class Draft(c: Context) {
        var name = carName(c)
        var panel = panel(c)
        var panelWidget = panelWidget(c)
        var lights = lights(c)
        var theme = theme(c)
        var lang = Prefs.lang(c)

        fun save(c: Context) {
            p(c).edit()
                .putString("name", name.trim().ifEmpty { "PRIUS" })
                .putString("panel", panel)
                .putString("panel_widget", panelWidget)
                .putString("lights", lights)
                .putString("theme", theme)
                .apply()
            Prefs.setLang(c, lang)
        }
    }

    /** Noche de 5 PM a 6 AM (mismo horario de las luces aprobado en v3). */
    fun isNightHours(): Boolean {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return h >= 17 || h < 6
    }

    fun isDark(c: Context): Boolean = when (theme(c)) {
        LIGHT -> false
        DARK -> true
        else -> isNightHours()
    }

    fun lightsOn(c: Context): Boolean = when (lights(c)) {
        ALWAYS -> true
        OFF -> false
        else -> isNightHours()
    }

    fun resetLayout(c: Context) = p(c).edit().remove("slots").remove("swap").apply()
}
