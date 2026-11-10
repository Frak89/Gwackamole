package com.frak89.gwackamole.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.frak89.gwackamole.core.PendingApplier
import com.frak89.gwackamole.core.UsageTracker
import com.frak89.gwackamole.data.AppRule
import com.frak89.gwackamole.data.Store

/**
 * Riepilogo d'uso in stile cyberpunk: quanto usi il telefono in media e quali app ti prendono di più.
 * Compare a fine soglia/giornata e si può aprire dalle statistiche. Dalle app oltre 1 h/giorno
 * offre di impostare un limite con un tocco.
 */
class UsageReportActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Neon.immersive(this)
        render()
    }

    private fun render() {
        val top = UsageTracker.topTodayApps(this, 6)
        val total = UsageTracker.totalTodayMs(this)
        val avg = UsageTracker.avgDailyMs(this, 7)
        val pm = packageManager
        fun label(p: String) = try { pm.getApplicationLabel(pm.getApplicationInfo(p, 0)).toString() } catch (_: Exception) { p }

        val scroll = ScrollView(this).apply { setBackgroundColor(Neon.BG) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(40), dp(22), dp(28))
        }
        col.addView(TextView(this).apply {
            text = "QUADRO D'USO"; textSize = 13f; setTextColor(C.GRASS); letterSpacing = 0.25f
            typeface = Typeface.DEFAULT_BOLD
        })
        col.addView(neonTitle("Oggi hai usato il telefono"))
        col.addView(TextView(this).apply {
            text = fmt(total); textSize = 54f
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
            paint.shader = LinearGradient(0f, 0f, 420f, 0f, Neon.VIOLET, Neon.BLUE, Shader.TileMode.CLAMP)
        })
        col.addView(TextView(this).apply {
            text = "Media ultimi 7 giorni: ${fmt(avg)}/giorno" +
                if (avg > 0 && total > avg) "  ·  oggi sei sopra la media" else ""
            textSize = 14f; setTextColor(C.MUTED); setPadding(0, dp(4), 0, dp(10))
        })

        if (top.isEmpty()) {
            col.addView(TextView(this).apply {
                text = "Dati d'uso non disponibili: attiva il permesso \"Utilizzo app\"."
                textSize = 14f; setTextColor(C.MUTED); setPadding(0, dp(20), 0, dp(10))
            })
        } else {
            val maxV = top.first().second.coerceAtLeast(1)
            col.addView(Chart(this, top.map { label(it.first) to it.second }, maxV),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44 * top.size + 16)))

            // app oltre 1 ora e non ancora limitate → offri il limite con un tocco
            val heavy = top.filter { it.second >= Store.autoLimitMin * 60_000L && !Store.rules().containsKey(it.first) }
            if (heavy.isNotEmpty()) {
                col.addView(TextView(this).apply {
                    text = "Oltre ${Store.autoLimitMin} min oggi — metti un limite:"
                    textSize = 14f; setTextColor(C.INK); typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(18), 0, dp(6))
                })
                for ((p, ms) in heavy) {
                    val lab = label(p)
                    col.addView(Button(this).apply {
                        text = "$lab · ${fmt(ms)} oggi → limita a 30 min"
                        isAllCaps = false; textSize = 14f; setTextColor(0xFF0A0A12.toInt())
                        typeface = Typeface.DEFAULT_BOLD; stateListAnimator = null
                        background = Neon.pill(context, C.GREEN); minHeight = dp(50)
                        setOnClickListener {
                            PendingApplier.apply(this@UsageReportActivity,
                                com.frak89.gwackamole.data.PendingChange(0, "rule",
                                    AppRule(p, lab, 30, 3, true).toJson().toString(), 0, "Limite $lab"))
                            text = "$lab · limitata a 30 min ✓"; isEnabled = false; alpha = 0.6f
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        .apply { topMargin = dp(8) })
                }
            }
        }

        col.addView(Button(this).apply {
            text = "Ho capito"; isAllCaps = false; textSize = 16f; setTextColor(C.INK)
            stateListAnimator = null; background = Neon.ghost(context); minHeight = dp(52)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { topMargin = dp(22) })

        scroll.addView(col)
        setContentView(FrameLayout(this).apply { setBackgroundColor(Neon.BG); addView(scroll) })
    }

    private fun neonTitle(t: String) = TextView(this).apply {
        text = t; textSize = 26f; setTextColor(C.INK)
        typeface = Typeface.create("sans-serif-black", Typeface.NORMAL); setPadding(0, dp(6), 0, dp(2))
    }

    private fun fmt(ms: Long): String {
        val m = ms / 60_000
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    @Deprecated("") @Suppress("DEPRECATION")
    override fun onBackPressed() { finish() }

    @SuppressLint("ViewConstructor")
    private class Chart(ctx: Context, val rows: List<Pair<String, Long>>, val maxV: Long) : android.view.View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1A1733.toInt() }
        private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF2F2FF.toInt(); textSize = 13 * d; typeface = Typeface.DEFAULT_BOLD }
        private val value = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF9A93C7.toInt(); textSize = 12 * d; textAlign = Paint.Align.RIGHT }
        private val rr = RectF()
        override fun onDraw(c: Canvas) {
            val rowH = 44 * d
            val barH = 16 * d
            val left = 0f
            val right = width.toFloat()
            rows.forEachIndexed { i, (lab, ms) ->
                val cy = i * rowH + 8 * d
                c.drawText(lab, left, cy + 12 * d, name)
                c.drawText(fmtM(ms), right, cy + 12 * d, value)
                val top = cy + 18 * d
                rr.set(left, top, right, top + barH)
                c.drawRoundRect(rr, barH / 2, barH / 2, track)
                val w = (right - left) * (ms.toFloat() / maxV).coerceIn(0.03f, 1f)
                bar.shader = LinearGradient(left, 0f, left + w, 0f, 0xFFB65CFF.toInt(), 0xFF2DD4FF.toInt(), Shader.TileMode.CLAMP)
                rr.set(left, top, left + w, top + barH)
                c.drawRoundRect(rr, barH / 2, barH / 2, bar)
            }
        }
        private fun fmtM(ms: Long): String {
            val m = ms / 60_000
            return if (m >= 60) "${m / 60}h${m % 60}m" else "${m}m"
        }
    }
}
