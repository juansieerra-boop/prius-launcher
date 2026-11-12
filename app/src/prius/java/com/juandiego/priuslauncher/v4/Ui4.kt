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

/** Colores v4: sin blanco puro ni negro puro para no cansar la vista. */
class Pal(val dark: Boolean) {
    val bg = if (dark) 0xFF0A0A0C.toInt() else 0xFFE6E7EA.toInt()
    val card = if (dark) 0xFF17181B.toInt() else 0xFFFAFAFB.toInt()
    val card2 = if (dark) 0xFF121316.toInt() else 0xFFF3F4F6.toInt()
    val tx = if (dark) 0xFFE4E5E8.toInt() else 0xFF1C1D20.toInt()
    val sub = if (dark) 0xFF8C9199.toInt() else 0xFF6B6F76.toInt()
    val faint = if (dark) 0xFF62666D.toInt() else 0xFF9AA0A8.toInt()
    val line = if (dark) 0xFF2C2E33.toInt() else 0xFFE2E4E8.toInt()
    val btn = if (dark) 0xFF2A2C31.toInt() else 0xFFF0F1F4.toInt()
    val glass = if (dark) 0xEB1C1D21.toInt() else 0xEBFFFFFF.toInt()
    val accent = 0xFF3E9BFF.toInt()
    val green = 0xFF34C759.toInt()
    val yellow = 0xFFFFD84A.toInt()
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
