package com.cargo.diag

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.ScrollView
import android.widget.TextView

/** Muestra los datos reales del sistema (no la etiqueta que pone el fabricante en Ajustes). */
class DiagActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(dm)
        @Suppress("DEPRECATION")
        val abis = if (Build.VERSION.SDK_INT >= 21) Build.SUPPORTED_ABIS.joinToString() else Build.CPU_ABI
        val info = """
            CarGo · Diagnóstico

            API (SDK_INT): ${Build.VERSION.SDK_INT}
            Android real: ${Build.VERSION.RELEASE}
            Procesador (ABI): $abis
            Pantalla: ${dm.widthPixels} x ${dm.heightPixels} · ${dm.densityDpi} dpi
            Modelo: ${Build.MANUFACTURER} ${Build.MODEL}
            Hardware: ${Build.HARDWARE} / ${Build.BOARD}
            Build: ${Build.DISPLAY}
        """.trimIndent()
        setContentView(ScrollView(this).apply {
            addView(TextView(this@DiagActivity).apply {
                text = info
                textSize = 24f
                setTextColor(0xFFFFFFFF.toInt())
                setPadding(48, 48, 48, 48)
            })
        })
    }
}
