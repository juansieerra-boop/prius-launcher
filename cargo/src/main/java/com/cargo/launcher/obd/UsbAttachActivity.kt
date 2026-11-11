package com.cargo.launcher.obd

import android.app.Activity
import android.os.Bundle

/**
 * Android abre esta pantalla invisible cuando se conecta un adaptador OBD2 USB.
 * Con eso CarGo recibe el permiso del USB (y la opción "usar siempre") y el servicio se reconecta solo.
 */
class UsbAttachActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ObdService.start(this)
        finish()
    }
}
