package com.frak89.gwackamole.ui

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.frak89.gwackamole.core.Messages
import com.frak89.gwackamole.data.Store

/**
 * Cooldown prima di riaprire un'app molto usata, poco dopo un uso intenso. Un minuto (regolabile)
 * di attesa: quando finisce l'app è di nuovo usabile. Mettere via il telefono resta la via d'uscita.
 */
class CooldownActivity : Activity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_LABEL = "label"
    }

    private lateinit var pkg: String
    private var endAt = 0L
    private lateinit var clock: TextView
    private lateinit var go: Button

    private val tick = object : Runnable {
        override fun run() {
            val left = ((endAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            clock.text = String.format("00:%02d", left)
            if (left <= 0) ready() else clock.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        Neon.immersive(this)
        pkg = intent.getStringExtra(EXTRA_PKG) ?: ""
        val label = intent.getStringExtra(EXTRA_LABEL) ?: "questa app"
        val sec = Store.reopenCooldownSec
        endAt = System.currentTimeMillis() + sec * 1000L
        // piccola finestra anti-rimbalzo: non riproporlo subito se esci e rientri
        Store.clearCooldown(pkg, System.currentTimeMillis() + 30_000L)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Neon.BG); setPadding(dp(30), dp(40), dp(30), dp(40))
        }
        col.addView(Neon.mark(this), LinearLayout.LayoutParams(dp(80), dp(80)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(TextView(this).apply {
            text = "RESPIRA UN MINUTO"; textSize = 13f; setTextColor(C.GRASS)
            letterSpacing = 0.2f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; setPadding(0, dp(18), 0, dp(6))
        })
        col.addView(TextView(this).apply {
            text = "Hai già usato tanto $label oggi"; textSize = 22f; setTextColor(C.INK); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        })
        col.addView(TextView(this).apply {
            text = Messages.forApp(label, pkg); textSize = 16f; setTextColor(C.MUTED); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(18))
        })
        clock = TextView(this).apply {
            textSize = 72f; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        }
        col.addView(clock)
        go = Button(this).apply {
            text = "Attendi…"; isAllCaps = false; textSize = 16f; isEnabled = false
            setTextColor(0xFF0A0A12.toInt()); typeface = android.graphics.Typeface.DEFAULT_BOLD
            stateListAnimator = null; background = Neon.pill(context, C.GREEN); minHeight = dp(54)
            alpha = 0.5f
            setOnClickListener { open() }
        }
        col.addView(go, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(20) })
        col.addView(Button(this).apply {
            text = "Lascio perdere, metto via"; isAllCaps = false; textSize = 15f; setTextColor(C.GRASS)
            stateListAnimator = null; background = Neon.ghost(context)
            setOnClickListener { goHome() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) })

        setContentView(FrameLayout(this).apply { setBackgroundColor(Neon.BG); addView(col) })
    }

    override fun onResume() {
        super.onResume()
        clock.removeCallbacks(tick); clock.post(tick)
    }

    override fun onPause() { super.onPause(); clock.removeCallbacks(tick) }

    private fun ready() {
        go.isEnabled = true; go.alpha = 1f; go.text = "Apri ora"
    }

    private fun open() {
        // sbloccata per un po': non riproporre il cooldown subito
        Store.clearCooldown(pkg, System.currentTimeMillis() + 15 * 60_000L)
        val launch = packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Deprecated("Attendi il cooldown") @Suppress("DEPRECATION")
    override fun onBackPressed() { goHome() }
}
