package com.frak89.gwackamole.game

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.core.Reason
import com.frak89.gwackamole.data.Store

/**
 * Schermata del minigioco. Non si esce finché il round non è finito:
 *  - con Device Owner: lock-task mode → Home, Recenti e swipe dal basso disattivati dal sistema;
 *  - senza: schermo intero immersivo, Indietro ignorato, gesture laterali escluse
 *    e, se esci comunque, il servizio di accessibilità ti riporta qui.
 */
class MoleGameActivity : Activity() {

    companion object {
        const val EXTRA_TARGET = "target"
        const val EXTRA_LABEL = "label"
        const val EXTRA_REASON = "reason"
        const val EXTRA_SEED = "seed"
        const val EXTRA_DIFFICULTY = "difficulty"
        const val EXTRA_RESUME = "resume"
    }

    private var finishedRound = false
    private var lockTask = false
    private lateinit var target: String
    private lateinit var reason: Reason

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        target = intent.getStringExtra(EXTRA_TARGET) ?: "test"
        val label = intent.getStringExtra(EXTRA_LABEL) ?: target
        reason = runCatching { Reason.valueOf(intent.getStringExtra(EXTRA_REASON) ?: "TEST") }.getOrDefault(Reason.TEST)
        val seed = intent.getIntExtra(EXTRA_SEED, 1)
        val diff = intent.getIntExtra(EXTRA_DIFFICULTY, 0)

        val lockMin = Store.lockBaseMinutes + Store.lockStepMinutes * Store.lossesToday()
        val winText = when (reason) {
            Reason.TIME_UP, Reason.USER_SITE -> "+${Store.bonusMinutes} min"
            Reason.ADULT_SITE, Reason.ADULT_LINK -> "Torni alla ricerca"
            Reason.TEST -> "Bravo!"
            else -> "Puoi continuare"
        }
        val loseText = if (reason == Reason.TEST) "Riprova" else "Blocco $lockMin min"

        val view = MoleGameView(this, seed, diff, "${reason.title} · $label", winText, loseText) { won ->
            finishedRound = true
            Guardian.onGameResult(applicationContext, target, reason, won)
            if (lockTask) runCatching { stopLockTask() }
            finish()
        }
        setContentView(view)
        view.addOnLayoutChangeListener { v, l, t, r, b, _, _, _, _ ->
            // escludi le gesture "indietro" ai bordi (Android limita a ~200dp per lato)
            if (Build.VERSION.SDK_INT >= 29) {
                val ex = (200 * resources.displayMetrics.density).toInt()
                val mid = (b - t) / 2
                v.systemGestureExclusionRects = listOf(
                    Rect(0, mid - ex / 2, 60, mid + ex / 2),
                    Rect(r - l - 60, mid - ex / 2, r - l, mid + ex / 2),
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_OVERLAY) { /* ignorato */ }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (!lockTask && Protection.isLockTaskPermitted(this)) {
            lockTask = runCatching { startLockTask() }.isSuccess
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }
    }

    @Deprecated("Indietro disattivato durante il round")
    @Suppress("DEPRECATION")
    override fun onBackPressed() { /* niente fuga */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // il servizio ci ha riportato in primo piano: la partita continua da dov'era
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!finishedRound && !lockTask) {
            // swipe verso la Home: torniamo subito qui
            window.decorView.postDelayed({
                if (!finishedRound) startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra(EXTRA_RESUME, true))
            }, 250)
        }
    }
}
