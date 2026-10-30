package com.frak89.gwackamole.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.game.MolePainter

/**
 * Banner bianchi statici con la talpa disegnati sopra i link vietati nei risultati di ricerca.
 * Sono finestre TYPE_ACCESSIBILITY_OVERLAY: non serve il permesso "mostra sopra altre app".
 * Toccare il banner non apre il link: fa partire il minigioco, la ricerca resta intatta.
 */
class MoleOverlay(private val ctx: Context) {
    data class Target(val bounds: Rect, val label: String)

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val shown = ArrayList<Pair<Target, View>>()

    fun show(targets: List<Target>) {
        if (targets.map { it.bounds } == shown.map { it.first.bounds }) return
        clear()
        for (t in targets) {
            val v = BannerView(ctx, t.label)
            val lp = WindowManager.LayoutParams(
                t.bounds.width(), t.bounds.height(),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = t.bounds.left
                y = t.bounds.top
            }
            try {
                wm.addView(v, lp)
                shown.add(t to v)
            } catch (_: Exception) { }
        }
    }

    fun clear() {
        for ((_, v) in shown) try { wm.removeView(v) } catch (_: Exception) { }
        shown.clear()
    }

    @SuppressLint("ViewConstructor")
    private class BannerView(ctx: Context, private val label: String) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
        private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFC9BFA8.toInt(); style = Paint.Style.STROKE; strokeWidth = 2 * d
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF2F4A17.toInt(); textSize = 16 * d; typeface = Typeface.DEFAULT_BOLD
        }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF5C4D3D.toInt(); textSize = 12 * d }
        private val rr = RectF()

        init {
            contentDescription = "Contenuto filtrato da Gwackamole. Tocca per giocare."
            isClickable = true
        }

        override fun onDraw(c: Canvas) {
            rr.set(1f, 1f, width - 1f, height - 1f)
            c.drawRoundRect(rr, 14 * d, 14 * d, bg)
            c.drawRoundRect(rr, 14 * d, 14 * d, border)
            val s = minOf(height * 0.8f, 64 * d)
            val top = (height - s) / 2
            MolePainter.draw(c, 10 * d, top, s, s * 1.0f)
            if (width > s + 120 * d && height > 34 * d) {
                val tx = 10 * d + s + 10 * d
                val cy = height / 2f
                c.drawText("Qui c'è una talpa", tx, cy - 2 * d, title)
                val tries = Store.attemptsToday("site:$label")
                c.drawText("Tocca per sfidarla · ${(tries + 1).coerceAtMost(10)} talpe", tx, cy + 15 * d, sub)
            }
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) performClick()
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            Guardian.onCoveredLinkTapped(label)
            return true
        }
    }
}
