package com.juandiego.priuslauncher.v4

import android.app.Activity
import android.content.Intent
import android.graphics.Outline
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import com.juandiego.priuslauncher.R
import kotlin.math.abs

/**
 * Menú de apps estilo CarPlay: íconos grandes en 5 x 2, por páginas (deslizar para cambiar).
 * Va debajo de la barra de música, que se queda fija arriba.
 */
class AppsGrid(private val u: U, private val act: Activity, private val onClose: () -> Unit) : Card(u) {

    private class App(val label: String, val icon: Drawable?, val intent: Intent)

    private companion object {
        const val COLS = 5
        const val ROWS = 2
        const val PER_PAGE = COLS * ROWS
    }

    private val pagesBox = FrameLayout(u.ctx)
    private val dots = LinearLayout(u.ctx)
    private var apps: List<App> = emptyList()
    private var page = 0
    private val pages = ArrayList<View>()
    private val main = Handler(Looper.getMainLooper())

    private val gestures = GestureDetector(u.ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > abs(vy) && abs(vx) > 500f) {
                go(page + if (vx < 0) 1 else -1)
                return true
            }
            return false
        }
    })

    init {
        isClickable = true
        addView(pagesBox, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        addView(dots, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, u.i(8f), Gravity.BOTTOM).apply { bottomMargin = u.i(22f) })
        load()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return true
    }

    /** Lista de apps en segundo plano (los íconos tardan un poco en cargar). */
    private fun load() {
        Thread {
            val pm = act.packageManager
            val list = try {
                pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                    .filter { it.activityInfo.packageName != act.packageName }
                    .map {
                        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setClassName(it.activityInfo.packageName, it.activityInfo.name)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                        App(it.loadLabel(pm).toString(), try { it.loadIcon(pm) } catch (e: Exception) { null }, i)
                    }
                    .distinctBy { it.intent.component?.flattenToString() }
                    .sortedBy { it.label.lowercase() }
            } catch (e: Exception) {
                emptyList()
            }
            main.post {
                if (!isAttachedToWindow && parent == null) return@post
                apps = list
                build()
            }
        }.start()
    }

    private fun build() {
        pagesBox.removeAllViews()
        pages.clear()
        val count = maxOf(1, (apps.size + PER_PAGE - 1) / PER_PAGE)
        for (p in 0 until count) {
            val grid = LinearLayout(u.ctx).apply { orientation = LinearLayout.VERTICAL }
            for (r in 0 until ROWS) {
                val row = LinearLayout(u.ctx).apply { orientation = LinearLayout.HORIZONTAL }
                for (c in 0 until COLS) {
                    val idx = p * PER_PAGE + r * COLS + c
                    val cell = if (idx < apps.size) cell(apps[idx]) else View(u.ctx)
                    row.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                }
                grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (r > 0) topMargin = u.i(40f)
                })
            }
            val holder = FrameLayout(u.ctx)
            holder.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = u.i(56f); leftMargin = u.i(60f); rightMargin = u.i(60f)
            })
            pagesBox.addView(holder, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            pages.add(holder)
        }
        page = page.coerceIn(0, pages.size - 1)
        layoutPages(false)
    }

    private fun cell(a: App): View = LinearLayout(u.ctx).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val iv = ImageView(u.ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (a.icon != null) setImageDrawable(a.icon) else {
                setImageResource(R.drawable.v4_ic_apps)
                setColorFilter(u.pal.sub)
                background = u.round(u.pal.btn, 26f)
                val pad = u.i(34f)
                setPadding(pad, pad, pad, pad)
            }
            // esquinas redondeadas como CarPlay
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, u.px(26f))
            }
            clipToOutline = true
            elevation = u.px(if (u.pal.dark) 4f else 2f)
        }
        addView(iv, LinearLayout.LayoutParams(u.i(112f), u.i(112f)))
        addView(u.text(15f, 600, u.pal.tx, a.label).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = u.i(12f) })
        setOnClickListener {
            if (startSafe(act, a.intent)) onClose()
            else android.widget.Toast.makeText(act, R.string.v4_cant_open, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun go(p: Int) {
        if (p < 0 || p >= pages.size || p == page) return
        page = p
        layoutPages(true)
    }

    private fun layoutPages(animate: Boolean) {
        val w = if (width > 0) width.toFloat() else u.px(1047f)
        pages.forEachIndexed { i, v ->
            val x = (i - page) * w
            if (animate) v.animate().translationX(x).setDuration(260).start() else v.translationX = x
        }
        dots.removeAllViews()
        if (pages.size > 1) for (i in pages.indices) {
            dots.addView(View(u.ctx).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (i == page) u.pal.sub else u.pal.line) }
            }, LinearLayout.LayoutParams(u.i(8f), u.i(8f)).apply { leftMargin = u.i(4f); rightMargin = u.i(4f) })
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutPages(false)
    }
}
