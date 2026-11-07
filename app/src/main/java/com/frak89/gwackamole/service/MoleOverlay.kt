package com.frak89.gwackamole.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.game.MolePainter

/**
 * Banner bianchi statici con la talpa, disegnati sopra i link filtrati nei risultati di ricerca.
 * Finestre TYPE_ACCESSIBILITY_OVERLAY: non serve il permesso "mostra sopra altre app".
 * Toccarli non apre il link: avvia il minigioco (o il blocco); la ricerca resta intatta.
 *
 * Per essere istantaneo le finestre vengono riusate e riposizionate (niente rimuovi+aggiungi a
 * ogni scansione) e con lo scroll si spostano subito del delta. Da usare solo sul thread principale.
 */
class MoleOverlay(private val ctx: Context) {
    data class Target(val bounds: Rect, val label: String)

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val views = ArrayList<BannerView>()
    private val params = ArrayList<WindowManager.LayoutParams>()
    private var active = 0

    private fun newParams(r: Rect) = WindowManager.LayoutParams(
        r.width(), r.height(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = r.left; y = r.top
        windowAnimations = 0
    }

    fun show(targets: List<Target>) {
        for ((i, t) in targets.withIndex()) {
            val b = t.bounds
            if (i < views.size) {
                val v = views[i]
                val p = params[i]
                v.setLabel(t.label)
                if (i >= active) { safe { wm.addView(v, p) } }
                if (p.x != b.left || p.y != b.top || p.width != b.width() || p.height != b.height()) {
                    p.x = b.left; p.y = b.top; p.width = b.width(); p.height = b.height()
                    safe { wm.updateViewLayout(v, p) }
                }
                v.invalidate()
            } else {
                val v = BannerView(ctx).also { it.setLabel(t.label) }
                val p = newParams(b)
                if (safe { wm.addView(v, p) }) { views.add(v); params.add(p) }
            }
        }
        // nascondi i banner in eccesso senza distruggerli (riuso al prossimo giro)
        for (i in targets.size until active) safe { wm.removeView(views[i]) }
        active = minOf(targets.size, views.size)
    }

    /** Sposta subito i banner già mostrati (reazione immediata allo scroll, prima della riscansione). */
    fun shift(dy: Int) {
        for (i in 0 until active) {
            val p = params[i]
            p.y += dy
            safe { wm.updateViewLayout(views[i], p) }
        }
    }

    fun clear() {
        for (i in 0 until active) safe { wm.removeView(views[i]) }
        active = 0
    }

    private inline fun safe(block: () -> Unit): Boolean =
        try { block(); true } catch (_: Exception) { false }

    @SuppressLint("ViewConstructor")
    private class BannerView(ctx: Context) : View(ctx) {
        private var label: String = ""
        private val d = ctx.resources.displayMetrics.density
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG)
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16 * d; typeface = Typeface.DEFAULT_BOLD
        }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * d }

        init {
            contentDescription = "Contenuto filtrato da Gwackamole. Tocca per continuare."
            isClickable = true
        }

        fun setLabel(l: String) {
            if (l != label) { label = l; invalidate() }
        }

        /** Il banner prende lo stesso colore di fondo della pagina, così si confonde con essa. */
        private fun dark(): Boolean {
            val mode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        override fun onDraw(c: Canvas) {
            val dark = dark()
            bg.color = if (dark) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt()
            title.color = if (dark) 0xFFE8E8E8.toInt() else 0xFF2F4A17.toInt()
            sub.color = if (dark) 0xFFB0B0B0.toInt() else 0xFF5C4D3D.toInt()
            // riempie tutta l'area del risultato (nessun bordo): sembra pagina vuota con una talpa
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
            val s = minOf(height * 0.8f, 64 * d)
            val top = (height - s) / 2
            MolePainter.draw(c, 10 * d, top, s, s * 1.0f)
            if (width > s + 120 * d && height > 34 * d) {
                val tx = 10 * d + s + 10 * d
                val cy = height / 2f
                val heading = if (Store.punishMode == "kick") "Contenuto bloccato" else "Qui c'è una talpa"
                c.drawText(heading, tx, cy - 2 * d, title)
                val cta = if (Store.punishMode == "kick") "Tocca per chiudere la ricerca"
                else "Tocca per sfidarla · ${(Store.attemptsToday("site:$label") + 1).coerceAtMost(10)} talpe"
                c.drawText(cta, tx, cy + 15 * d, sub)
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
