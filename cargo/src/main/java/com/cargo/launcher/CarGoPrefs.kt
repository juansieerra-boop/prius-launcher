package com.cargo.launcher

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest

class CarGoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CarGoPrefs.init(this)
    }
}

/** Vehículos que trae CarGo de fábrica. Se agregan aquí; el resto del código no cambia. */
enum class Vehicle { PRIUS_2011, JEEP_SAHARA_2021 }

/** Temas de la pantalla. En el Jeep: DAY = Sunset Rose Gold, NIGHT = Midnight Rose & Cherry, XMAS = navidad rosa. */
enum class ThemeMode { DAY, NIGHT, XMAS }

/** Cómo se ve el carro. */
enum class CarView { CLEAN, XRAY, HUD_ROAD, WIREFRAME }

/** Configuración persistente (SharedPreferences). Todo se cambia en campo, sin recompilar. */
object CarGoPrefs {
    enum class ObdMode { AUTO, USB, BLUETOOTH }

    private lateinit var p: SharedPreferences

    fun init(c: Context) {
        if (!::p.isInitialized) p = c.applicationContext.getSharedPreferences("cargo", Context.MODE_PRIVATE)
    }

    private inline fun <reified T : Enum<T>> enumPref(key: String, def: T): T =
        p.getString(key, null)?.let { s -> enumValues<T>().firstOrNull { it.name == s } } ?: def

    var vehicle: Vehicle
        get() = enumPref("vehicle", Vehicle.PRIUS_2011)
        set(v) = p.edit().putString("vehicle", v.name).apply()

    var theme: ThemeMode
        get() = enumPref("theme_" + vehicle.name, ThemeMode.NIGHT)
        set(v) = p.edit().putString("theme_" + vehicle.name, v.name).apply()

    var carView: CarView
        get() = enumPref("view_" + vehicle.name, CarView.HUD_ROAD)
        set(v) = p.edit().putString("view_" + vehicle.name, v.name).apply()

    /** PNG propio del cliente (content:// persistente o ruta de archivo), por vehículo. */
    var customCarUri: String?
        get() = p.getString("custom_" + vehicle.name, null)
        set(v) = p.edit().putString("custom_" + vehicle.name, v).apply()

    var useMph: Boolean
        get() = p.getBoolean("mph", true)
        set(v) = p.edit().putBoolean("mph", v).apply()

    var obdMode: ObdMode
        get() = enumPref("obd_mode", ObdMode.AUTO)
        set(v) = p.edit().putString("obd_mode", v.name).apply()

    var obdMac: String?
        get() = p.getString("obd_mac", null)
        set(v) = p.edit().putString("obd_mac", v).apply()

    var usbBaud: Int
        get() = p.getInt("usb_baud", 0)
        set(v) = p.edit().putInt("usb_baud", v).apply()

    /** App elegida para el botón de CarPlay (ComponentName aplanado), o null para buscar ZLink sola. */
    var zlinkComponent: String?
        get() = p.getString("zlink_cn", null)
        set(v) = p.edit().putString("zlink_cn", v).apply()

    var lastObdLabel: String?
        get() = p.getString("obd_label", null)
        set(v) = p.edit().putString("obd_label", v).apply()

    // ---- código del instalador: solo se guarda su huella SHA-256, nunca el número ----
    private const val DEFAULT_PIN_HASH = "aa914af031e46d941e1311025e6cd9e73614c5301f793e8bc0debe511fd74df1"

    fun checkPin(pin: String): Boolean = hash(pin) == (p.getString("pin_hash", null) ?: DEFAULT_PIN_HASH)

    fun setPin(pin: String) = p.edit().putString("pin_hash", hash(pin)).apply()

    private fun hash(pin: String): String =
        MessageDigest.getInstance("SHA-256").digest("cargo:$pin".toByteArray()).joinToString("") { "%02x".format(it) }
}
