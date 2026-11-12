package com.juandiego.priuslauncher.v4

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Colores v4: sin blanco puro ni negro puro para no cansar la vista.
 * style: "gray" (Prius), "rose" (oro rosa, Jeep de CARO) o "xmas" (Navidad).
 */
class Pal(val dark: Boolean, val style: String = "gray") {
    private fun c(gray: Long, grayD: Long, rose: Long, roseD: Long, xmas: Long, xmasD: Long): Int = when (style) {
        "rose" -> if (dark) roseD else rose
        "xmas" -> if (dark) xmasD else xmas
        else -> if (dark) grayD else gray
    }.toInt()
    val bg = c(0xFFE6E7EA, 0xFF0A0A0C, 0xFFEFE5E2, 0xFF0C090B, 0xFFF6E6EC, 0xFF0B0D19)
    val card = c(0xFFFAFAFB, 0xFF17181B, 0xFFFCF8F7, 0xFF1A1417, 0xFFFFF8FA, 0xFF161A2B)
    val card2 = c(0xFFF3F4F6, 0xFF121316, 0xFFF6EEEC, 0xFF141013, 0xFFFBEFF3, 0xFF111423)
    val tx = c(0xFF1C1D20, 0xFFE4E5E8, 0xFF2A1C20, 0xFFEDE4E6, 0xFF2B1A24, 0xFFF2E8EE)
    val sub = c(0xFF6B6F76, 0xFF8C9199, 0xFF7A646A, 0xFFA08C93, 0xFF8A6578, 0xFFB39FB0)
    val faint = c(0xFF9AA0A8, 0xFF62666D, 0xFFA8949A, 0xFF6E5E64, 0xFFB496A6, 0xFF756C86)
    val line = c(0xFFE2E4E8, 0xFF2C2E33, 0xFFEADBD8, 0xFF322A2E, 0xFFF0DAE3, 0xFF2A2D44)
    val btn = c(0xFFF0F1F4, 0xFF2A2C31, 0xFFF3E8E5, 0xFF2C2328, 0xFFF8E9EF, 0xFF252940)
    val glass = c(0xEBFFFFFF, 0xEB1C1D21, 0xEBFFFAF9, 0xEB1E171B, 0xEBFFFAFC, 0xEB181B2D)
    val accent = c(0xFF3E9BFF, 0xFF3E9BFF, 0xFFC9506E, 0xFFC9506E, 0xFFE8508A, 0xFFE8508A)
    val green = 0xFF34C759.toInt()
    val yellow = 0xFFFFD84A.toInt()

    /** Fondo del carro: pared arriba/abajo, piso arriba/abajo, luz detrás y su intensidad. */
    val stage: IntArray = when {
        style == "rose" && dark -> intArrayOf(0xFF151013.toInt(), 0xFF1D171A.toInt(), 0xFF191316.toInt(), 0xFF0D0A0C.toInt(), 0xFF43363C.toInt())
        style == "rose" -> intArrayOf(0xFFF6EEEB.toInt(), 0xFFEDE2DF.toInt(), 0xFFE6D9D5.toInt(), 0xFFDACBC7.toInt(), 0xFFFFFFFF.toInt())
        dark -> intArrayOf(0xFF121317.toInt(), 0xFF1A1C21.toInt(), 0xFF16171B.toInt(), 0xFF0C0D10.toInt(), 0xFF3A3F48.toInt())
        else -> intArrayOf(0xFFECEDEF.toInt(), 0xFFE2E4E7.toInt(), 0xFFDCDEE1.toInt(), 0xFFCFD2D6.toInt(), 0xFFFFFFFF.toInt())
    }
}

/** Tipografía Inter (la misma de los renders). */
object Fonts {
    private val cache = HashMap<String, Typeface>()
    private fun get(c: Context, name: String): Typeface = cache.getOrPut(name) {
        try {
            Typeface.createFromAsset(c.assets, "fonts/inter_$name.otf")
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
    }
    fun w200(c: Context) = get(c, "extralight")
    fun w300(c: Context) = get(c, "light")
    fun w400(c: Context) = get(c, "regular")
    fun w500(c: Context) = get(c, "medium")
    fun w600(c: Context) = get(c, "semibold")
    fun w700(c: Context) = get(c, "bold")
    fun weight(c: Context, w: Int) = when {
        w <= 200 -> w200(c)
        w <= 300 -> w300(c)
        w <= 400 -> w400(c)
        w <= 500 -> w500(c)
        w <= 600 -> w600(c)
        else -> w700(c)
    }
}

/**
 * Medidas del diseño: todo se dibujó en un lienzo de 600 de alto.
 * s = alto real / 600 (1024x600 → 1.0 · 1280x720 → 1.2 · 1920x1080 → 1.8).
 */
class U(val ctx: Context, val s: Float, val pal: Pal) {
    fun px(v: Float): Float = v * s
    fun i(v: Float): Int = (v * s).roundToInt()
    fun i(v: Int): Int = (v * s).roundToInt()

    fun text(size: Float, weight: Int, color: Int, txt: CharSequence = ""): TextView = TextView(ctx).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size * s)
        typeface = Fonts.weight(ctx, weight)
        setTextColor(color)
        includeFontPadding = false
        isSingleLine = true
        ellipsize = android.text.TextUtils.TruncateAt.END
        text = txt
    }

    fun icon(res: Int, color: Int, w: Float, h: Float): ImageView = ImageView(ctx).apply {
        setImageResource(res)
        setColorFilter(color)
        scaleType = ImageView.ScaleType.FIT_CENTER
        tag = floatArrayOf(w, h)
    }

    fun round(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius * s
    }

    fun cardBg(radius: Float = 22f): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.card, pal.card2)).apply { cornerRadius = radius * s }
}

/** Coloca una vista en (x, y) con tamaño (w, h), en unidades del diseño. */
fun FrameLayout.place(u: U, v: View, x: Float, y: Float, w: Float, h: Float): View {
    val lp = FrameLayout.LayoutParams(
        if (w < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(w),
        if (h < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(h)
    )
    lp.gravity = Gravity.TOP or Gravity.START
    lp.leftMargin = u.i(x)
    lp.topMargin = u.i(y)
    if (v.parent == null) addView(v, lp) else v.layoutParams = lp
    return v
}

/** Igual que place, pero medido desde la derecha (right) del contenedor. */
fun FrameLayout.placeRight(u: U, v: View, right: Float, y: Float, w: Float, h: Float): View {
    val lp = FrameLayout.LayoutParams(
        if (w < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(w),
        if (h < 0) ViewGroup.LayoutParams.WRAP_CONTENT else u.i(h)
    )
    lp.gravity = Gravity.TOP or Gravity.END
    lp.rightMargin = u.i(right)
    lp.topMargin = u.i(y)
    if (v.parent == null) addView(v, lp) else v.layoutParams = lp
    return v
}

/** Tarjeta redondeada con el degradado suave del diseño; recorta su contenido. */
open class Card(val u: U) : FrameLayout(u.ctx) {
    var radius = 22f
        set(v) { field = v; background = u.cardBg(v); invalidateOutline() }

    init {
        background = u.cardBg(radius)
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, u.px(radius))
            }
        }
        clipToOutline = true
    }
}

fun glassPill(u: U, radius: Float): Drawable = u.round(u.pal.glass, radius)

fun dot(u: U, color: Int, size: Float): View = View(u.ctx).apply {
    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
    tag = size
}

fun alpha(c: Int, a: Float): Int = Color.argb((a * 255).roundToInt(), Color.red(c), Color.green(c), Color.blue(c))
