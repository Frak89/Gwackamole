package com.frak89.gwackamole.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.EventLog
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.ui.CooldownActivity
import com.frak89.gwackamole.ui.GuardActivity
import com.frak89.gwackamole.ui.LockActivity
import com.frak89.gwackamole.ui.UnlockGateActivity
import com.frak89.gwackamole.ui.UsageReportActivity
import kotlin.random.Random

/** Perché è scattato il blocco. */
enum class Reason(val title: String) {
    TIME_UP("Tempo finito"),
    RANDOM("Controllo a sorpresa"),
    ADULT_SITE("Sito bloccato"),
    ADULT_LINK("Link coperto"),
    USER_SITE("Sito in blacklist"),
    BYPASS("Tentativo di aggirare le regole"),
    TEST("Prova"),
}

/** Ponte verso il servizio di accessibilità (azioni globali). */
interface GuardianHost {
    val context: Context
    fun goHome()
    fun goBack()
    fun dismissShade()
    /** Cancella dalla cronologia del browser le voci del dominio appena bloccato (best-effort, browser Chromium). */
    fun cleanHistory(browserPkg: String, domain: String)
}

/**
 * Il cervello dell'app: decide quando far comparire le talpe, cosa succede se vinci o perdi,
 * e tiene d'occhio i tentativi di fuga.
 */
object Guardian {
    private val h = Handler(Looper.getMainLooper())
    private var host: GuardianHost? = null
    /** Contesto di riserva (applicazione) quando il servizio non è attaccato. */
    @Volatile var appContext: Context? = null
    private val ctx: Context? get() = host?.context ?: appContext

    // stato corrente
    @Volatile var fgPkg: String? = null; private set
    private var fgSince = 0L
    private var currentHost: String? = null

    private var scheduledKey: String? = null
    private var lastRandomAt = 0L
    private var lastRandomRoll = 0L
    var screenOnSince = System.currentTimeMillis()
    private val recentlyHandled = HashMap<String, Long>()

    private const val INTENSE_MS = 15 * 60_000L   // sessione ≥ 15 min = intensa
    private const val REOPEN_GAP_MS = 15 * 60_000L // riapertura entro 15 min dall'uso intenso

    private val ignorePkgs = setOf("com.android.systemui", "android")
    private val alwaysAllowedWhenLocked = setOf(
        "com.android.phone", "com.android.server.telecom", "com.android.dialer",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.emergency", "com.google.android.apps.safetyhub", "com.android.incallui",
    )

    private val tickRunnable = object : Runnable {
        override fun run() {
            try { tick() } catch (e: Throwable) { com.frak89.gwackamole.data.CrashLog.note("tick", e) }
            h.postDelayed(this, 5_000)
        }
    }

    fun attach(host: GuardianHost) {
        this.host = host
        appContext = host.context.applicationContext
        h.removeCallbacks(tickRunnable)
        h.postDelayed(tickRunnable, 1_500) // lascia finire l'aggancio del servizio prima del primo giro
        try { Protection.apply(host.context) } catch (_: Throwable) { }
    }

    fun detach() {
        h.removeCallbacks(tickRunnable)
        host = null
    }

    private fun own(pkg: String) = ctx?.packageName == pkg

    private fun dialerPkg(): String? = try {
        (ctx?.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    } catch (_: Exception) { null }

    // ------------------------------------------------------------------ eventi dal servizio

    fun onForeground(pkg: String) {
        if (pkg in ignorePkgs) return
        val now = System.currentTimeMillis()
        if (pkg != fgPkg) {
            closeSession(now)
            fgPkg = pkg
            fgSince = now
            currentHost = null
            if (Store.rules().containsKey(pkg)) EventLog.log("OPEN", pkg)
        }
        if (own(pkg)) return

        // telefono in pausa: si torna al conto alla rovescia (chiamate sempre permesse)
        if (Store.isLocked(now)) {
            if (pkg !in alwaysAllowedWhenLocked && pkg != dialerPkg()) showLock()
            return
        }
        // app in blacklist (timer)
        checkAppTime(pkg)
        // app molto usate oggi: cooldown se la riapri poco dopo un uso intenso
        checkReopenCooldown(pkg, now)
    }

    private fun closeSession(now: Long) {
        val p = fgPkg ?: return
        val dur = now - fgSince
        if (dur <= 0) return
        if (Store.rules().containsKey(p)) {
            Store.addUsedMs(p, dur)
            if (dur > 2_000) EventLog.log("SESSION", p, dur)
        }
        // una sessione lunga è "intensa": segna la fine, serve al cooldown di riapertura
        if (dur >= INTENSE_MS) Store.setLastIntenseEnd(p, now)
    }

    /**
     * App usata oltre il limite auto (default 1 h/giorno): se la riapri entro pochi minuti dalla
     * fine di un uso intenso, parte un cooldown (default 1 minuto) prima di poterla usare.
     */
    private fun checkReopenCooldown(pkg: String, now: Long) {
        val c = ctx ?: return
        val limitMin = Store.autoLimitMin
        if (limitMin <= 0 || own(pkg)) return
        if (Store.cooldownClearedUntil(pkg) > now) return
        if (UsageTracker.usedTodayMs(c, pkg) < limitMin * 60_000L) return
        val since = now - Store.lastIntenseEnd(pkg)
        if (since !in 1 until REOPEN_GAP_MS) return
        val key = "cd:$pkg"
        if ((recentlyHandled[key] ?: 0) > now - 5_000) return
        recentlyHandled[key] = now
        EventLog.log("COOLDOWN", pkg)
        c.startActivity(Intent(c, CooldownActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(CooldownActivity.EXTRA_PKG, pkg)
            .putExtra(CooldownActivity.EXTRA_LABEL, labelOf(pkg)))
    }

    private fun labelOf(pkg: String): String = try {
        val pm = ctx!!.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) { pkg.substringAfterLast('.') }

    fun onScreen(on: Boolean) {
        val now = System.currentTimeMillis()
        if (on) {
            screenOnSince = now
        } else {
            Store.addScreenMs(now - screenOnSince) // conteggio uso di oggi
            closeSession(now).also { fgSince = now }
        }
    }

    /**
     * Telefono sbloccato (schermo acceso + sblocco). A caso, e tanto più spesso quanto più hai
     * usato il telefono oggi, compare la schermata "Vuoi proprio sbloccarmi?" con 1–3 conferme
     * (0 = niente, se l'uso è ancora fresco).
     */
    fun onUnlock() {
        val c = ctx ?: return
        if (!Store.gateEnabled || Store.isLocked()) return
        val now = System.currentTimeMillis()
        if ((recentlyHandled["gate"] ?: 0) > now - 3_000) return
        val tier = Store.usageTier()
        if (tier <= 0) return
        val chance = when (tier) { 1 -> 0.30; 2 -> 0.60; else -> 0.90 }
        if (Random.nextDouble() >= chance) return
        recentlyHandled["gate"] = now
        c.startActivity(Intent(c, UnlockGateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(UnlockGateActivity.EXTRA_STEPS, tier))
    }

    /** URL letto dalla barra degli indirizzi di un browser. */
    fun onUrl(browserPkg: String, url: String) {
        if (fgPkg != browserPkg || Store.isLocked()) return
        val hostName = Blocklist.hostOf(url) ?: return
        currentHost = hostName
        if (Blocklist.isSearchEngine(hostName)) return // la ricerca non si tocca: si coprono solo i link
        val now = System.currentTimeMillis()

        val adult = Blocklist.isAdultDomain(hostName) ||
            (Blocklist.looksAdult(url.substringAfter(hostName, "")) && Store.adultFilter)
        val userSite = Blocklist.isUserSite(hostName)

        when {
            adult -> scheduleSiteBlock(browserPkg, hostName, Reason.ADULT_SITE, Store.siteGraceSec)
            userSite != null -> scheduleSiteBlock(browserPkg, userSite, Reason.USER_SITE, 0)
        }
    }

    private fun scheduleSiteBlock(browserPkg: String, site: String, reason: Reason, graceSec: Int) {
        val key = "site:$site"
        val now = System.currentTimeMillis()
        if ((recentlyHandled[key] ?: 0) > now - 4_000) return
        recentlyHandled[key] = now
        h.postDelayed({
            // dopo la tolleranza, se sei ancora lì: pulisci la cronologia, poi indietro e blocco
            if (fgPkg == browserPkg && currentHost?.endsWith(site) == true) {
                EventLog.log("SITE_BLOCK", key)
                if (Store.cleanHistory) {
                    host?.cleanHistory(browserPkg, site)
                    h.postDelayed({ host?.goBack(); block(key, site, reason) }, 1_600)
                } else {
                    host?.goBack()
                    block(key, site, reason)
                }
            }
        }, graceSec * 1000L + 50)
    }

    /** Tap sul banner che copre un link. La ricerca resta dov'è: si blocca solo il telefono. */
    fun onCoveredLinkTapped(domainOrLabel: String) {
        EventLog.log("SITE_BLOCK", "site:$domainOrLabel")
        block("site:$domainOrLabel", domainOrLabel, Reason.ADULT_LINK)
    }

    /**
     * Il servizio ha visto la pagina di gestione DI Nero Block (Info app, disinstalla, forza
     * arresto, togli accessibilità/admin). Il blocco qui è: chiudere di forza le impostazioni e
     * mostrare un testo.
     */
    fun onBypassAttempt(what: String) {
        if (!Store.armed) return
        val now = System.currentTimeMillis()
        if ((recentlyHandled["bypass"] ?: 0) > now - 1_500) return
        recentlyHandled["bypass"] = now
        // chiusura forzata delle impostazioni
        host?.goHome()
        EventLog.log("BYPASS", what)
        val n = Store.incBypass()
        val c = ctx ?: return
        // testo che compare
        c.startActivity(Intent(c, GuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("what", what).putExtra("count", n))
    }

    // ------------------------------------------------------------------ controlli periodici

    private fun checkAppTime(pkg: String) {
        val c = ctx ?: return
        val rule = Store.rules()[pkg] ?: return
        if (UsageTracker.remainingMs(c, pkg) > 0) return
        val key = "app:$pkg"
        if (scheduledKey == key) return
        scheduledKey = key
        h.postDelayed({
            scheduledKey = null
            if (fgPkg == pkg && !Store.isLocked() && UsageTracker.remainingMs(c, pkg) <= 0) {
                block(pkg, rule.label, Reason.TIME_UP)
            }
        }, rule.graceSec * 1000L + 50)
    }

    private fun tick() {
        val c = ctx ?: return
        val now = System.currentTimeMillis()
        applyDuePending(c)
        maybeShowReport(c)

        val pkg = fgPkg ?: return
        if (Store.isLocked(now)) {
            if (!own(pkg) && pkg !in alwaysAllowedWhenLocked && pkg != dialerPkg()) showLock()
            return
        }
        if (own(pkg)) return

        checkAppTime(pkg)

        // controlli a sorpresa (solo se impostati, con probabilità adattata al comportamento)
        val rule = Store.rules()[pkg]
        if (rule != null && rule.randomChecks && Store.randomChecks &&
            now - fgSince > 120_000 && now - lastRandomAt > 300_000 && now - lastRandomRoll > 60_000
        ) {
            lastRandomRoll = now
            val p = 0.04 + Behavior.risk(pkg) * 0.12
            if (Random.nextDouble() < p) {
                lastRandomAt = now
                EventLog.log("RANDOM", pkg)
                block(pkg, rule.label, Reason.RANDOM)
                return
            }
        }

    }

    private fun applyDuePending(c: Context) {
        for (p in Store.takeDuePending()) PendingApplier.apply(c, p)
    }

    /** Mostra il grafico d'uso una volta al giorno, quando superi la soglia di minuti totali. */
    private fun maybeShowReport(c: Context) {
        if (Store.reportShownDate == Store.today()) return
        val limit = Store.usageReportMin
        if (limit <= 0) return
        if (UsageTracker.totalTodayMs(c) < limit * 60_000L) return
        Store.reportShownDate = Store.today()
        c.startActivity(Intent(c, UsageReportActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ------------------------------------------------------------------ blocco

    /**
     * Il blocco di Nero Block: uscita forzata dall'app (torni alla Home) e telefono in pausa per
     * un po' (default 1 minuto, crescente con le recidive della giornata). Niente minigioco.
     * Su un link di ricerca coperto la pagina resta dov'è: non si torna alla Home, si blocca solo.
     */
    fun block(target: String, label: String, reason: Reason) {
        val c = ctx ?: return
        val now = System.currentTimeMillis()
        if ((recentlyHandled["block"] ?: 0) > now - 2_000) return
        recentlyHandled["block"] = now
        Store.incAttempts(target)
        Store.incLosses()
        EventLog.log("BLOCK", target, reason.ordinal.toLong())
        // messaggio adatto al tipo/contenuto dell'app e alla durezza secondo l'uso di oggi
        val pkgGuess = if (target.startsWith("site:") || target == "test") null else target
        val kind = if (target.startsWith("site:")) AppKind.ADULT else null
        Store.lockMessage = Messages.forApp(label, pkgGuess, kind = kind)
        if (reason != Reason.ADULT_LINK) host?.goHome()
        val sec = Store.kickLockSec + Store.lockStepSec * (Store.lossesToday() - 1).coerceAtLeast(0)
        Store.lockUntil = now + sec * 1000L
        h.postDelayed({
            c.startActivity(Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, 150)
        h.postDelayed({ Protection.lockNow(c) }, 700)
    }

    private fun showLock() {
        val c = ctx ?: return
        c.startActivity(Intent(c, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
