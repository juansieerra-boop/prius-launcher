package com.cargo.launcher.viewport

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
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
 *  - vp / near: carretera de día (fracciones del contenedor)
 *  - road / roadCarLeft / roadCarTop / roadCarWidth: asset nocturno y dónde está el auto dentro de
 *    él (px del asset). Con eso la carretera se alinea con las llantas en cualquier resolución.
 */
class VehicleSpec(
    val drawable: Int,
    val heads: List<FloatArray>, val fogs: List<FloatArray>, val tails: List<FloatArray>,
    val contactX: Float, val contactY: Float,
    val vpX: Float, val vpY: Float, val nearX: Float, val nearY: Float, val nearHalfW: Float,
    val road: Int, val roadCarLeft: Float, val roadCarTop: Float, val roadCarWidth: Float
) {
    companion object {
        fun of(v: VehicleType): VehicleSpec = when (v) {
            Vehicle.PRIUS_2011 -> VehicleSpec(
                R.drawable.vp_prius_2011,   // plata, rines negros
                heads = listOf(floatArrayOf(0.385f, 0.527f, 0.040f), floatArrayOf(0.046f, 0.464f, 0.022f)),
                fogs = listOf(floatArrayOf(0.396f, 0.788f, 0.012f)),
                tails = emptyList(),
                contactX = 0.53f, contactY = 0.975f,
                vpX = 1.33f, vpY = 0.59f, nearX = -0.30f, nearY = 0.89f, nearHalfW = 0.45f,
                road = R.drawable.vp_road_prius, roadCarLeft = 58f, roadCarTop = 476f, roadCarWidth = 850f
            )
            Vehicle.JEEP_SAHARA_2021 -> VehicleSpec(
                R.drawable.vp_jeep_sahara_caro,   // blanco, placa CARO
                heads = listOf(floatArrayOf(0.676f, 0.438f, 0.030f), floatArrayOf(0.891f, 0.409f, 0.025f)),
                fogs = listOf(floatArrayOf(0.696f, 0.678f, 0.011f), floatArrayOf(0.955f, 0.629f, 0.010f)),
                tails = listOf(floatArrayOf(0.017f, 0.368f, 0.012f)),
                contactX = 0.50f, contactY = 0.975f,
                vpX = -0.48f, vpY = 0.61f, nearX = 1.30f, nearY = 0.86f, nearHalfW = 0.45f,
                road = R.drawable.vp_road_jeep, roadCarLeft = 58f, roadCarTop = 439f, roadCarWidth = 850f
            )
        }
    }
}

/**
 * Contenedor de las capas del vehículo. Todo es relativo al contenedor:
 * auto = 88 % del ancho (máx. 56 % del alto), apoyado al 80 % de la altura.
 *
 * Cambios sin parpadeo:
 *  - Día ↔ noche: un ValueAnimator mezcla escenario, carretera, faros y reflejo en el mismo cuadro.
 *  - Cambio de auto: ivVehicleModelNext recibe el nuevo dibujo y se cruza con ivVehicleModel
 *    (crossfade); al terminar se intercambian las referencias. Nunca se deja la capa vacía.
 */
class VehicleViewport @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {

    private lateinit var sceneDay: ImageView
    lateinit var road: PerspectiveRoadView; private set
    private lateinit var roadNight: ImageView
    private lateinit var roadOverlay: ImageView
    private lateinit var beams: HeadlightBeamView
    private lateinit var reflection: ImageView
    private lateinit var shadow: ImageView
    private lateinit var car: ImageView
    private lateinit var carNext: ImageView
    private lateinit var glow: HeadlightBeamView

    private var spec: VehicleSpec? = null
    var vehicle: VehicleType? = null; private set
    var isNight = false; private set
    private var nightFraction = 0f
    private var modeAnim: ValueAnimator? = null

    /** Tinte del HUD que corresponde al modo (cian neón de noche, ámbar de día). */
    val hudTint: Int get() = if (isNight) NIGHT_TINT else DAY_TINT

    companion object {
        const val NIGHT_TINT = 0xFF22D3EE.toInt()
        const val DAY_TINT = 0xFFFF9F43.toInt()
        private const val FADE_MS = 450L
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        sceneDay = findViewById(R.id.ivSceneDay)
        road = findViewById(R.id.vpRoad)
        roadNight = findViewById(R.id.ivRoadBackground)
        roadOverlay = findViewById(R.id.ivRoadOverlay)
        beams = findViewById(R.id.ivHeadlightBeam)
        reflection = findViewById(R.id.ivReflection)
        shadow = findViewById(R.id.ivCarShadow)
        car = findViewById(R.id.ivVehicleModel)
        carNext = findViewById(R.id.ivVehicleModelNext)
        glow = findViewById(R.id.ivLampGlow)
        beams.layer = HeadlightBeamView.Layer.CONES
        glow.layer = HeadlightBeamView.Layer.FLARES
        road.setAccent(DAY_TINT, NIGHT_TINT)
        shadow.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf(Color.argb(160, 0, 0, 0), Color.argb(70, 0, 0, 0), Color.TRANSPARENT)
        })
    }

    // ------------------------------------------------------------------ API

    /** Carga el auto. Si ya había otro, hace crossfade; la primera vez lo pone directo. */
    fun setVehicle(v: VehicleType, animate: Boolean = true) {
        if (v == vehicle) return
        val first = vehicle == null
        vehicle = v
        val s = VehicleSpec.of(v)
        spec = s
        roadNight.setImageResource(s.road)
        if (first || !animate || !isLaidOut) {
            car.setImageResource(s.drawable); reflection.setImageResource(s.drawable)
            layoutLayers()
            return
        }
        carNext.animate().cancel(); car.animate().cancel()
        carNext.setImageResource(s.drawable)
        carNext.alpha = 0f
        layoutLayers()                       // coloca las dos capas con la proporción del nuevo auto
        carNext.animate().alpha(1f).setDuration(FADE_MS).start()
        car.animate().alpha(0f).setDuration(FADE_MS).withEndAction {
            val old = car; car = carNext; carNext = old   // la capa visible pasa a ser la del auto nuevo
            carNext.setImageDrawable(null)
            car.bringToFront(); glow.bringToFront()
            reflection.setImageResource(s.drawable)
        }.start()
    }

    /** Día / noche con transición suave (sin recrear vistas). */
    fun setNightMode(night: Boolean, animate: Boolean = true) {
        val target = if (night) 1f else 0f
        if (night == isNight && modeAnim == null && nightFraction == target) return
        isNight = night
        modeAnim?.cancel()
        if (night) { beams.visibility = View.VISIBLE; glow.visibility = View.VISIBLE }
        if (!animate) { applyNight(target); hideBeamsIfDay(); return }
        modeAnim = ValueAnimator.ofFloat(nightFraction, target).apply {
            duration = FADE_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { applyNight(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { modeAnim = null; hideBeamsIfDay() }
            })
            start()
        }
    }

    /** Velocidad del OBD2: mueve las rayas de la carretera de día. */
    fun setSpeedMph(mph: Float) = road.setSpeedMph(mph)

    /** false mientras otra app (ZLink) está encima: no se anima nada. */
    var running: Boolean
        get() = road.running
        set(v) { road.running = v }

    // ------------------------------------------------------------------ interno

    private fun hideBeamsIfDay() {
        if (!isNight) { beams.visibility = View.INVISIBLE; glow.visibility = View.INVISIBLE }
    }

    private fun applyNight(f: Float) {
        nightFraction = f
        sceneDay.alpha = 1f - f
        road.alpha = 1f - f
        road.setNightFraction(f)
        roadNight.alpha = f
        roadOverlay.alpha = f
        beams.alpha = f
        glow.alpha = f
        reflection.alpha = 0.09f * f
        shadow.alpha = 1f - 0.2f * f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) post { layoutLayers() }
    }

    /** Coloca auto, carretera, sombra, reflejo y faros con porcentajes del contenedor. */
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
        place(reflection, left, top + ch * s.contactY * 2f - ch, cw, ch)   // espejo sobre la línea de llantas
        val cx = left + s.contactX * cw; val cy = top + s.contactY * ch
        place(shadow, cx - cw * 0.52f, cy - ch * 0.07f, cw * 1.04f, ch * 0.14f)
        (shadow.drawable as? GradientDrawable)?.gradientRadius = cw * 0.52f

        // Carretera nocturna: el asset se escala igual que el auto y se mueve para que el auto
        // quede exactamente donde estaba al dibujarla (sobre el carril, siguiendo su dirección).
        val k = cw / s.roadCarWidth
        roadNight.imageMatrix = Matrix().apply {
            setScale(k, k)
            postTranslate(left - s.roadCarLeft * k, top - s.roadCarTop * k)
        }
        // Si no alcanza a cubrir el contenedor, su fondo (#07090D) se funde con la orilla oscura del asset.

        // Carretera de día (código)
        road.setGeometry(s.vpX * pw, s.vpY * ph, s.nearX * pw, s.nearY * ph, s.nearHalfW * pw)

        // Faros: fracción del dibujo → px del contenedor; alumbran hacia adelante del auto
        fun lamps(list: List<FloatArray>) = list.map { HeadlightBeamView.Lamp(left + it[0] * cw, top + it[1] * ch, it[2] * cw) }
        val dirX = s.nearX - s.vpX; val dirY = (s.nearY - s.vpY) * ph / pw
        beams.setLamps(lamps(s.heads), lamps(s.fogs), lamps(s.tails), dirX, dirY, pw * 0.55f)
        glow.setLamps(lamps(s.heads), lamps(s.fogs), lamps(s.tails), dirX, dirY, pw * 0.55f)
    }
}
