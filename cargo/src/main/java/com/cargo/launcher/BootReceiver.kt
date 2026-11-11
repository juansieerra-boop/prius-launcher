package com.cargo.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.cargo.launcher.obd.ObdService

/** Al encender: arranca la telemetría y abre el launcher (respaldo si aún no es el inicio predeterminado). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        CarGoPrefs.init(c)
        ObdService.start(c)
        try {
            c.startActivity(
                Intent(c, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (_: Exception) {
            // Android 10+ puede bloquear abrir pantallas desde segundo plano; como HOME no hace falta
        }
    }
}
