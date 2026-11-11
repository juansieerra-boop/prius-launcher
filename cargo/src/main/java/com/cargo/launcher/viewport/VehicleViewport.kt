package com.cargo.launcher.viewport

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.TransitionDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.cargo.launcher.R
import com.cargo.launcher.Vehicle
import kotlin.math.roundToInt

/** Nombre que usa el viewport; es el mismo enum que guarda CarGoPrefs. */
typealias VehicleType = Vehicle

/**
 * Datos de cada auto, en fracciones del dibujo (0..1) para que no dependan de la resolución.
 *  - heads/fogs/tails: x, y, radio (radio como fracción del ancho del dibujo)
 *  - contactX/Y: punto del piso bajo el centro del auto (ahí se apoya la sombra)
 *  - vp / near: carretera (fracciones del CONTENEDOR): punto de fuga y centro del borde cercano.
 *    El Prius mira hacia adelante-izquierda y el Jeep hacia adelante-derecha, así que la carretera
 *    se fuga detrás de cada uno y el auto queda sobre el carril central.
 */
class VehicleSpec(
    val drawable: Int,
    val heads: List<FloatArray>, val fogs: List<FloatArray>, val tails: List<FloatArray>,
    val contactX: Float, val contactY: Float,
    val vpX: Float, val vpY: Float, val nearX: Float, val nearY: Float, val nearHalfW: Float
) {
    companion object {
        fun of(v: VehicleType): VehicleSpec = when (v) {
            Vehicle.PRIUS_2011 -> VehicleSpec(
                R.drawable.vp_prius_2011,   // plata, rines negros
                heads = listOf(floatArrayOf(0.385f, 0.527f, 0.040f), floatArrayOf(0.046f, 0.464f, 0.022f)),
                fogs = listOf(floatArrayOf(0.396f, 0.788f, 0.012f)),
                tails = emptyList(),
                contactX = 0.53f, contactY = 0.975f,
                vpX = 1.05f, vpY = 0.36f, nearX = -0.30f, nearY = 1.30f, nearHalfW = 0.95f
            )
            Vehicle.JEEP_SAHARA_2021 -> VehicleSpec(
                R.drawable.vp_jeep_sahara_caro,   // blanco, placa CARO
                heads = listOf(floatArrayOf(0.676f, 0.438f, 0.030f), floatArrayOf(0.891f, 0.409f, 0.025f)),
                fogs = listOf(floatArrayOf(0.696f, 0.678f, 0.011f), floatArrayOf(0.955f, 0.629f, 0.010f)),
                tails = listOf(floatArrayOf(0.017f, 0.368f, 0.012f)),
                contactX = 0.50f, contactY = 0.975f,
                vpX = -0.05f, vpY = 0.36f, nearX = 1.30f, nearY = 1.30f, nearHalfW = 0.95f
            )
        }
    }
}

/**
 * Contenedor de las 5 capas. Todo el tamaño/posición es relativo al contenedor:
 * auto = 88 % del ancho (máx. 56 % del alto), apoyado al 80 % de la altura.
 *
 * Cambios sin parpadeo:
 *  - Día ↔ noche: TransitionDrawable en el fondo + ValueAnimator que mezcla los colores de la
 *    carretera y desvanece faros / reflejo, todo en el mismo cuadro.
 *  - Cambio de auto: la capa vpCarNext recibe el nuevo dibujo y se cruza con vpCar (crossfade);
 *    al terminar se intercambian las referencias. Nunca se deja la capa vacía.
 */
class VehicleViewport @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {

    private lateinit var scene: ImageView
    lateinit var road: PerspectiveRoadView; private set
    private lateinit var reflection: ImageView
    private lateinit var shadow: ImageView
    private lateinit var car: ImageView
    private lateinit var carNext: ImageView
    private lateinit var beams: HeadlightBeamView

    private var spec: VehicleSpec? = null
    var vehicle: VehicleType? = null; private set
    var isNight = false; private set
    private var nightFraction = 0f
    private var modeAnim: ValueAnimator? = null
    private val sceneFade = TransitionDrawable(arrayOf<Drawable>(
        context.getDrawable(R.drawable.vp_scene_day)!!, context.getDrawable(R.drawable.vp_scene_night)!!
    )).apply { isCrossFadeEnabled = true }

    /** Tinte del HUD que corresponde al modo (cian neón de noche, ámbar de día). */
    val hudTint: Int get() = if (isNight) NIGHT_TINT else DAY_TINT

    companion object {
        const val NIGHT_TINT = 0xFF22D3EE.toInt()
        const val DAY_TINT = 0xFFFF9F43.toInt()
        private const val FADE_MS = 450L
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        scene = findViewById(R.id.vpScene)
        road = findViewById(R.id.vpRoad)
        reflection = findViewById(R.id.vpReflection)
        shadow = findViewById(R.id.vpShadow)
        car = findViewById(R.id.vpCar)
        carNext = findViewById(R.id.vpCarNext)
        beams = findViewById(R.id.vpBeams)
        scene.setImageDrawable(sceneFade)
        road.setAccent(DAY_TINT, NIGHT_TINT)
        shadow.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(Color.argb(150, 0, 0, 0), Color.argb(60, 0, 0, 0), Color.TRANSPARENT)
        })
        shadow.scaleType = ImageView.ScaleType.FIT_XY
    }

    // ------------------------------------------------------------------ API

    /** Carga el auto. Si ya había otro, hace crossfade; la primera vez lo pone directo. */
    fun setVehicle(v: VehicleType, animate: Boolean = true) {
        if (v == vehicle) return
        val first = vehicle == null
        vehicle = v
        spec = VehicleSpec.of(v)
        val res = spec!!.drawable
        if (first || !animate || !isLaidOut) {
            car.setImageResource(res); reflection.setImageResource(res)
            layoutLayers()
            return
        }
        carNext.animate().cancel(); car.animate().cancel()
        carNext.setImageResource(res)
        carNext.alpha = 0f
        layoutLayers()                       // coloca las dos capas con la proporción del nuevo auto
        carNext.animate().alpha(1f).setDuration(FADE_MS).start()
        car.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            // intercambia: la capa visible pasa a ser la del auto nuevo
            val old = car; car = carNext; carNext = old
            carNext.setImageDrawable(null)
            car.bringToFront(); beams.bringToFront()
            reflection.setImageResource(res)
        }.start()
    }

    /** Día / noche con transición suave (sin recrear vistas). */
    fun setNightMode(night: Boolean, animate: Boolean = true) {
        if (night == isNight && modeAnim == null && (nightFraction == if (night) 1f else 0f)) return
        isNight = night
        val target = if (night) 1f else 0f
        modeAnim?.cancel()
        if (night) sceneFade.startTransition(if (animate) FADE_MS.toInt() else 0)
        else sceneFade.reverseTransition(if (animate) FADE_MS.toInt() else 0)
        if (night) beams.visibility = View.VISIBLE
        if (!animate) { applyNight(target); if (!night) beams.visibility = View.INVISIBLE; return }
        modeAnim = ValueAnimator.ofFloat(nightFraction, target).apply {
            duration = FADE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyNight(it.animatedValue as Float) }
            doOnEnd { modeAnim = null; if (!isNight) beams.visibility = View.INVISIBLE }
            start()
        }
    }

    /** Velocidad del OBD2: mueve las rayas de carril. */
    fun setSpeedMph(mph: Float) = road.setSpeedMph(mph)

    /** false mientras otra app (ZLink) está encima: no se anima nada. */
    var running: Boolean
        get() = road.running
        set(v) { road.running = v }

    // ------------------------------------------------------------------ interno

    private fun applyNight(f: Float) {
        nightFraction = f
        road.setNightFraction(f)
        beams.alpha = f
        reflection.alpha = 0.22f * f
        shadow.alpha = 1f - 0.25f * f
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) = block()
    })

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) post { layoutLayers() }
    }

    /** Coloca auto, reflejo, sombra, carretera y faros con porcentajes del contenedor. */
    private fun layoutLayers() {
        val s = spec ?: return
        val pw = width.toFloat(); val ph = height.toFloat()
        if (pw <= 0f || ph <= 0f) return
        val d = context.getDrawable(s.drawable) ?: return
        val aspect = d.intrinsicHeight.toFloat() / d.intrinsicWidth

        var cw = pw * 0.88f; var ch = cw * aspect
        if (ch > ph * 0.56f) { ch = ph * 0.56f; cw = ch / aspect }
        val left = (pw - cw) / 2f
        val top = ph * 0.80f - ch

        fun place(v: View, l: Float, t: Float, w: Float, h: Float) {
            v.layoutParams = (v.layoutParams as LayoutParams).apply {
                width = w.roundToInt(); height = h.roundToInt()
                gravity = Gravity.TOP or Gravity.START
                leftMargin = l.roundToInt(); topMargin = t.roundToInt()
            }
        }
        place(car, left, top, cw, ch)
        place(carNext, left, top, cw, ch)
        // Reflejo: el mismo auto volteado, pegado a la línea de las llantas
        place(reflection, left, top + ch * s.contactY * 2f - ch, cw, ch)
        // Sombra de contacto: elipse plana bajo las llantas
        val cx = left + s.contactX * cw; val cy = top + s.contactY * ch
        place(shadow, cx - cw * 0.52f, cy - ch * 0.07f, cw * 1.04f, ch * 0.14f)
        (shadow.drawable as? GradientDrawable)?.gradientRadius = cw * 0.52f

        // Carretera
        road.setGeometry(s.vpX * pw, s.vpY * ph, s.nearX * pw, s.nearY * ph, s.nearHalfW * pw)

        // Faros: fracción del dibujo → px del contenedor; alumbran hacia el borde cercano
        fun lamps(list: List<FloatArray>) = list.map { HeadlightBeamView.Lamp(left + it[0] * cw, top + it[1] * ch, it[2] * cw) }
        val dirX = s.nearX - s.vpX; val dirY = (s.nearY - s.vpY) * ph / pw
        beams.setLamps(lamps(s.heads), lamps(s.fogs), lamps(s.tails), dirX, dirY, pw * 0.55f)
    }
}
