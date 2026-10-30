package com.frak89.gwackamole.ui

import android.app.Activity
import android.os.Bundle
import android.text.format.DateUtils
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.frak89.gwackamole.core.PendingApplier
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.data.Store

/** Premi, punizioni, talpe a sorpresa, cooldown e modifiche in attesa. */
class RulesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun set(key: String, value: Any, desc: String, looser: Boolean) {
        report(PendingApplier.request(this, "setting", PendingApplier.setting(key, value), desc, looser))
        render()
    }

    private fun render() {
        val col = page()
        col.eyebrow("Regole")
        col.title("Premi e punizioni")
        col.gap(12)

        col.card {
            eyebrow("Se vinci")
            sliderRow("Minuti guadagnati", 1, 3, Store.bonusMinutes / 5, { "${it * 5} min" }) {
                val v = it * 5
                if (v != Store.bonusMinutes) set("bonus_minutes", v, "Premio $v min", looser = v > Store.bonusMinutes)
            }
        }

        col.card {
            eyebrow("Se vincono le talpe")
            sliderRow("Blocco iniziale", 1, 12, Store.lockBaseMinutes / 5, { "${it * 5} min" }) {
                val v = it * 5
                if (v != Store.lockBaseMinutes) set("lock_base", v, "Blocco $v min", looser = v < Store.lockBaseMinutes)
            }
            sliderRow("In più a ogni sconfitta", 0, 6, Store.lockStepMinutes / 5, { "+${it * 5} min" }) {
                val v = it * 5
                if (v != Store.lockStepMinutes) set("lock_step", v, "Blocco +$v min", looser = v < Store.lockStepMinutes)
            }
            body("Lo schermo si spegne e al riavvio trovi il conto alla rovescia. Le chiamate d'emergenza funzionano sempre.")
        }

        col.card {
            eyebrow("Talpe a sorpresa")
            switchRow("Durante l'uso delle app in blacklist", Store.randomChecks) {
                if (it != Store.randomChecks) set("random_checks", it, "Talpe a sorpresa ${if (it) "on" else "off"}", looser = !it)
            }
            sliderRow("Schermo acceso di fila", 0, 12, Store.screenLimitMin / 15, { if (it == 0) "mai" else "${it * 15} min" }) {
                val v = it * 15
                val old = Store.screenLimitMin
                if (v != old) set("screen_limit", v, "Talpe dopo $v min di schermo", looser = v == 0 || (old != 0 && v > old))
            }
        }

        col.card {
            eyebrow("Cooldown")
            sliderRow("Attesa per allentare le regole", 1, 72, Store.cooldownHours, { "$it h" }) {
                if (it != Store.cooldownHours) set("cooldown_h", it, "Cooldown $it h", looser = it < Store.cooldownHours)
            }
            if (Protection.isDeviceOwner(this@RulesActivity)) {
                switchRow("Blocca anche il debug USB (adb)", Store.blockAdb) {
                    if (it != Store.blockAdb) set("block_adb", it, "Debug USB ${if (it) "bloccato" else "libero"}", looser = !it)
                }
            }
        }

        val pend = Store.pending()
        col.card {
            eyebrow("In attesa · ${pend.size}")
            if (pend.isEmpty()) body("Nessuna modifica in coda.")
            for (p in pend.sortedBy { it.applyAt }) {
                val r = LinearLayout(this@RulesActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val t = LinearLayout(this@RulesActivity).apply { orientation = LinearLayout.VERTICAL }
                t.addView(TextView(this@RulesActivity).apply { text = p.description; textSize = 14f; setTextColor(C.INK) })
                t.addView(TextView(this@RulesActivity).apply {
                    text = "parte ${DateUtils.getRelativeTimeSpanString(p.applyAt)}"; textSize = 12f; setTextColor(C.MUTED)
                })
                r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                r.addView(Button(this@RulesActivity).apply {
                    text = "Annulla"; isAllCaps = false; setTextColor(C.GREEN); stateListAnimator = null
                    background = rounded(C.SOFT, dp(10).toFloat())
                    setOnClickListener { Store.cancelPending(p.id); render() }
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
                addView(r)
            }
        }
    }
}
