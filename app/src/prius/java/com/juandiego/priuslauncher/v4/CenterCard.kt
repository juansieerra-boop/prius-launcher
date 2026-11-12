package com.juandiego.priuslauncher.v4

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.juandiego.priuslauncher.R

/**
 * Panel central: el carro (con fondo, sombra y luces), una foto de la galería,
 * un widget de CarGO (velocidad, apps, reloj, idioma) o el widget de otra app.
 */
class CenterCard(
    u: U, private val act: Activity, private val widgetHost: AppWidgetHost?,
    private val onLangChange: (String) -> Unit
) : Card(u) {

    val stage = CarStageView(u.ctx, u)
    private val name: TextView = u.text(12f, 600, u.pal.faint, "").apply {
        gravity = Gravity.CENTER
        letterSpacing = 0.25f
    }
    private val extra = FrameLayout(u.ctx)
    var swap: SwapCard? = null
        private set
    private val chip = LinearLayout(u.ctx)
    var mode = ""
        private set

    init {
        addView(stage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(name, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            bottomMargin = u.i(30f)
        })
        addView(extra, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // "Imagen" (solo en modo editar): abre Ajustes en el panel central
        chip.orientation = LinearLayout.HORIZONTAL
        chip.gravity = Gravity.CENTER_VERTICAL
        chip.background = glassPill(u, 12f)
        chip.setPadding(u.i(14f), 0, u.i(14f), 0)
        chip.elevation = u.px(2f)
        chip.addView(u.icon(R.drawable.v4_ic_camera, u.pal.tx, 16f, 16f), LinearLayout.LayoutParams(u.i(16f), u.i(16f)))
        chip.addView(u.text(13f, 600, u.pal.tx, u.ctx.getString(R.string.v4_image)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = u.i(8f) })
        chip.visibility = View.GONE
        addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, u.i(34f), Gravity.TOP or Gravity.END).apply {
            rightMargin = u.i(14f); topMargin = u.i(14f)
        })
    }

    fun setChip(visible: Boolean, onClick: () -> Unit) {
        chip.visibility = if (visible) View.VISIBLE else View.GONE
        chip.setOnClickListener { onClick() }
    }

    /** Rectángulo del botón Imagen en coordenadas de la pantalla (para el modo editar). */
    fun chipHit(x: Float, y: Float): Boolean {
        if (chip.visibility != View.VISIBLE) return false
        val loc = IntArray(2)
        chip.getLocationOnScreen(loc)
        return x >= loc[0] && x <= loc[0] + chip.width && y >= loc[1] && y <= loc[1] + chip.height
    }

    fun applyMode(tall: Boolean) {
        val panel = V4Prefs.panel(act)
        extra.removeAllViews()
        swap = null
        name.text = V4Prefs.carName(act)
        stage.lightsOn = V4Prefs.lightsOn(act)
        when {
            panel == V4Prefs.PANEL_GALLERY && V4Prefs.galleryUri(act) != null -> {
                mode = panel
                stage.visibility = View.VISIBLE
                stage.setGallery(decode(act, V4Prefs.galleryUri(act)!!, 1600))
                name.visibility = View.GONE
            }
            panel == V4Prefs.PANEL_WIDGET && V4Prefs.panelWidget(act) == V4Prefs.K_APPWIDGET && widgetHost != null && V4Prefs.appWidgetId(act) >= 0 -> {
                mode = V4Prefs.K_APPWIDGET
                stage.visibility = View.GONE
                name.visibility = View.GONE
                val id = V4Prefs.appWidgetId(act)
                val info = AppWidgetManager.getInstance(act).getAppWidgetInfo(id)
                if (info != null) {
                    try {
                        val hv = widgetHost.createView(act.applicationContext, id, info)
                        hv.setAppWidget(id, info)
                        extra.addView(hv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            val m = u.i(12f); setMargins(m, m, m, m)
                        })
                    } catch (e: Exception) {
                        showSwap(V4Prefs.K_SPEED, tall)
                    }
                } else {
                    showSwap(V4Prefs.K_SPEED, tall)
                }
            }
            panel == V4Prefs.PANEL_WIDGET -> {
                mode = panel
                stage.visibility = View.GONE
                name.visibility = View.GONE
                showSwap(V4Prefs.panelWidget(act), tall)
            }
            else -> {
                mode = V4Prefs.PANEL_CAR
                stage.visibility = View.VISIBLE
                stage.setGallery(null)
                name.visibility = View.VISIBLE
            }
        }
    }

    private fun showSwap(kind: String, tall: Boolean) {
        val k = if (kind in V4Prefs.SWAP_KINDS) kind else V4Prefs.K_SPEED
        val sc = SwapCard(u, act, k, onLangChange)
        sc.background = null
        sc.setTall(tall)
        extra.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        swap = sc
    }

    companion object {
        /** Carga una foto de la galería respetando la rotación del celular. */
        fun decode(c: Context, uri: Uri, maxWidth: Int): Bitmap? {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0) return null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
                val bmp = c.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return null
                val rotation = c.contentResolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
                if (rotation == 0f) return bmp
                return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
            } catch (e: Exception) {
                return null
            } catch (e: OutOfMemoryError) {
                return null
            }
        }

        fun pickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
    }
}
