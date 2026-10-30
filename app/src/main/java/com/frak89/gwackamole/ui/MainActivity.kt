package com.frak89.gwackamole.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.core.PendingApplier
import com.frak89.gwackamole.core.Protection
import com.frak89.gwackamole.core.Reason
import com.frak89.gwackamole.core.UsageTracker
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.service.GwackAccessibilityService

class MainActivity : Activity() {

    private data class Perm(val title: String, val why: String, val ok: () -> Boolean, val action: () -> Unit)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
    }

    override fun onResume() {
        super.onResume()
        if (Store.isLocked()) {
            startActivity(Intent(this, LockActivity::class.java)); return
        }
        Store.takeDuePending().forEach { PendingApplier.apply(this, it) }
        render()
    }

    private fun accessibilityOn(): Boolean {
        val cn = ComponentName(this, GwackAccessibilityService::class.java).flattenToString()
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.equals(cn, true) }
    }

    private fun perms(): List<Perm> = listOf(
        Perm("Accessibilità", "Vede app e siti aperti, copre i link vietati con la talpa, protegge le Impostazioni.",
            ::accessibilityOn) {
            if (Build.VERSION.SDK_INT >= 33) toast("Se è disattivato: Impostazioni › App › Gwackamole › ⋮ › Consenti impostazioni limitate")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        },
        Perm("Utilizzo app", "Misura il tempo di ogni app in blacklist.", { UsageTracker.hasPermission(this) }) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        },
        Perm("Mostra sopra altre app", "Fa comparire il minigioco ovunque.", { Settings.canDrawOverlays(this) }) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        },
        Perm("Amministratore dispositivo", "Spegne e blocca lo schermo se le talpe vincono.", { Protection.isAdmin(this) }) {
            startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, Protection.admin(this))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Serve per spegnere e bloccare lo schermo quando perdi."))
        },
        Perm("Notifiche", "Avvisi prima che finisca il tempo.", {
            Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }) {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        },
        Perm("Ignora ottimizzazione batteria", "Il guardiano resta sveglio in background.", {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        }) {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        },
        Perm("Device Owner (consigliato)", "Blocca disinstallazione, arresto forzato e le gesture durante il gioco. Si attiva da PC con adb.",
            { Protection.isDeviceOwner(this) }) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("adb", Protection.ADB_COMMAND))
            AlertDialog.Builder(this).setTitle("Device Owner")
                .setMessage("Comando copiato negli appunti. Da un PC con il telefono collegato in debug USB:\n\n" +
                    Protection.ADB_COMMAND + "\n\nPrima rimuovi tutti gli account dal telefono (Google compreso), " +
                    "poi riaggiungili. È il modo previsto da Android per le app di controllo.")
                .setPositiveButton("Ok", null).show()
        },
    )

    private fun render() {
        val col = page()
        col.eyebrow(if (Store.armed) "Protezioni armate" else "Configurazione")
        col.title("Gwackamole", 34f)
        col.body("Quando arriva una distrazione arrivano le talpe. Martellale tutte e guadagni minuti; se vincono loro, lo schermo si spegne.")
        col.gap(16)

        val list = perms()
        val done = list.count { it.ok() }
        col.card {
            eyebrow("Permessi · $done di ${list.size}")
            list.forEachIndexed { i, p -> permRow(this, i + 1, p) }
        }

        col.card {
            if (!Store.armed) {
                eyebrow("Modalità rigida")
                body("Armando le protezioni: niente accesso a Impostazioni › Gwackamole, niente disinstallazione, " +
                    "e ogni modifica che allenta le regole parte solo dopo ${Store.cooldownHours} ore.", C.INK)
                button("Arma le protezioni") { arm() }
            } else {
                eyebrow("Modalità rigida attiva")
                val pend = Store.pending().size
                body(if (pend == 0) "Nessuna modifica in attesa." else "$pend modifiche in attesa del cooldown.", C.INK)
                button("Disattiva protezioni (dopo ${Store.cooldownHours} h)", primary = false) { askRelease() }
            }
        }

        col.button("App in blacklist e timer") { startActivity(Intent(this, AppsActivity::class.java)) }
        col.button("Siti web e filtro adulti") { startActivity(Intent(this, SitesActivity::class.java)) }
        col.button("Regole, blocco e cooldown") { startActivity(Intent(this, RulesActivity::class.java)) }
        col.button("Analisi del comportamento", primary = false) { startActivity(Intent(this, StatsActivity::class.java)) }
        col.button("Prova il minigioco", primary = false) {
            Guardian.launchGame(this, "test", "Prova", Reason.TEST, 3, 0)
        }
    }

    private fun permRow(parent: LinearLayout, n: Int, p: Perm) {
        val ok = p.ok()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val chip = TextView(this).apply {
            text = if (ok) "✓" else "$n"; gravity = Gravity.CENTER; textSize = 15f
            setTextColor(if (ok) 0xFFFFFFFF.toInt() else 0xFF7A4A12.toInt())
            background = rounded(if (ok) C.GRASS else 0xFFFCE3C6.toInt(), dp(10).toFloat())
        }
        row.addView(chip, LinearLayout.LayoutParams(dp(36), dp(36)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(8), 0) }
        texts.addView(TextView(this).apply { text = p.title; textSize = 15f; setTextColor(C.INK); paint.isFakeBoldText = true })
        texts.addView(TextView(this).apply { text = p.why; textSize = 12f; setTextColor(C.MUTED) })
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!ok) {
            val b = Button(this).apply {
                text = "Attiva"; isAllCaps = false; setTextColor(0xFFFFFFFF.toInt()); stateListAnimator = null
                background = rounded(C.GREEN, dp(12).toFloat())
                setOnClickListener { p.action() }
            }
            row.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        }
        parent.addView(row)
    }

    private fun arm() {
        if (!accessibilityOn() || !Protection.isAdmin(this)) {
            toast("Prima attiva almeno Accessibilità e Amministratore dispositivo."); return
        }
        AlertDialog.Builder(this)
            .setTitle("Armare le protezioni?")
            .setMessage("Da questo momento:\n• Impostazioni › Gwackamole e la disinstallazione ti riportano alla Home\n" +
                "• allungare i timer o togliere app/siti dalla blacklist richiede ${Store.cooldownHours} h di attesa\n" +
                "• l'unico modo di uscire è \"Disattiva protezioni\", che parte dopo il cooldown.\n\n" +
                "Le chiamate d'emergenza restano sempre possibili.")
            .setPositiveButton("Arma") { _, _ ->
                Store.armed = true
                Protection.apply(this)
                render()
            }
            .setNegativeButton("Non ora", null).show()
    }

    private fun askRelease() {
        val owner = Protection.isDeviceOwner(this)
        AlertDialog.Builder(this)
            .setTitle("Disattivare le protezioni?")
            .setMessage("La richiesta parte tra ${Store.cooldownHours} ore. Nel frattempo puoi annullarla in Regole." +
                if (owner) "\n\nVerrà rimosso anche il Device Owner, così potrai disinstallare l'app." else "")
            .setPositiveButton("Richiedi") { _, _ ->
                report(PendingApplier.request(this, "release", if (owner) "owner" else "keep",
                    "Disattiva tutte le protezioni", looser = true))
                render()
            }
            .setNegativeButton("Annulla", null).show()
    }
}
