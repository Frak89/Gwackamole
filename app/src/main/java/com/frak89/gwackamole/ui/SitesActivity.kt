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
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.Store

/** Blacklist dei siti, filtro per adulti, domini imparati dalle ricerche. */
class SitesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun setting(key: String, value: Any, desc: String, looser: Boolean) {
        report(PendingApplier.request(this, "setting", PendingApplier.setting(key, value), desc, looser))
        render()
    }

    private fun render() {
        val col = page()
        col.eyebrow("Browser")
        col.title("Siti web")
        col.gap(12)

        col.card {
            eyebrow("Filtro contenuti per adulti")
            switchRow("Blocca siti per adulti", Store.adultFilter) {
                if (it != Store.adultFilter) setting("adult_filter", it, "Filtro adulti ${if (it) "on" else "off"}", looser = !it)
            }
            switchRow("Copri con la talpa i link nei risultati di ricerca", Store.serpCover) {
                if (it != Store.serpCover) setting("serp_cover", it, "Copertura risultati ${if (it) "on" else "off"}", looser = !it)
            }
            sliderRow("Blocco dopo", 0, 5, Store.siteGraceSec, { if (it == 0) "subito" else "$it s" }) {
                if (it != Store.siteGraceSec) setting("site_grace", it, "Blocco siti dopo $it s", looser = it > Store.siteGraceSec)
            }
            body("Vincere il minigioco non apre un sito per adulti: ti riporta alla ricerca. Perdere spegne lo schermo.")
        }

        col.card {
            eyebrow("I tuoi siti in blacklist")
            body("Per siti come YouTube o Reddit da browser: vincere il minigioco li sblocca per ${Store.bonusMinutes} min.")
            val inp = input("es. youtube.com")
            button("Aggiungi") {
                val d = Blocklist.hostOf(inp.text.toString())
                if (d == null) toast("Dominio non valido") else { Store.addSite(d); render() }
            }
            for (s in Store.userSites().sorted()) removableRow(this, s) {
                report(PendingApplier.request(this@SitesActivity, "remove_site", s, "Togli $s dalla blacklist", looser = true))
                render()
            }
        }

        val learned = Blocklist.learnedDomains().sorted()
        col.card {
            eyebrow("Imparati dalle ricerche · ${learned.size}")
            body("Quando un risultato di Google, Bing, DuckDuckGo… contiene parole come porn o porno, il suo dominio finisce qui da solo.")
            for (d in learned.take(150)) removableRow(this, d) {
                report(PendingApplier.request(this@SitesActivity, "forget_learned", d, "Dimentica $d", looser = true))
                render()
            }
        }

        col.card {
            eyebrow("Liste pubbliche")
            val last = Store.lastListUpdate
            body("${Blocklist.SEED.size} domini inclusi · ${Blocklist.downloadedCount()} scaricati" +
                if (last > 0) " · aggiornate ${DateUtils.getRelativeTimeSpanString(last)}" else "")
            button("Aggiorna liste", primary = false) {
                toast("Scarico le liste…")
                Blocklist.updateRemote { n, err ->
                    runOnUiThread {
                        toast(if (n > 0) "$n domini caricati" else "Download non riuscito${err?.let { ": $it" } ?: ""}")
                        render()
                    }
                }
            }
        }
    }

    private fun removableRow(parent: LinearLayout, label: String, onRemove: () -> Unit) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(TextView(this).apply { text = label; textSize = 14f; setTextColor(C.INK) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(Button(this).apply {
            text = "Togli"; isAllCaps = false; setTextColor(C.GREEN); stateListAnimator = null
            background = rounded(C.SOFT, dp(10).toFloat())
            setOnClickListener { onRemove() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        parent.addView(r)
    }
}
