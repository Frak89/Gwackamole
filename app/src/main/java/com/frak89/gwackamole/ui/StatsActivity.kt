package com.frak89.gwackamole.ui

import android.app.Activity
import android.os.Bundle
import com.frak89.gwackamole.core.Behavior
import com.frak89.gwackamole.core.UsageTracker
import com.frak89.gwackamole.data.Store

/** Analisi del comportamento sulle app e sui siti in blacklist (ultimi 7 giorni, tutto sul telefono). */
class StatsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val col = page()
        col.eyebrow("Ultimi 7 giorni")
        col.title("Come usi le distrazioni")
        col.gap(12)

        val profiles = Behavior.profiles(force = true)
        col.card {
            eyebrow("Oggi")
            body("Round persi: ${Store.lossesToday()}   ·   tentativi di bypass: ${Store.bypassToday()}", C.INK, 15f)
        }

        val rules = Store.rules()
        for ((pkg, rule) in rules) {
            val p = profiles[pkg]
            val used = UsageTracker.usedTodayMs(this, pkg) / 60_000
            col.card {
                eyebrow(rule.label)
                body("Oggi: $used / ${rule.limitMin + Store.bonusToday(pkg)} min · aperta ${p?.opensToday ?: 0} volte", C.INK, 15f)
                if (p != null) {
                    body("7 giorni: ${p.opens7d} aperture, ${p.minutes7d} min, sessione media ${p.avgSessionSec}s")
                    body("Minigiochi: ${p.wins} vinti, ${p.losses} persi · rischio ${(p.risk * 100).toInt()}%")
                    body(p.advice(), C.GREEN)
                }
            }
        }

        val sites = profiles.values.filter { it.target.startsWith("site:") }.sortedByDescending { it.blocks }.take(15)
        if (sites.isNotEmpty()) col.card {
            eyebrow("Siti bloccati")
            for (s in sites) body("${s.target.removePrefix("site:")} — ${s.blocks} blocchi, ${s.wins} vinti, ${s.losses} persi", C.INK)
        }

        if (rules.isEmpty()) col.body("Aggiungi qualche app alla blacklist per vedere l'analisi.")
    }
}
