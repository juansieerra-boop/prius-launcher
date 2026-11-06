package com.juandiego.priuslauncher

import android.app.Activity
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * Lista de todas las apps instaladas.
 * Si se abre con EXTRA_PICK_SLOT, al tocar una app se asigna a ese botón de la barra.
 */
class AppsActivity : Activity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Themer.wrap(newBase))
    }

    companion object {
        const val EXTRA_PICK_SLOT = "pick_slot"
    }

    private data class AppEntry(val label: String, val component: ComponentName)

    private val apps = mutableListOf<AppEntry>()
    private val iconCache = HashMap<ComponentName, Drawable>()
    private var pickSlot = -1

    private val adapter = object : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int): Any = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: layoutInflater.inflate(R.layout.app_item, parent, false)
            val entry = apps[position]
            v.findViewById<TextView>(R.id.appLabel).text = entry.label
            v.findViewById<ImageView>(R.id.appIcon).setImageDrawable(iconFor(entry.component))
            return v
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)
        hideSystemUi()

        pickSlot = intent.getIntExtra(EXTRA_PICK_SLOT, -1)
        if (pickSlot !in DockSlots.ALL.indices) pickSlot = -1

        val title = findViewById<TextView>(R.id.title)
        val reset = findViewById<TextView>(R.id.btnReset)
        if (pickSlot >= 0) {
            val slotName = getString(DockSlots.ALL[pickSlot].labelRes)
            title.text = getString(R.string.pick_for, slotName)
            reset.visibility = View.VISIBLE
            reset.setOnClickListener {
                prefs().edit().remove(DockSlots.key(pickSlot)).apply()
                Toast.makeText(this, getString(R.string.reset_done, slotName), Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            title.setText(R.string.apps_title)
        }

        findViewById<View>(R.id.btnClose).setOnClickListener { finish() }

        val grid = findViewById<GridView>(R.id.grid)
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ -> onAppClick(apps[position]) }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            openAppInfo(apps[position])
            true
        }

        loadApps()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun prefs() = getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE)

    private fun loadApps() {
        Thread {
            val pm = packageManager
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(main, 0)
                .filter { it.activityInfo.packageName != packageName }
                .map {
                    AppEntry(
                        it.loadLabel(pm).toString(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    )
                }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                apps.clear()
                apps.addAll(list)
                adapter.notifyDataSetChanged()
            }
        }.start()
    }

    private fun iconFor(cn: ComponentName): Drawable =
        iconCache.getOrPut(cn) {
            try {
                packageManager.getActivityIcon(cn)
            } catch (e: Exception) {
                packageManager.defaultActivityIcon
            }
        }

    private fun onAppClick(entry: AppEntry) {
        if (pickSlot >= 0) {
            prefs().edit().putString(DockSlots.key(pickSlot), entry.component.flattenToString()).apply()
            val slotName = getString(DockSlots.ALL[pickSlot].labelRes)
            Toast.makeText(this, getString(R.string.assigned, entry.label, slotName), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val launch = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(launch)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppInfo(entry: AppEntry) {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", entry.component.packageName, null)
                )
            )
        } catch (e: ActivityNotFoundException) {
            // algunas pantallas no tienen esa pantalla de ajustes
        }
    }
}
