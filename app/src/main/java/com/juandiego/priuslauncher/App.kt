package com.juandiego.priuslauncher

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Si la app se llega a cerrar por un error, lo guarda para mostrarlo en pantalla
 * la próxima vez que abra (en lugar de quedarse en negro).
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            CrashReport.save(this, error)
            previous?.uncaughtException(thread, error)
        }
    }
}

object CrashReport {
    private const val PREFS = "crash"
    private const val KEY = "trace"

    fun format(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        val header = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
        return header + sw.toString().take(6000)
    }

    fun save(ctx: Context, error: Throwable) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, format(error))
            .commit()
    }

    fun take(ctx: Context): String? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val t = p.getString(KEY, null) ?: return null
        p.edit().remove(KEY).commit()
        return t
    }

    /** Pantalla simple con el error, hecha sin layouts para que no pueda fallar. */
    fun show(act: Activity, trace: String) {
        val d = act.resources.displayMetrics.density
        val root = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(16, 18, 21))
            setPadding((24 * d).toInt(), (20 * d).toInt(), (24 * d).toInt(), (16 * d).toInt())
        }
        root.addView(TextView(act).apply {
            text = act.getString(R.string.app_name) + " tuvo un error.\nTómale una foto a esta pantalla y mándasela a Claude."
            setTextColor(Color.WHITE)
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
        })
        val buttons = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        buttons.addView(Button(act).apply {
            text = "Reintentar"
            setOnClickListener { act.recreate() }
        })
        buttons.addView(Button(act).apply {
            text = "Cambiar pantalla de inicio"
            setOnClickListener {
                try {
                    act.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    act.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        })
        root.addView(buttons)
        val scroll = ScrollView(act)
        scroll.addView(TextView(act).apply {
            text = trace
            setTextColor(Color.rgb(255, 170, 170))
            textSize = 11f
            typeface = Typeface.MONOSPACE
        })
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        act.setContentView(root)
    }
}
