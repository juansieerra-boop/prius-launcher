package com.cargo.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.cargo.launcher.obd.ObdService

/**
 * Encendido del estéreo: arranca la telemetría OBD2 y, solo si CarGo todavía NO es la pantalla de
 * inicio predeterminada, abre el launcher como respaldo.
 * Si ya es la predeterminada, Android la muestra solo; abrirla otra vez sacaría al conductor de Maps
 * cuando el estéreo despierta del modo ACC (algunas unidades mandan "bootcheck" en cada encendido).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CarGoBoot"
        private val BOOT_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.BOOT_IPO_COMPLETED",   // MTK: arranque rápido (IPO)
            "com.microntek.bootcheck"                     // Microntek / MTC: despertar de ACC
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in BOOT_ACTIONS) return   // ignora intents falsos u otros
        Log.d(TAG, "Encendido: $action")

        CarGoPrefs.init(context)
        ObdService.start(context)

        if (isDefaultHome(context)) return
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            // Android 10+ puede bloquear abrir pantallas desde segundo plano
            Log.w(TAG, "No se pudo abrir el launcher desde el arranque", e)
        }
    }

    private fun isDefaultHome(c: Context): Boolean {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == c.packageName
    }
}
