package com.frak89.gwackamole.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Acchiappa-talpe a difficoltà progressiva.
 *
 * - seed (1..10) = quante talpe devi martellare (sale se insisti sullo stesso obiettivo).
 * - difficulty (0..8) = quanto sono veloci, quante insieme, quante esche col caschetto.
 * - Dentro al round: ogni 2 talpe sali di livello e tutto accelera.
 * - Talpa d'oro = vale 2. Talpa col caschetto = esca: se la colpisci perdi una vita.
 * - 3 talpe che scappano (o esche colpite) = le talpe vincono.
 */
@SuppressLint("ViewConstructor")
class MoleGameView(
    ctx: Context,
    private val seed: Int,
    private val difficulty: Int,
    private val subtitle: String,
    private val winText: String,
    private val loseText: String,
    private val onEnd: (Boolean) -> Unit,
) : View(ctx) {

    private enum class St { DOWN, RISING, UP, HIT, FALLING }
    private enum class Kind { NORMAL, GOLD, DECOY }

    private class Hole {
        var st = St.DOWN
        var kind = Kind.NORMAL
        var t0 = 0L
        var upDur = 1000L
        val rect = RectF() // area della buca (dove esce la talpa)
    }

    private class Particle(var x: Float, var y: Float, val vx: Float, val vy: Float, val t0: Long, val text: String?, val color: Int)

    private val d = resources.displayMetrics.density
    private val cols = 3
    private val rows = if (seed >= 6) 4 else 3
    private val holes = List(cols * rows) { Hole() }
    private val particles = ArrayList<Particle>()

    private val target = seed.coerceIn(1, 10)
    private val maxLives = if (difficulty >= 5) 2 else 3
    private val timeLimitMs = (max(9.0, 10 + target * 3.2 - difficulty * 0.6) * 1000).toLong()

    private var hits = 0
    private var lives = maxLives
    private var phase = 0 // 0 intro, 1 gioco, 2 fine
    private var won = false
    private var startAt = 0L
    private var playStart = 0L
    private var endAt = 0L
    private var nextSpawn = 0L
    private var shakeUntil = 0L
    private var hammerX = -1f
    private var hammerY = -1f
    private var hammerAt = 0L
    private var ended = false
    private var combo = 0

    private val field = RectF()
    private val tmp = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }

    init {
        isClickable = true
        contentDescription = "Minigioco: martella $target talpe"
    }

    private fun level() = 1 + hits / 2

    private fun upDuration(): Long {
        val base = 1150.0 * 0.92.pow(difficulty) * 0.94.pow(hits)
        return max(380.0, base).toLong()
    }

    private fun maxUp() = min(3, 1 + hits / 2 + difficulty / 3)
    private fun decoyChance() = if (difficulty >= 2 || target >= 5) min(0.3, 0.12 + 0.025 * difficulty + 0.01 * hits) else 0.0

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val top = h * 0.24f
        val pad = 14 * d
        field.set(pad, top, w - pad, h - 70 * d)
        val cw = field.width() / cols
        val ch = field.height() / rows
        holes.forEachIndexed { i, hole ->
            val c = i % cols
            val r = i / cols
            val hw = cw * 0.78f
            val hh = min(ch * 0.82f, hw * 1.15f)
            val cx = field.left + cw * (c + 0.5f)
            val cy = field.top + ch * (r + 0.5f)
            hole.rect.set(cx - hw / 2, cy - hh / 2, cx + hw / 2, cy + hh / 2)
        }
    }

    // ------------------------------------------------------------------ logica

    private fun update(now: Long) {
        if (startAt == 0L) startAt = now
        if (phase == 0 && now - startAt > 2400) {
            phase = 1; playStart = now; nextSpawn = now + 250
        }
        if (phase == 1) {
            for (h in holes) when (h.st) {
                St.RISING -> if (now - h.t0 > 130) { h.st = St.UP; h.t0 = now }
                St.UP -> if (now - h.t0 > h.upDur) {
                    h.st = St.FALLING; h.t0 = now
                    if (h.kind != Kind.DECOY) loseLife(now, h)
                }
                St.HIT -> if (now - h.t0 > 320) { h.st = St.FALLING; h.t0 = now }
                St.FALLING -> if (now - h.t0 > 140) h.st = St.DOWN
                St.DOWN -> Unit
            }
            if (now >= nextSpawn && phase == 1) {
                spawn(now)
                val up = upDuration()
                nextSpawn = now + (up * (0.35 + Random.nextDouble() * 0.55)).toLong()
            }
            if (now - playStart > timeLimitMs) finish(now, hits >= target)
        }
        particles.removeAll { now - it.t0 > 700 }
        if (phase == 2 && !ended && now - endAt > 2300) {
            ended = true
            onEnd(won)
        }
    }

    private fun spawn(now: Long) {
        val upCount = holes.count { it.st == St.UP || it.st == St.RISING }
        if (upCount >= maxUp()) return
        val free = holes.filter { it.st == St.DOWN }
        if (free.isEmpty()) return
        val h = free[Random.nextInt(free.size)]
        h.kind = when {
            Random.nextDouble() < decoyChance() -> Kind.DECOY
            Random.nextDouble() < 0.08 -> Kind.GOLD
            else -> Kind.NORMAL
        }
        h.st = St.RISING
        h.t0 = now
        h.upDur = if (h.kind == Kind.GOLD) (upDuration() * 0.7).toLong() else upDuration()
    }

    private fun loseLife(now: Long, h: Hole) {
        lives--
        combo = 0
        shakeUntil = now + 300
        particles.add(Particle(h.rect.centerX(), h.rect.top, 0f, -0.08f * d, now,
            if (h.kind == Kind.DECOY) "ahia!" else "scappata!", 0xFFB42318.toInt()))
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (lives <= 0) finish(now, false)
    }

    private fun finish(now: Long, w: Boolean) {
        if (phase == 2) return
        phase = 2; won = w; endAt = now
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_DOWN && e.actionMasked != MotionEvent.ACTION_POINTER_DOWN) return true
        val idx = e.actionIndex
        val x = e.getX(idx)
        val y = e.getY(idx)
        val now = SystemClock.uptimeMillis()
        hammerX = x; hammerY = y; hammerAt = now
        if (phase != 1) return true
        val h = holes.firstOrNull { (it.st == St.UP || it.st == St.RISING) && hitBox(it).contains(x, y) } ?: run {
            combo = 0
            return true
        }
        h.st = St.HIT
        h.t0 = now
        if (h.kind == Kind.DECOY) {
            loseLife(now, h)
            return true
        }
        val pts = if (h.kind == Kind.GOLD) 2 else 1
        hits += pts
        combo++
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        repeat(6) {
            val a = Random.nextDouble(0.0, Math.PI * 2)
            particles.add(Particle(h.rect.centerX(), h.rect.centerY(),
                (kotlin.math.cos(a) * 0.25 * d).toFloat(), (sin(a) * 0.25 * d).toFloat(), now, null,
                if (h.kind == Kind.GOLD) 0xFFE0A526.toInt() else 0xFFFFD34D.toInt()))
        }
        particles.add(Particle(h.rect.centerX(), h.rect.top, 0f, -0.1f * d, now,
            if (combo >= 3) "+$pts  combo x$combo" else "+$pts", 0xFFFFFFFF.toInt()))
        if (hits >= target) finish(now, true)
        return true
    }

    private fun hitBox(h: Hole): RectF {
        tmp.set(h.rect.left - 8 * d, h.rect.top - 8 * d, h.rect.right + 8 * d, h.rect.bottom + 8 * d)
        return tmp
    }

    // ------------------------------------------------------------------ disegno

    override fun onDraw(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        update(now)
        val w = width.toFloat()
        val h = height.toFloat()

        c.drawColor(0xFFF7F1E3.toInt())
        c.save()
        if (now < shakeUntil) c.translate((sin(now / 18.0) * 7 * d).toFloat(), 0f)

        drawHeader(c, w, h, now)

        // prato
        paint.color = 0xFF5B8C32.toInt()
        c.drawRoundRect(field, 28 * d, 28 * d, paint)
        paint.color = 0xFF66983A.toInt()
        var sy = field.top + 18 * d
        while (sy < field.bottom - 10 * d) {
            c.drawRoundRect(field.left + 12 * d, sy, field.right - 12 * d, sy + 6 * d, 3 * d, 3 * d, paint)
            sy += 46 * d
        }

        for (hole in holes) drawHole(c, hole, now)

        // particelle
        for (p in particles) {
            val t = (now - p.t0).toFloat()
            val px = p.x + p.vx * t
            val py = p.y + p.vy * t
            if (p.text != null) {
                text.color = p.color; text.textSize = 20 * d
                text.setShadowLayer(4 * d, 0f, 1 * d, 0x88000000.toInt())
                c.drawText(p.text, px, py, text)
                text.clearShadowLayer()
            } else {
                MolePainter.drawStar(c, px, py, 9 * d * (1 - t / 800f), p.color)
            }
        }

        drawHammer(c, now)
        c.restore()

        if (phase == 0) drawIntro(c, w, h, now)
        if (phase == 2) drawEnd(c, w, h, now)

        // footer
        text.color = 0xFF4A3B2C.toInt(); text.textSize = 13 * d
        c.drawText("Uscita bloccata finché il round non finisce", w / 2, h - 30 * d, text)

        if (!ended) postInvalidateOnAnimation()
    }

    private fun drawHeader(c: Canvas, w: Float, h: Float, now: Long) {
        val left = 22 * d
        text.textAlign = Paint.Align.LEFT
        text.color = 0xFF6B5A45.toInt(); text.textSize = 12 * d
        c.drawText(subtitle.uppercase(), left, 40 * d, text)
        text.color = 0xFF2F4A17.toInt(); text.textSize = 30 * d
        c.drawText("Gwackamole!", left, 74 * d, text)
        text.textAlign = Paint.Align.RIGHT
        text.color = 0xFF6B5A45.toInt(); text.textSize = 12 * d
        c.drawText("LIVELLO", w - left, 40 * d, text)
        text.color = 0xFFC2410C.toInt(); text.textSize = 30 * d
        c.drawText("${level()}", w - left, 74 * d, text)
        text.textAlign = Paint.Align.CENTER

        // talpe da colpire (pallini) e vite (cuori)
        val dotsY = 100 * d
        val dotR = 7 * d
        val gap = 20 * d
        for (i in 0 until target) {
            paint.color = if (i < hits) 0xFF2F4A17.toInt() else 0xFFE6DCC5.toInt()
            c.drawCircle(left + dotR + i * gap, dotsY, dotR, paint)
        }
        for (i in 0 until maxLives) {
            paint.color = if (i < lives) 0xFFC2410C.toInt() else 0xFFE6DCC5.toInt()
            drawHeart(c, w - left - 10 * d - i * 26 * d, dotsY, 10 * d)
        }
        // barra del tempo
        val frac = if (phase == 1) 1f - ((now - playStart).toFloat() / timeLimitMs).coerceIn(0f, 1f) else if (phase == 0) 1f else 0f
        paint.color = 0xFFE6DCC5.toInt()
        c.drawRoundRect(left, 122 * d, w - left, 132 * d, 5 * d, 5 * d, paint)
        paint.color = if (frac < 0.25f) 0xFFC2410C.toInt() else 0xFF5B8C32.toInt()
        c.drawRoundRect(left, 122 * d, left + (w - 2 * left) * frac, 132 * d, 5 * d, 5 * d, paint)
        text.color = 0xFF4A3B2C.toInt(); text.textSize = 13 * d
        val secs = if (phase == 1) ((timeLimitMs - (now - playStart)) / 1000).coerceAtLeast(0) else timeLimitMs / 1000
        c.drawText("$hits / $target talpe  ·  ${secs}s", w / 2, 156 * d, text)
    }

    private val heartPath = android.graphics.Path()
    private fun drawHeart(c: Canvas, x: Float, y: Float, s: Float) {
        heartPath.reset()
        heartPath.moveTo(x, y + s * 0.9f)
        heartPath.cubicTo(x - s * 1.4f, y - s * 0.1f, x - s * 0.6f, y - s * 1.2f, x, y - s * 0.4f)
        heartPath.cubicTo(x + s * 0.6f, y - s * 1.2f, x + s * 1.4f, y - s * 0.1f, x, y + s * 0.9f)
        c.drawPath(heartPath, paint)
    }

    private fun drawHole(c: Canvas, hole: Hole, now: Long) {
        val r = hole.rect
        val holeH = r.width() * 0.32f
        val baseY = r.bottom - holeH / 2
        // fondo della buca
        paint.color = 0xFF3B2A1A.toInt()
        c.drawOval(r.left, baseY - holeH / 2, r.right, baseY + holeH / 2, paint)

        val visible = when (hole.st) {
            St.DOWN -> 0f
            St.RISING -> ((now - hole.t0) / 130f).coerceIn(0f, 1f)
            St.UP, St.HIT -> 1f
            St.FALLING -> 1f - ((now - hole.t0) / 140f).coerceIn(0f, 1f)
        }
        if (visible > 0f) {
            val mw = r.width() * 0.86f
            val mh = r.height() * 0.95f
            val ml = r.centerX() - mw / 2
            val mt = baseY - mh * visible + mh * 0.08f
            c.save()
            c.clipRect(r.left - 20 * d, r.top - 30 * d, r.right + 20 * d, baseY)
            val mood = when {
                hole.st == St.HIT && hole.kind == Kind.DECOY -> MolePainter.Mood.SMUG
                hole.st == St.HIT -> MolePainter.Mood.BONKED
                hole.kind == Kind.DECOY -> MolePainter.Mood.SMUG
                hole.st == St.UP && now - hole.t0 > hole.upDur * 0.6 -> MolePainter.Mood.ANGRY
                else -> MolePainter.Mood.HAPPY
            }
            MolePainter.draw(c, ml, mt, mw, mh, mood, golden = hole.kind == Kind.GOLD, helmet = hole.kind == Kind.DECOY)
            c.restore()
            if (hole.st == St.HIT && hole.kind != Kind.DECOY) {
                val t = (now - hole.t0) / 320f
                for (i in 0 until 3) {
                    val a = t * 6 + i * 2.1
                    MolePainter.drawStar(c, r.centerX() + (kotlin.math.cos(a) * mw * 0.35).toFloat(),
                        mt + mh * 0.12f + (sin(a) * 6 * d).toFloat(), 7 * d, 0xFFFFD34D.toInt())
                }
            }
        }
        // bordo di terra davanti
        paint.color = 0xFF6B4423.toInt()
        c.drawArc(r.left - 4 * d, baseY - holeH / 2, r.right + 4 * d, baseY + holeH / 2 + 8 * d, 0f, 180f, true, paint)
    }

    private fun drawHammer(c: Canvas, now: Long) {
        val t = now - hammerAt
        if (hammerX < 0 || t > 220) return
        val angle = if (t < 90) -50f + 60f * (t / 90f) else 10f
        c.save()
        c.translate(hammerX + 28 * d, hammerY + 28 * d)
        c.rotate(angle)
        paint.color = 0xFF8B5A2B.toInt()
        c.drawRoundRect(-5 * d, -70 * d, 5 * d, 0f, 4 * d, 4 * d, paint)
        paint.color = 0xFF3D3D3D.toInt()
        c.drawRoundRect(-30 * d, -92 * d, 22 * d, -62 * d, 8 * d, 8 * d, paint)
        paint.color = 0xFFC2410C.toInt()
        c.drawRoundRect(-34 * d, -90 * d, -24 * d, -64 * d, 4 * d, 4 * d, paint)
        c.restore()
    }

    private fun drawIntro(c: Canvas, w: Float, h: Float, now: Long) {
        paint.color = 0xE6F7F1E3.toInt()
        c.drawRect(0f, 0f, w, h, paint)
        val n = 3 - ((now - startAt) / 800).toInt()
        text.color = 0xFF2F4A17.toInt(); text.textSize = 28 * d
        c.drawText("Martella $target ${if (target == 1) "talpa" else "talpe"}!", w / 2, h * 0.36f, text)
        text.color = 0xFF4A3B2C.toInt(); text.textSize = 15 * d
        c.drawText("Le talpe col caschetto sono esche: lasciale stare.", w / 2, h * 0.36f + 32 * d, text)
        c.drawText("Quella d'oro vale doppio.", w / 2, h * 0.36f + 54 * d, text)
        text.color = 0xFFC2410C.toInt(); text.textSize = 96 * d
        c.drawText("${max(1, n)}", w / 2, h * 0.62f, text)
        MolePainter.draw(c, w / 2 - 50 * d, h * 0.68f, 100 * d, 110 * d, MolePainter.Mood.SMUG)
    }

    private fun drawEnd(c: Canvas, w: Float, h: Float, now: Long) {
        val a = ((now - endAt) / 250f).coerceIn(0f, 1f)
        paint.color = if (won) 0xFFF7F1E3.toInt() else 0xFF2B2118.toInt()
        paint.alpha = (a * 240).toInt()
        c.drawRect(0f, 0f, w, h, paint)
        paint.alpha = 255
        if (won) {
            text.color = 0xFF2F4A17.toInt(); text.textSize = 15 * d
            c.drawText("HAI MARTELLATO TUTTE LE TALPE", w / 2, h * 0.36f, text)
            text.textSize = 44 * d
            c.drawText(winText, w / 2, h * 0.46f, text)
            for (i in 0 until 3) MolePainter.draw(c, w / 2 - 150 * d + i * 100 * d, h * 0.55f, 90 * d, 99 * d, MolePainter.Mood.BONKED)
        } else {
            text.color = 0xFFF5B78A.toInt(); text.textSize = 15 * d
            c.drawText("LE TALPE HANNO VINTO", w / 2, h * 0.36f, text)
            text.color = 0xFFFFFFFF.toInt(); text.textSize = 34 * d
            c.drawText(loseText, w / 2, h * 0.46f, text)
            for (i in 0 until 3) MolePainter.draw(c, w / 2 - 150 * d + i * 100 * d, h * 0.55f, 90 * d, 99 * d, MolePainter.Mood.SMUG)
        }
    }
}
