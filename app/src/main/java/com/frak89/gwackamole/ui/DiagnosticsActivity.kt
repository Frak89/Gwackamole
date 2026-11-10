package com.frak89.gwackamole.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.TextView
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.core.UsageTracker
import com.frak89.gwackamole.data.CrashLog
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.service.GwackAccessibilityService

/** Diagnostica da incollare: stato servizio, permessi, ambiente e ultimo errore. */
class DiagnosticsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        val col = page()
        col.eyebrow("Diagnostica")
        col.title("Stato di Nero Block")
        col.gap(12)

        val report = build()
        col.card {
            val tv = TextView(this@DiagnosticsActivity).apply {
                text = report; textSize = 12f; setTextColor(C.INK)
                typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true)
            }
            addView(tv, LinearLayoutParamsWrap())
        }
        col.button("Copia tutto") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("diagnostica", report))
            toast("Diagnostica copiata: incollala nella chat")
        }
        col.button("Pulisci l'ultimo errore", primary = false) { CrashLog.clear(); recreate() }
    }

    private fun LinearLayoutParamsWrap() =
        android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun accessibilityEnabledInSettings(): Boolean {
        val cn = ComponentName(this, GwackAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.equals(cn, true) }
    }

    private fun build(): String {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, com.frak89.gwackamole.admin.GwackAdminReceiver::class.java)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        fun yn(b: Boolean) = if (b) "SI" else "NO"
        val pkgInfo = packageManager.getPackageInfo(packageName, 0)
        val sb = StringBuilder()
        sb.appendLine("=== Nero Block diagnostica ===")
        sb.appendLine("app: $packageName v${pkgInfo.versionName} (${Build.VERSION.SDK_INT})")
        sb.appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        sb.appendLine("")
        sb.appendLine("Accessibilità attiva nelle impostazioni: ${yn(accessibilityEnabledInSettings())}")
        sb.appendLine("Servizio REALMENTE agganciato: ${yn(Guardian.isAttached())}")
        sb.appendLine("App in primo piano vista: ${Guardian.foreground() ?: "-"}")
        sb.appendLine("")
        sb.appendLine("Permesso utilizzo app: ${yn(UsageTracker.hasPermission(this))}")
        sb.appendLine("Overlay (sopra le app): ${yn(Settings.canDrawOverlays(this))}")
        sb.appendLine("Amministratore dispositivo: ${yn(dpm.isAdminActive(admin))}")
        sb.appendLine("Device Owner: ${yn(Protection.isDeviceOwner(this))}")
        sb.appendLine("Batteria non ottimizzata: ${yn(pm.isIgnoringBatteryOptimizations(packageName))}")
        sb.appendLine("")
        sb.appendLine("Protezioni armate: ${yn(Store.armed)}")
        sb.appendLine("App in blacklist: ${Store.rules().size}")
        sb.appendLine("Uso schermo oggi (min): ${Store.screenMsToday() / 60000}")
        sb.appendLine("Blocco attivo ora: ${yn(Store.isLocked())}")
        sb.appendLine("")
        sb.appendLine("--- ultimo errore ---")
        sb.append(CrashLog.last() ?: "nessuno")
        return sb.toString()
    }
}
