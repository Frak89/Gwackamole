package com.frak89.gwackamole.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.frak89.gwackamole.data.Store

/** "Telefono in pausa": conto alla rovescia. Il servizio riporta qui finché non scade. */
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
        val dark = Neon.BG
        window.statusBarColor = dark
        window.navigationBarColor = dark

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(dark); setPadding(dp(28), dp(64), dp(28), dp(32))
        }
        col.addView(Neon.mark(this), LinearLayout.LayoutParams(dp(92), dp(92)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(TextView(this).apply {
            text = "NERO BLOCK"; textSize = 13f; setTextColor(0xFF2DD4FF.toInt()); typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.25f; gravity = Gravity.CENTER; setPadding(0, dp(20), 0, dp(6))
        })
        col.addView(TextView(this).apply {
            text = if (Store.kickLockSec == 60) "Un minuto di pausa" else "Telefono in pausa"
            textSize = 34f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        })
        val quote = Store.lockMessage.ifBlank { Store.randomText() ?: "" }
        col.addView(TextView(this).apply {
            text = quote.ifBlank { "Riprova quando il conto alla rovescia arriva a zero." }
            textSize = if (quote.isNotBlank()) 18f else 15f
            setTextColor(if (quote.isNotBlank()) 0xFFF2F2FF.toInt() else 0xFFBDBDBD.toInt())
            gravity = Gravity.CENTER; setPadding(dp(6), dp(12), dp(6), dp(18))
        })
        clock = TextView(this).apply {
            textSize = 80f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
        }
        col.addView(clock)
        col.addView(TextView(this).apply {
            text = "Blocchi oggi: ${Store.lossesToday()} · il prossimo dura di più"
            textSize = 13f; setTextColor(0xFFBDBDBD.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(24))
        })
        col.addView(Button(this).apply {
            text = "Chiamata di emergenza"; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt()); stateListAnimator = null
            background = rounded(dark, dp(16).toFloat(), dp(2), 0xFF3A3A3A.toInt())
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
}
