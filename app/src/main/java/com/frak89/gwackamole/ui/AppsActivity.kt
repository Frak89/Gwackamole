package com.frak89.gwackamole.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import com.frak89.gwackamole.core.PendingApplier
import com.frak89.gwackamole.core.UsageTracker
import com.frak89.gwackamole.data.AppRule
import com.frak89.gwackamole.data.Store

/** Scegli le app in blacklist e il timer giornaliero di ciascuna. */
class AppsActivity : Activity() {

    private data class App(val pkg: String, val label: String, val icon: Drawable)

    private val limits = intArrayOf(1, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)
    private var apps: List<App> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val pm = packageManager
        val launch = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(launch, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != packageName }
            .map { App(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it)) }
            .sortedBy { it.label.lowercase() }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val col = page()
        col.eyebrow("Blacklist app")
        col.title("Timer giornalieri")
        col.body("Rendere un timer più corto vale subito. Allungarlo o togliere un'app vale solo dopo il cooldown " +
            "(${Store.cooldownHours} h) — oppure vincendo al minigioco, +${Store.bonusMinutes} min alla volta.")
        col.gap(12)

        val rules = Store.rules()
        val sorted = apps.sortedByDescending { rules.containsKey(it.pkg) }
        for (a in sorted) row(col, a, rules[a.pkg])
    }

    private fun row(col: LinearLayout, a: App, rule: AppRule?) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (rule != null) C.CARD else C.SOFT, dp(16).toFloat())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minimumHeight = dp(56)
            isClickable = true
            setOnClickListener { edit(a, rule) }
        }
        r.addView(ImageView(this).apply { setImageDrawable(a.icon) }, LinearLayout.LayoutParams(dp(36), dp(36)))
        val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        t.addView(TextView(this).apply { text = a.label; textSize = 15f; setTextColor(C.INK); paint.isFakeBoldText = true })
        if (rule != null) {
            val usedMin = UsageTracker.usedTodayMs(this, a.pkg) / 60_000
            val bonus = Store.bonusToday(a.pkg)
            t.addView(TextView(this).apply {
                text = "${rule.limitMin} min/giorno" + (if (bonus > 0) " +$bonus vinti" else "") +
                    " · usati $usedMin · tolleranza ${rule.graceSec}s" + if (rule.randomChecks) " · talpe a sorpresa" else ""
                textSize = 12f; setTextColor(C.MUTED)
            })
        }
        r.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(8) })
    }

    private fun edit(a: App, old: AppRule?) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        box.body("Minuti al giorno", C.INK)
        val np = NumberPicker(this).apply {
            minValue = 0; maxValue = limits.size - 1
            displayedValues = limits.map { "$it min" }.toTypedArray()
            value = limits.indexOfFirst { it >= (old?.limitMin ?: 30) }.coerceAtLeast(0)
            wrapSelectorWheel = false
        }
        box.addView(np)
        var grace = old?.graceSec ?: 3
        box.sliderRow("Secondi prima del minigioco", 0, 10, grace, { "$it s" }) { grace = it }
        var random = old?.randomChecks ?: true
        box.switchRow("Talpe a sorpresa durante l'uso", random) { random = it }

        val dlg = AlertDialog.Builder(this).setTitle(a.label).setView(box)
            .setPositiveButton("Salva") { _, _ ->
                val nr = AppRule(a.pkg, a.label, limits[np.value], grace, random)
                val looser = old != null && (nr.limitMin > old.limitMin || nr.graceSec > old.graceSec ||
                    (old.randomChecks && !nr.randomChecks))
                report(PendingApplier.request(this, "rule", nr.toJson().toString(),
                    "${a.label}: ${nr.limitMin} min, ${nr.graceSec}s", looser))
                render()
            }
            .setNegativeButton("Annulla", null)
        if (old != null) dlg.setNeutralButton("Togli dalla blacklist") { _, _ ->
            report(PendingApplier.request(this, "remove_rule", a.pkg, "Togli ${a.label} dalla blacklist", looser = true))
            render()
        }
        dlg.show()
    }
}
