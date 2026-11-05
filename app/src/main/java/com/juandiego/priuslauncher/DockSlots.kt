package com.juandiego.priuslauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Los botones de la barra de abajo. Cada uno intenta abrir, en orden:
 *  1. la app que elegiste manteniendo presionado el botón,
 *  2. la primera app conocida que esté instalada,
 *  3. una acción del sistema (marcador, ajustes).
 * Si nada funciona, se abre la lista de apps para que elijas una.
 */
object DockSlots {

    const val PREFS = "dock"

    class Slot(
        val labelRes: Int,
        val iconRes: Int,
        val packages: List<String>,
        val fallback: (() -> Intent)?
    )

    val ALL = listOf(
        Slot(
            R.string.slot_maps, R.drawable.ic_nav,
            listOf("com.google.android.apps.maps", "com.waze"),
            null
        ),
        Slot(
            R.string.slot_music, R.drawable.ic_music,
            listOf(
                "com.spotify.music",
                "com.google.android.apps.youtube.music",
                "com.amazon.mp3",
                "com.apple.android.music",
                "com.pandora.android"
            ),
            null
        ),
        Slot(
            R.string.slot_phone, R.drawable.ic_phone,
            emptyList(),
            { Intent(Intent.ACTION_DIAL) }
        ),
        Slot(
            R.string.slot_radio, R.drawable.ic_radio,
            emptyList(),
            null
        ),
        Slot(
            R.string.slot_settings, R.drawable.ic_tune,
            emptyList(),
            { Intent(Settings.ACTION_SETTINGS) }
        )
    )

    fun key(index: Int) = "slot_$index"

    fun resolve(ctx: Context, prefs: SharedPreferences, index: Int): Intent? {
        val pm = ctx.packageManager

        val saved = prefs.getString(key(index), null)?.let { ComponentName.unflattenFromString(it) }
        if (saved != null && activityExists(pm, saved)) {
            return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(saved)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

        val slot = ALL[index]
        for (pkg in slot.packages) {
            val launch = pm.getLaunchIntentForPackage(pkg)
            if (launch != null) return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return slot.fallback?.invoke()?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    private fun activityExists(pm: PackageManager, cn: ComponentName): Boolean =
        try {
            pm.getActivityInfo(cn, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
}
