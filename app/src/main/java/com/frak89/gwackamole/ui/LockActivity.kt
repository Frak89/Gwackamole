package com.frak89.gwackamole.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.game.MolePainter

/** "Le talpe hanno vinto": conto alla rovescia. Il servizio riporta qui finché non scade. */
class LockActivity : Activity() {

    private lateinit var clock: TextView
    private val ticker = object : Runnable {
        override fun run() {
            val left = Store.lockUntil - System.currentTimeMillis()
            if (left <= 0) { finish(); return }
            val s = left / 1000
            clock.text = String.format("%02d:%02d", s / 60, s % 60)
            clock.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val dark = 0xFF1E1712.toInt()
        window.statusBarColor = dark
        window.navigationBarColor = dark

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(dark); setPadding(dp(28), dp(48), dp(28), dp(32))
        }
        col.addView(MolesView(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120)))
        col.addView(TextView(this).apply {
            text = "LE TALPE HANNO VINTO"; textSize = 13f; setTextColor(0xFFF5B78A.toInt()); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.12f; gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6))
        })
        col.addView(TextView(this).apply {
            text = "Telefono in pausa"; textSize = 34f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = "Riprova quando il conto alla rovescia arriva a zero."; textSize = 15f
            setTextColor(0xFFE6DCC5.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(18))
        })
        clock = TextView(this).apply {
            textSize = 80f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        col.addView(clock)
        col.addView(TextView(this).apply {
            text = "Round persi oggi: ${Store.lossesToday()} · il prossimo blocco dura " +
                "${Store.lockBaseMinutes + Store.lockStepMinutes * Store.lossesToday()} min"
            textSize = 13f; setTextColor(0xFFE6DCC5.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(24))
        })
        col.addView(Button(this).apply {
            text = "Chiamata di emergenza"; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt()); stateListAnimator = null
            background = rounded(dark, dp(16).toFloat(), dp(2), 0xFF4A3B2C.toInt())
            setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        setContentView(FrameLayout(this).apply { setBackgroundColor(dark); addView(col) })
    }

    override fun onResume() {
        super.onResume()
        if (!Store.isLocked()) { finish(); return }
        clock.removeCallbacks(ticker)
        clock.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        clock.removeCallbacks(ticker)
    }

    @Deprecated("Indietro disattivato durante il blocco")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { /* niente */ }

    @SuppressLint("ViewConstructor")
    private class MolesView(ctx: Context) : View(ctx) {
        override fun onDraw(c: Canvas) {
            val s = height * 0.8f
            val gap = s * 0.1f
            val total = s * 3 + gap * 2
            var x = (width - total) / 2
            for (i in 0 until 3) {
                val sc = if (i == 1) 1f else 0.8f
                MolePainter.draw(c, x + s * (1 - sc) / 2, height - s * sc * 1.1f, s * sc, s * sc * 1.1f, MolePainter.Mood.SMUG)
                x += s + gap
            }
        }
    }
}
