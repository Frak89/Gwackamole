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

/**
 * Banner che coprono i link filtrati nei risultati di ricerca. Finestre
 * TYPE_ACCESSIBILITY_OVERLAY: non serve il permesso "mostra sopra altre app".
 * Hanno lo stesso colore di fondo della pagina (chiaro o scuro) così si confondono con essa.
 * Toccarli blocca il telefono; la ricerca resta intatta.
 *
 * Per essere istantaneo le finestre vengono riusate e riposizionate (niente rimuovi+aggiungi a
 * ogni scansione) e con lo scroll si spostano subito del delta. Da usare solo sul thread principale.
 */
class BlockOverlay(private val ctx: Context) {
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
                if (i >= active) safe { wm.addView(v, p) }
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
        for (i in targets.size until active) safe { wm.removeView(views[i]) }
        active = minOf(targets.size, views.size)
    }

    /** Sposta subito i banner già mostrati (reazione immediata allo scroll). */
    fun shift(dy: Int) {
        for (i in 0 until active) {
            params[i].y += dy
            safe { wm.updateViewLayout(views[i], params[i]) }
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
        private val block = Paint(Paint.ANTI_ALIAS_FLAG)
        private val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
        }
        private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16 * d; typeface = Typeface.DEFAULT_BOLD
        }
        private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12 * d }
        private val rr = RectF()

        init {
            contentDescription = "Contenuto bloccato da Nero Block. Tocca per continuare."
            isClickable = true
        }

        fun setLabel(l: String) { if (l != label) { label = l; invalidate() } }

        private fun dark(): Boolean {
            val m = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            return m == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        override fun onDraw(c: Canvas) {
            val dark = dark()
            bg.color = if (dark) 0xFF1F1F1F.toInt() else 0xFFFFFFFF.toInt()
            title.color = if (dark) 0xFFF0F0F0.toInt() else 0xFF141414.toInt()
            sub.color = if (dark) 0xFFB0B0B0.toInt() else 0xFF5C5C5C.toInt()
            // riempie tutta l'area del risultato (nessun bordo): sembra pagina vuota con un blocco
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
            // icona: quadrato nero con divieto
            val s = minOf(height * 0.7f, 48 * d)
            val left = 12 * d
            val top = (height - s) / 2
            block.color = 0xFF141414.toInt()
            rr.set(left, top, left + s, top + s)
            c.drawRoundRect(rr, s * 0.22f, s * 0.22f, block)
            slash.strokeWidth = s * 0.1f
            val cx = left + s / 2
            val cy = top + s / 2
            val r = s * 0.28f
            c.drawCircle(cx, cy, r, slash)
            c.drawLine(cx - r * 0.7f, cy - r * 0.7f, cx + r * 0.7f, cy + r * 0.7f, slash)
            if (width > s + 120 * d && height > 34 * d) {
                val tx = left + s + 12 * d
                c.drawText("Contenuto bloccato", tx, cy - 2 * d, title)
                c.drawText("Tocca per continuare", tx, cy + 15 * d, sub)
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
