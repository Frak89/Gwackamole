package com.frak89.gwackamole.game

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Disegna la talpa di Gwackamole (vettoriale, nessuna immagine esterna). */
object MolePainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val path = Path()
    private val r = RectF()

    const val BODY = 0xFF7A5A44.toInt()
    const val BODY_HIT = 0xFFA4876B.toInt()
    const val GOLD = 0xFFE0A526.toInt()
    const val SNOUT = 0xFFD9B48F.toInt()
    const val NOSE = 0xFFE8798A.toInt()
    const val EYE = 0xFF1B130C.toInt()

    enum class Mood { HAPPY, BONKED, ANGRY, SMUG }

    /**
     * Disegna una talpa nel riquadro [l,t,w,h] (proporzioni consigliate 1:1.1).
     * @param golden talpa d'oro (vale doppio)
     * @param helmet caschetto: è un'esca da NON colpire
     */
    fun draw(c: Canvas, l: Float, t: Float, w: Float, h: Float, mood: Mood = Mood.HAPPY,
             golden: Boolean = false, helmet: Boolean = false) {
        val cx = l + w / 2
        // corpo
        p.color = when { mood == Mood.BONKED -> BODY_HIT; golden -> GOLD; else -> BODY }
        r.set(l + w * 0.1f, t + h * 0.12f, l + w * 0.9f, t + h * 1.1f)
        c.drawOval(r, p)
        // pancia / muso
        p.color = SNOUT
        r.set(cx - w * 0.25f, t + h * 0.5f, cx + w * 0.25f, t + h * 0.88f)
        c.drawOval(r, p)
        // zampette appoggiate al bordo
        p.color = if (golden) 0xFFC98E1C.toInt() else 0xFF6A4C38.toInt()
        r.set(l + w * 0.05f, t + h * 0.82f, l + w * 0.32f, t + h * 0.98f); c.drawOval(r, p)
        r.set(l + w * 0.68f, t + h * 0.82f, l + w * 0.95f, t + h * 0.98f); c.drawOval(r, p)
        p.color = 0xFFF2D7BD.toInt()
        for (i in 0..2) {
            c.drawCircle(l + w * (0.11f + i * 0.07f), t + h * 0.84f, w * 0.022f, p)
            c.drawCircle(l + w * (0.75f + i * 0.07f), t + h * 0.84f, w * 0.022f, p)
        }
        // occhi
        val ey = t + h * 0.42f
        val ex1 = cx - w * 0.15f
        val ex2 = cx + w * 0.15f
        val er = w * 0.055f
        when (mood) {
            Mood.BONKED -> {
                stroke.color = EYE; stroke.strokeWidth = w * 0.03f
                for (ex in floatArrayOf(ex1, ex2)) {
                    c.drawLine(ex - er, ey - er, ex + er, ey + er, stroke)
                    c.drawLine(ex - er, ey + er, ex + er, ey - er, stroke)
                }
            }
            else -> {
                p.color = EYE
                c.drawCircle(ex1, ey, er, p); c.drawCircle(ex2, ey, er, p)
                p.color = Color.WHITE
                c.drawCircle(ex1 + er * 0.35f, ey - er * 0.35f, er * 0.35f, p)
                c.drawCircle(ex2 + er * 0.35f, ey - er * 0.35f, er * 0.35f, p)
                if (mood == Mood.ANGRY || mood == Mood.SMUG) {
                    stroke.color = EYE; stroke.strokeWidth = w * 0.035f
                    val dy = if (mood == Mood.ANGRY) 1 else -1
                    c.drawLine(ex1 - er * 1.6f, ey - er * (2.2f + dy * 0.6f), ex1 + er * 1.2f, ey - er * (2.2f - dy * 0.6f), stroke)
                    c.drawLine(ex2 + er * 1.6f, ey - er * (2.2f + dy * 0.6f), ex2 - er * 1.2f, ey - er * (2.2f - dy * 0.6f), stroke)
                }
            }
        }
        // naso
        p.color = NOSE
        r.set(cx - w * 0.09f, t + h * 0.53f, cx + w * 0.09f, t + h * 0.65f)
        c.drawOval(r, p)
        // dentini
        p.color = Color.WHITE
        c.drawRoundRect(cx - w * 0.05f, t + h * 0.66f, cx - w * 0.004f, t + h * 0.73f, 3f, 3f, p)
        c.drawRoundRect(cx + w * 0.004f, t + h * 0.66f, cx + w * 0.05f, t + h * 0.73f, 3f, 3f, p)
        // baffi
        stroke.color = 0xFF5A3E2A.toInt(); stroke.strokeWidth = w * 0.015f
        c.drawLine(cx - w * 0.2f, t + h * 0.6f, cx - w * 0.38f, t + h * 0.56f, stroke)
        c.drawLine(cx - w * 0.2f, t + h * 0.64f, cx - w * 0.38f, t + h * 0.66f, stroke)
        c.drawLine(cx + w * 0.2f, t + h * 0.6f, cx + w * 0.38f, t + h * 0.56f, stroke)
        c.drawLine(cx + w * 0.2f, t + h * 0.64f, cx + w * 0.38f, t + h * 0.66f, stroke)
        // caschetto da minatore = esca
        if (helmet) {
            p.color = 0xFFF2B705.toInt()
            path.reset()
            r.set(l + w * 0.14f, t + h * 0.02f, l + w * 0.86f, t + h * 0.5f)
            path.arcTo(r, 180f, 180f)
            path.close()
            c.drawPath(path, p)
            p.color = 0xFFD99A00.toInt()
            c.drawRect(l + w * 0.08f, t + h * 0.24f, l + w * 0.92f, t + h * 0.3f, p)
            p.color = Color.WHITE
            c.drawCircle(cx, t + h * 0.14f, w * 0.06f, p)
            p.color = 0xFFB42318.toInt()
            c.drawCircle(cx, t + h * 0.14f, w * 0.035f, p)
        }
        if (golden && mood != Mood.BONKED) {
            p.color = Color.WHITE
            drawSparkle(c, l + w * 0.85f, t + h * 0.18f, w * 0.07f)
            drawSparkle(c, l + w * 0.18f, t + h * 0.3f, w * 0.05f)
        }
    }

    fun drawSparkle(c: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.lineTo(x + s * 0.3f, y - s * 0.3f); path.lineTo(x + s, y)
        path.lineTo(x + s * 0.3f, y + s * 0.3f); path.lineTo(x, y + s); path.lineTo(x - s * 0.3f, y + s * 0.3f)
        path.lineTo(x - s, y); path.lineTo(x - s * 0.3f, y - s * 0.3f); path.close()
        c.drawPath(path, p)
    }

    fun drawStar(c: Canvas, x: Float, y: Float, s: Float, color: Int) {
        p.color = color
        path.reset()
        for (i in 0 until 10) {
            val a = Math.PI / 5 * i - Math.PI / 2
            val rr = if (i % 2 == 0) s else s * 0.45f
            val px = x + (Math.cos(a) * rr).toFloat()
            val py = y + (Math.sin(a) * rr).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        c.drawPath(path, p)
    }
}
