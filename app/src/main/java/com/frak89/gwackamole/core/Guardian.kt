package com.frak89.gwackamole.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.EventLog
import com.frak89.gwackamole.data.Store
import com.frak89.gwackamole.game.MoleGameActivity
import com.frak89.gwackamole.ui.GuardActivity
import com.frak89.gwackamole.ui.LockActivity
import kotlin.random.Random

/** Perché è comparso il minigioco. */
enum class Reason(val title: String) {
    TIME_UP("Tempo finito"),
    RANDOM("Talpa a sorpresa"),
    SCREEN("Troppo tempo sullo schermo"),
    ADULT_SITE("Sito bloccato"),
    ADULT_LINK("Link coperto dalla talpa"),
    USER_SITE("Sito in blacklist"),
    BYPASS("Tentativo di aggirare le regole"),
    TEST("Partita di prova"),
}

/** Ponte verso il servizio di accessibilità (azioni globali). */
interface GuardianHost {
    val context: Context
    fun goHome()
    fun goBack()
    fun dismissShade()
}

/**
 * Il cervello dell'app: decide quando far comparire le talpe, cosa succede se vinci o perdi,
 * e tiene d'occhio i tentativi di fuga.
 */
object Guardian {
    private val h = Handler(Looper.getMainLooper())
    private var host: GuardianHost? = null
    private val ctx: Context? get() = host?.context

    // stato corrente
    @Volatile var fgPkg: String? = null; private set
    private var fgSince = 0L
    private var currentHost: String? = null

    @Volatile var gameActive = false; private set
    private var gameStartedAt = 0L
    private var scheduledKey: String? = null
    private var lastRandomAt = 0L
    private var lastRandomRoll = 0L
    var screenOnSince = System.currentTimeMillis()
    private val recentlyHandled = HashMap<String, Long>()

    private val ignorePkgs = setOf("com.android.systemui", "android")
    private val alwaysAllowedWhenLocked = setOf(
        "com.android.phone", "com.android.server.telecom", "com.android.dialer",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.emergency", "com.google.android.apps.safetyhub", "com.android.incallui",
    )

    private val tickRunnable = object : Runnable {
        override fun run() {
            tick()
            h.postDelayed(this, 5_000)
        }
    }

    fun attach(host: GuardianHost) {
        this.host = host
        h.removeCallbacks(tickRunnable)
        h.post(tickRunnable)
        Protection.apply(host.context)
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
        if (pkg in ignorePkgs) {
            if (gameActive) host?.dismissShade()
            return
        }
        val now = System.currentTimeMillis()
        if (pkg != fgPkg) {
            closeSession(now)
            fgPkg = pkg
            fgSince = now
            currentHost = null
            if (Store.rules().containsKey(pkg)) EventLog.log("OPEN", pkg)
        }
        if (own(pkg)) return

        // 1) telefono in punizione: si torna al conto alla rovescia (chiamate sempre permesse)
        if (Store.isLocked(now)) {
            if (pkg !in alwaysAllowedWhenLocked && pkg != dialerPkg()) showLock()
            return
        }
        // 2) minigioco in corso: niente fughe
        if (gameActive) {
            if (now - gameStartedAt > 120_000) gameActive = false // sicurezza: partita orfana
            else { bringGameBack(); return }
        }
        // 3) app in blacklist
        checkAppTime(pkg)
    }

    private fun closeSession(now: Long) {
        val p = fgPkg ?: return
        val dur = now - fgSince
        if (dur > 0 && Store.rules().containsKey(p)) {
            Store.addUsedMs(p, dur)
            if (dur > 2_000) EventLog.log("SESSION", p, dur)
        }
    }

    fun onScreen(on: Boolean) {
        val now = System.currentTimeMillis()
        if (on) screenOnSince = now else closeSession(now).also { fgSince = now }
    }

    /** URL letto dalla barra degli indirizzi di un browser. */
    fun onUrl(browserPkg: String, url: String) {
        if (fgPkg != browserPkg || gameActive || Store.isLocked()) return
        val hostName = Blocklist.hostOf(url) ?: return
        currentHost = hostName
        if (Blocklist.isSearchEngine(hostName)) return // la ricerca non si tocca: si coprono solo i link
        val now = System.currentTimeMillis()

        val adult = Blocklist.isAdultDomain(hostName) ||
            (Blocklist.looksAdult(url.substringAfter(hostName, "")) && Store.adultFilter)
        val userSite = Blocklist.isUserSite(hostName)

        when {
            adult -> scheduleSiteBlock(browserPkg, hostName, Reason.ADULT_SITE, Store.siteGraceSec)
            userSite != null && Store.siteAllowedUntil(userSite) < now ->
                scheduleSiteBlock(browserPkg, userSite, Reason.USER_SITE, 0)
        }
    }

    private fun scheduleSiteBlock(browserPkg: String, site: String, reason: Reason, graceSec: Int) {
        val key = "site:$site"
        val now = System.currentTimeMillis()
        if ((recentlyHandled[key] ?: 0) > now - 4_000) return
        recentlyHandled[key] = now
        h.postDelayed({
            // dopo la tolleranza, se sei ancora lì: indietro e talpe
            if (fgPkg == browserPkg && currentHost?.endsWith(site) == true && !gameActive) {
                host?.goBack()
                EventLog.log("SITE_BLOCK", key)
                requestGame(key, site, reason)
            }
        }, graceSec * 1000L + 50)
    }

    /** Tap sul banner con la talpa che copre un link. La ricerca resta dov'è. */
    fun onCoveredLinkTapped(domainOrLabel: String) {
        if (gameActive) return
        EventLog.log("SITE_BLOCK", "site:$domainOrLabel")
        requestGame("site:$domainOrLabel", domainOrLabel, Reason.ADULT_LINK)
    }

    /** Il servizio ha visto Impostazioni › Gwackamole, il dialogo di disinstallazione, ecc. */
    fun onBypassAttempt(what: String) {
        if (!Store.armed) return
        val now = System.currentTimeMillis()
        if ((recentlyHandled["bypass"] ?: 0) > now - 1_500) return
        recentlyHandled["bypass"] = now
        host?.goHome()
        EventLog.log("BYPASS", what)
        val n = Store.incBypass()
        val c = ctx ?: return
        c.startActivity(Intent(c, GuardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra("what", what).putExtra("count", n))
        if (n >= 3) h.postDelayed({ requestGame("bypass", "Gwackamole", Reason.BYPASS) }, 1_800)
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
            if (fgPkg == pkg && !gameActive && !Store.isLocked() && UsageTracker.remainingMs(c, pkg) <= 0) {
                requestGame(pkg, rule.label, Reason.TIME_UP)
            }
        }, rule.graceSec * 1000L + 50)
    }

    private fun tick() {
        val c = ctx ?: return
        val now = System.currentTimeMillis()
        applyDuePending(c)

        val pkg = fgPkg ?: return
        if (Store.isLocked(now)) {
            if (!own(pkg) && pkg !in alwaysAllowedWhenLocked && pkg != dialerPkg()) showLock()
            return
        }
        if (gameActive || own(pkg)) return

        checkAppTime(pkg)

        // talpe a sorpresa (solo se impostate, con probabilità adattata al comportamento)
        val rule = Store.rules()[pkg]
        if (rule != null && rule.randomChecks && Store.randomChecks &&
            now - fgSince > 120_000 && now - lastRandomAt > 300_000 && now - lastRandomRoll > 60_000
        ) {
            lastRandomRoll = now
            val p = 0.04 + Behavior.risk(pkg) * 0.12
            if (Random.nextDouble() < p) {
                lastRandomAt = now
                EventLog.log("RANDOM", pkg)
                requestGame(pkg, rule.label, Reason.RANDOM)
                return
            }
        }

        // troppo tempo di schermo continuo
        val lim = Store.screenLimitMin
        if (lim > 0 && now - screenOnSince > lim * 60_000L) {
            screenOnSince = now // evita raffiche; se perdi parte comunque il blocco
            EventLog.log("SCREEN", "screen", lim.toLong())
            requestGame("screen", "Schermo", Reason.SCREEN)
        }
    }

    private fun applyDuePending(c: Context) {
        for (p in Store.takeDuePending()) PendingApplier.apply(c, p)
    }

    // ------------------------------------------------------------------ minigioco

    /** Quante talpe: parte da 1 e sale a ogni nuovo tentativo sullo stesso obiettivo, fino a 10. */
    private fun seedFor(target: String, reason: Reason): Int {
        val attempts = Store.incAttempts(target)
        val base = when (reason) {
            Reason.RANDOM -> 1 + (Behavior.risk(target) * 3).toInt()
            Reason.BYPASS -> 3 + Store.bypassToday()
            Reason.TEST -> 3
            Reason.ADULT_SITE, Reason.ADULT_LINK -> maxOf(attempts, (Store.incAttempts("adult") + 1) / 2)
            else -> attempts
        }
        return base.coerceIn(1, 10)
    }

    /** Difficoltà (velocità, esche, vite): sale con le sconfitte di oggi e col "rischio" del target. */
    private fun difficultyFor(target: String): Int =
        (Store.lossesToday() + (Behavior.risk(target) * 3).toInt() + Store.attemptsToday(target) / 3).coerceIn(0, 8)

    fun requestGame(target: String, label: String, reason: Reason) {
        val c = ctx ?: return
        if (gameActive) return
        val seed = seedFor(target, reason)
        launchGame(c, target, label, reason, seed, difficultyFor(target))
    }

    /** Usato anche dalla UI per la partita di prova. */
    fun launchGame(c: Context, target: String, label: String, reason: Reason, seed: Int, difficulty: Int) {
        gameActive = true
        gameStartedAt = System.currentTimeMillis()
        lastGameIntent = Intent(c, MoleGameActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MoleGameActivity.EXTRA_TARGET, target)
            .putExtra(MoleGameActivity.EXTRA_LABEL, label)
            .putExtra(MoleGameActivity.EXTRA_REASON, reason.name)
            .putExtra(MoleGameActivity.EXTRA_SEED, seed)
            .putExtra(MoleGameActivity.EXTRA_DIFFICULTY, difficulty)
        c.startActivity(lastGameIntent)
    }

    private var lastGameIntent: Intent? = null

    private fun bringGameBack() {
        val c = ctx ?: return
        val i = lastGameIntent ?: return
        c.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MoleGameActivity.EXTRA_RESUME, true))
    }

    fun onGameResult(c: Context, target: String, reason: Reason, won: Boolean) {
        gameActive = false
        lastGameIntent = null
        if (reason == Reason.TEST) return
        val now = System.currentTimeMillis()
        if (won) {
            EventLog.log("GAME_WIN", target)
            when (reason) {
                Reason.TIME_UP -> Store.addBonus(target, Store.bonusMinutes)
                Reason.USER_SITE -> Store.allowSite(target.removePrefix("site:"), now + Store.bonusMinutes * 60_000L)
                Reason.SCREEN -> screenOnSince = now
                else -> Unit // siti per adulti: vincere non li apre, ti riporta solo alla ricerca
            }
        } else {
            EventLog.log("GAME_LOSS", target)
            punish(c)
        }
    }

    /** Le talpe hanno vinto: schermo spento, telefono bloccato, conto alla rovescia. */
    fun punish(c: Context) {
        val minutes = Store.lockBaseMinutes + Store.lockStepMinutes * Store.lossesToday()
        Store.incLosses()
        Store.lockUntil = System.currentTimeMillis() + minutes * 60_000L
        c.startActivity(Intent(c, LockActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        h.postDelayed({ Protection.lockNow(c) }, 600)
    }

    private fun showLock() {
        val c = ctx ?: return
        c.startActivity(Intent(c, LockActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }
}
