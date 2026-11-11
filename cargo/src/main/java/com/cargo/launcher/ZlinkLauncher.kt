package com.cargo.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Abre ZLink, la app del estéreo que proyecta Apple CarPlay / Android Auto.
 * Orden de búsqueda:
 *  1. la app que el instalador eligió (mantener presionado el botón),
 *  2. los paquetes conocidos de ZLink,
 *  3. cualquier app instalada cuyo nombre contenga "ZLink" o "CarPlay" (cada fabricante la renombra).
 */
object ZlinkLauncher {

    val KNOWN_PACKAGES = listOf(
        "com.zjinnova.zlink",
        "com.zjinnova.zlink5",
        "com.zjinnova.zlink.carplay",
        "com.zjinnova.zlinkcar",
        "com.syu.carlink",
        "com.suding.carlink",
        "com.ts.carplay",
        "com.fyt.carplay"
    )

    private val NAME_HINT = Regex("z\\s*link|carplay|car\\s*link|autokit", RegexOption.IGNORE_CASE)

    /** Intent listo para abrir ZLink, o null si no hay ninguna instalada. */
    fun intent(c: Context): Intent? {
        val pm = c.packageManager
        CarGoPrefs.zlinkComponent?.let { ComponentName.unflattenFromString(it) }?.let { cn ->
            if (exists(pm, cn)) return launchIntent(cn)
        }
        for (pkg in KNOWN_PACKAGES) {
            pm.getLaunchIntentForPackage(pkg)?.let { return it.addFlags(FLAGS) }
        }
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val hit = pm.queryIntentActivities(main, 0).firstOrNull {
            it.activityInfo.packageName != c.packageName && NAME_HINT.containsMatchIn(it.loadLabel(pm))
        } ?: return null
        return launchIntent(ComponentName(hit.activityInfo.packageName, hit.activityInfo.name))
    }

    /** true si la abrió; false si no está instalada o falló. */
    fun open(c: Context): Boolean {
        val i = intent(c) ?: return false
        return try {
            c.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Apps que se pueden elegir manualmente para el botón de CarPlay (por nombre). */
    fun launchableApps(c: Context): List<Pair<String, ComponentName>> {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .filter { it.activityInfo.packageName != c.packageName }
            .map { it.loadLabel(pm).toString() to ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            .sortedWith(compareByDescending<Pair<String, ComponentName>> { NAME_HINT.containsMatchIn(it.first) }.thenBy { it.first.lowercase() })
    }

    private const val FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

    private fun launchIntent(cn: ComponentName) =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn).addFlags(FLAGS)

    private fun exists(pm: PackageManager, cn: ComponentName) = try {
        pm.getActivityInfo(cn, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
