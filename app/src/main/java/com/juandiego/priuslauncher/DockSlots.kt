package com.juandiego.priuslauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager

/**
 * Los 6 botones de la cuadrícula. Cada uno intenta abrir, en orden:
 *  1. la app que elegiste manteniendo presionado el botón,
 *  2. la primera app conocida que esté instalada.
 * Si no hay ninguna, se abre la lista de apps para que elijas una.
 */
object DockSlots {

    const val PREFS = "dock"
    const val MAPS = 3
    const val PHONE = 6

    /** Los primeros 6 son la cuadrícula; el 7 es "Conectar teléfono". */
    const val TILE_COUNT = 6

    class Slot(val labelRes: Int, val iconRes: Int, val packages: List<String>)

    val ALL = listOf(
        Slot(
            R.string.slot_radio, R.drawable.ic_radio,
            listOf("com.syu.radio", "com.microntek.radio", "com.android.fmradio", "com.ts.radio", "com.fyt.radio")
        ),
        Slot(
            R.string.slot_btmusic, R.drawable.ic_bt,
            listOf("com.syu.bt", "com.syu.btmusic", "com.microntek.btmusic", "com.ts.bt")
        ),
        Slot(
            R.string.slot_youtube, R.drawable.ic_yt,
            listOf("com.google.android.youtube")
        ),
        Slot(
            R.string.slot_maps, R.drawable.ic_pin,
            listOf("com.google.android.apps.maps", "com.waze", "app.organicmaps", "net.osmand", "net.osmand.plus", "com.here.app.maps")
        ),
        Slot(
            R.string.slot_video, R.drawable.ic_video,
            listOf("org.videolan.vlc", "com.mxtech.videoplayer.ad", "com.syu.video", "com.android.gallery3d")
        ),
        Slot(
            R.string.slot_ytmusic, R.drawable.ic_music,
            listOf("com.google.android.apps.youtube.music", "com.spotify.music")
        ),
        Slot(
            R.string.connect_phone, R.drawable.ic_smartphone,
            emptyList()
        )
    )

    fun key(index: Int) = "tile_$index"

    private fun saved(ctx: Context, prefs: SharedPreferences, index: Int): ComponentName? {
        val cn = prefs.getString(key(index), null)?.let { ComponentName.unflattenFromString(it) } ?: return null
        return try {
            ctx.packageManager.getActivityInfo(cn, 0)
            cn
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    fun resolve(ctx: Context, prefs: SharedPreferences, index: Int): Intent? {
        val cn = saved(ctx, prefs, index)
        if (cn != null) {
            return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        for (pkg in ALL[index].packages) {
            val launch = ctx.packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) return launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }

    /** Si elegiste una app para este botón, muestra su nombre; si no, el nombre del botón. */
    fun labelFor(ctx: Context, prefs: SharedPreferences, index: Int): String {
        val cn = saved(ctx, prefs, index) ?: return ctx.getString(ALL[index].labelRes)
        return try {
            ctx.packageManager.getActivityInfo(cn, 0).loadLabel(ctx.packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            ctx.getString(ALL[index].labelRes)
        }
    }
}
