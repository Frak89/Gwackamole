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
 * "Vuoi proprio sbloccarmi?" — compare a caso allo sblocco, con 1–3 conferme secondo quanto
 * hai usato il telefono oggi. Mettere via il telefono (Home) è sempre permesso: è l'uscita giusta.
 */
class UnlockGateActivity : Activity() {

    companion object { const val EXTRA_STEPS = "steps" }

    private var left = 1
    private lateinit var btn: Button
    private lateinit var msg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        val tier = intent.getIntExtra(EXTRA_STEPS, 1).coerceIn(1, 3)
        left = tier
        Neon.immersive(this)

        val bg = 0xFF0A0A12.toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(bg); setPadding(dp(30), dp(40), dp(30), dp(40))
        }
        col.addView(Neon.mark(this), LinearLayout.LayoutParams(dp(84), dp(84)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        col.addView(TextView(this).apply {
            text = Messages.gateTitle(tier); textSize = 28f; setTextColor(C.INK); gravity = Gravity.CENTER
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            setPadding(0, dp(20), 0, dp(10))
        })
        msg = TextView(this).apply {
            text = Store.randomText() ?: "Stai davvero scegliendo di usarlo, o è solo l'abitudine?"
            textSize = 17f; setTextColor(C.MUTED); gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(26))
        }
        col.addView(msg)
        btn = Button(this).apply {
            isAllCaps = false; textSize = 17f; setTextColor(0xFF0A0A12.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD; stateListAnimator = null
            background = Neon.pill(context, C.GREEN); minHeight = dp(54)
            setOnClickListener { step() }
        }
        col.addView(btn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        col.addView(Button(this).apply {
            text = "No, lo metto via"; isAllCaps = false; textSize = 15f; setTextColor(C.GRASS)
            stateListAnimator = null; background = Neon.ghost(context)
            setOnClickListener { goHome() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) })

        updateBtn()
        setContentView(FrameLayout(this).apply { setBackgroundColor(bg); addView(col) })
    }

    private fun updateBtn() {
        btn.text = if (left <= 1) "Sì, sbloccami" else "Sì, sbloccami ($left)"
    }

    private fun step() {
        left--
        if (left <= 0) { finish(); return }
        msg.text = Store.randomText() ?: "Sicuro? Ancora una conferma."
        updateBtn()
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    @Deprecated("Conferma richiesta") @Suppress("DEPRECATION")
    override fun onBackPressed() { goHome() }
}
