package com.juandiego.priuslauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/** Ajustes que el usuario elige: color, unidades y foto. */
object Prefs {
    private const val NAME = "launcher"

    /** Colores listos para elegir. */
    val PRESETS = intArrayOf(
        0xFFE5243B.toInt(), // rojo
        0xFFFF7A1A.toInt(), // naranja
        0xFFFFC400.toInt(), // amarillo
        0xFF3DDC84.toInt(), // verde
        0xFF00D4FF.toInt(), // cian
        0xFF2F7BFF.toInt(), // azul
        0xFF9B5CFF.toInt(), // morado
        0xFFFF4081.toInt(), // rosa
        0xFFE6E8EB.toInt()  // plata
    )

    private fun p(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun accent(c: Context): Int = p(c).getInt("accent", PRESETS[0])
    fun setAccent(c: Context, color: Int) = p(c).edit().putInt("accent", color).apply()

    fun useKmh(c: Context): Boolean = p(c).getBoolean("kmh", false)
    fun setKmh(c: Context, kmh: Boolean) = p(c).edit().putBoolean("kmh", kmh).apply()

    fun lightTheme(c: Context): Boolean = p(c).getBoolean("light", false)
    fun setLightTheme(c: Context, light: Boolean) = p(c).edit().putBoolean("light", light).apply()

    /** Cada modo (día y noche) recuerda su propia foto. */
    private fun carKey(c: Context) = if (lightTheme(c)) "car_uri_day" else "car_uri"

    fun carUri(c: Context): Uri? = p(c).getString(carKey(c), null)?.let { Uri.parse(it) }
    fun setCarUri(c: Context, uri: Uri?) {
        if (uri == null) p(c).edit().remove(carKey(c)).apply()
        else p(c).edit().putString(carKey(c), uri.toString()).apply()
    }
}

/** Aplica el fondo elegido (negro o blanco) a cada pantalla. */
object Themer {
    fun wrap(base: Context): Context {
        val cfg = android.content.res.Configuration(base.resources.configuration)
        val night = if (Prefs.lightTheme(base)) android.content.res.Configuration.UI_MODE_NIGHT_NO
        else android.content.res.Configuration.UI_MODE_NIGHT_YES
        cfg.uiMode = (cfg.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(cfg)
    }
}

/** Elegir y cargar la foto del carro. */
object CarPhoto {
    const val REQ_PICK = 4101

    fun pickIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("image/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    /** Guarda la foto elegida y pide permiso permanente para leerla. */
    fun handleResult(act: Activity, data: Intent?): Boolean {
        val uri = data?.data ?: return false
        try {
            act.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            // algunos selectores no lo permiten; igual intentamos usarla
        }
        val old = Prefs.carUri(act)
        if (old != null && old != uri) releaseQuietly(act, old)
        Prefs.setCarUri(act, uri)
        return true
    }

    fun reset(act: Activity) {
        Prefs.carUri(act)?.let { releaseQuietly(act, it) }
        Prefs.setCarUri(act, null)
    }

    private fun releaseQuietly(act: Activity, uri: Uri) {
        try {
            act.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // no había permiso guardado
        }
    }

    /** La foto guardada o, si no hay (o ya no se puede leer), mi_carro.png. */
    fun load(c: Context, maxWidth: Int): Bitmap {
        val uri = Prefs.carUri(c)
        if (uri != null) {
            val b = decodeUri(c, uri, maxWidth)
            if (b != null) return b
        }
        val default = if (Prefs.lightTheme(c)) R.drawable.mi_carro_dia else R.drawable.mi_carro
        return BitmapFactory.decodeResource(c.resources, default)
    }

    private fun decodeUri(c: Context, uri: Uri, maxWidth: Int): Bitmap? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0) return null

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = c.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            // Respeta la rotación de las fotos tomadas con el celular
            val rotation = c.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
            if (rotation == 0f) return bmp
            val m = Matrix().apply { postRotate(rotation) }
            return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        } catch (e: Exception) {
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
    }
}
