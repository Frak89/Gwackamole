package com.frak89.gwackamole.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import com.frak89.gwackamole.core.Guardian
import com.frak89.gwackamole.core.GuardianHost
import com.frak89.gwackamole.data.Blocklist
import com.frak89.gwackamole.data.EventLog
import com.frak89.gwackamole.data.Store
import java.util.ArrayDeque
import java.util.Locale

/**
 * Occhi e mani di Gwackamole:
 *  - capisce quale app è in primo piano;
 *  - legge l'URL dei browser e i risultati di ricerca, copre i link vietati con la talpa;
 *  - riconosce Impostazioni › App › Gwackamole, il dialogo di disinstallazione, la pagina
 *    Accessibilità ecc. e attiva le protezioni.
 */
class GwackAccessibilityService : AccessibilityService(), GuardianHost {

    override val context: Context get() = this

    private val h = Handler(Looper.getMainLooper())
    private lateinit var overlay: BlockOverlay
    private lateinit var shield: Shield
    private var imePkgs: Set<String> = emptySet()
    private var browsers: Set<String> = emptySet()
    private var launcherPkgs: Set<String> = emptySet()
    private var settingsCheckPending = false
    private var scanPkg: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            try {
                when (i.action) {
                    Intent.ACTION_SCREEN_ON -> Guardian.onScreen(true)
                    Intent.ACTION_SCREEN_OFF -> { Guardian.onScreen(false); overlay.clear() }
                    Intent.ACTION_USER_PRESENT -> Guardian.onUnlock()
                }
            } catch (_: Throwable) { }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Tutto in try/catch: un errore qui farebbe segnare il servizio come "malfunzionante".
        try { Store.init(this); EventLog.init(this); Blocklist.init(this) } catch (_: Throwable) { }
        overlay = BlockOverlay(this)
        shield = Shield(this)
        try { refreshPackageSets() } catch (_: Throwable) { }
        try {
            registerReceiver(screenReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            })
        } catch (_: Throwable) { }
        try { Guardian.attach(this) } catch (_: Throwable) { }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) { }
        overlay.clear()
        shield.hide()
        Guardian.detach()
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {}

    private fun refreshPackageSets() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imePkgs = imm.enabledInputMethodList.map { it.packageName }.toSet()
        val pm = packageManager
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
        browsers = pm.queryIntentActivities(web, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet() +
            URL_BAR_IDS.keys
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        launcherPkgs = pm.queryIntentActivities(home, PackageManager.MATCH_ALL).map { it.activityInfo.packageName }.toSet()
    }

    // ------------------------------------------------------------------ GuardianHost

    override fun goHome() { performGlobalAction(GLOBAL_ACTION_HOME) }
    override fun goBack() { performGlobalAction(GLOBAL_ACTION_BACK) }
    override fun dismissShade() {
        if (Build.VERSION.SDK_INT >= 31) performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        else performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Cancella dalla cronologia le voci del dominio bloccato. Funziona sui browser Chromium
     * (Chrome, Brave, Edge, Kiwi, Vivaldi…): apre chrome://history filtrata per il dominio tramite
     * la barra indirizzi e tocca i pulsanti "Elimina/Remove" trovati. Durante l'operazione uno
     * scudo opaco copre lo schermo, così non si vede la pagina della cronologia.
     */
    override fun cleanHistory(browserPkg: String, domain: String) {
        if (Build.VERSION.SDK_INT < 30 || browserPkg !in CHROMIUM) return
        overlay.clear()
        shield.show("Pulizia della cronologia…")
        val root = rootInActiveWindow ?: run { shield.hide(); return }
        val urlNode = findUrlNode(root, browserPkg) ?: run { shield.hide(); return }
        // scrivi l'indirizzo della cronologia filtrata e invia
        val args = android.os.Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "chrome://history/?q=$domain")
        }
        urlNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        if (!urlNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) { shield.hide(); return }
        h.postDelayed({
            rootInActiveWindow?.let { r ->
                findUrlNode(r, browserPkg)?.performAction(
                    AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
            }
        }, 150)
        // dopo il caricamento, tocca i pulsanti di eliminazione per qualche passata
        historyPasses(browserPkg, 0)
    }

    private fun historyPasses(browserPkg: String, pass: Int) {
        h.postDelayed({
            val r = try { rootInActiveWindow } catch (_: Throwable) { null }
            if (r == null || r.packageName?.toString() != browserPkg || pass >= 6) { shield.hide(); return@postDelayed }
            var clicked = 0
            for (n in bfs(r, 1500)) {
                if (!n.isClickable) continue
                val desc = (n.contentDescription ?: n.text)?.toString()?.lowercase(Locale.ROOT) ?: continue
                if (HISTORY_DELETE_WORDS.any { desc.contains(it) }) {
                    if (n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) clicked++
                    if (clicked >= 4) break
                }
            }
            if (clicked > 0) historyPasses(browserPkg, pass + 1)
            else { EventLog.log("HISTORY_CLEAN", browserPkg); shield.hide() }
        }, if (pass == 0) 700 else 350)
    }

    // ------------------------------------------------------------------ eventi

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        try { handleEvent(e) } catch (_: Throwable) { /* mai far cadere il servizio per un evento */ }
    }

    private fun handleEvent(e: AccessibilityEvent) {
        val pkg = e.packageName?.toString() ?: return
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg in imePkgs) return
                // le nostre finestre overlay non devono "rubare" il primo piano
                if (pkg == packageName && rootInActiveWindow?.packageName?.toString() != packageName) return
                Guardian.onForeground(pkg)
                if (pkg !in browsers) overlay.clear()
                onContent(pkg)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                if (pkg in browsers) {
                    // sposta subito i banner col dito, poi riscansiona con un attimo di ritardo
                    if (Build.VERSION.SDK_INT >= 28) {
                        val dy = e.scrollDeltaY
                        if (dy != 0 && dy != Int.MIN_VALUE) overlay.shift(-dy)
                    } else {
                        overlay.clear()
                    }
                    scheduleBrowserScan(pkg, 120)
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED -> onContent(pkg)
        }
    }

    private fun onContent(pkg: String) {
        if (pkg in SETTINGS_PKGS || pkg in launcherPkgs) scheduleSettingsCheck()
        if (pkg in browsers) scheduleBrowserScan(pkg, 120)
    }

    // ------------------------------------------------------------------ protezione impostazioni

    private fun scheduleSettingsCheck() {
        if (settingsCheckPending || !Store.armed) return
        settingsCheckPending = true
        h.postDelayed({ settingsCheckPending = false; try { checkSettings() } catch (_: Throwable) { } }, 250)
    }

    private fun checkSettings() {
        val root = rootInActiveWindow ?: return
        val rootPkg = root.packageName?.toString() ?: return
        val label = APP_LABEL.lowercase(Locale.ROOT)

        // Il nome dell'app deve comparire come titolo/voce a sé (non solo citato in mezzo al testo):
        // così si capisce di essere davvero sulla pagina DI Gwackamole, non in impostazioni qualsiasi.
        val onOurPage = bfs(root, 900).any { n ->
            val t = n.text?.toString()?.trim()?.lowercase(Locale.ROOT)
            t == label || (t != null && t.startsWith(label) && t.length < label.length + 18)
        }
        if (!onOurPage) return

        val joined = collectTexts(root, 700).joinToString(" \u0001 ").lowercase(Locale.ROOT)
        val isInstaller = rootPkg.contains("packageinstaller") || rootPkg.contains("permissioncontroller")
        val danger = DANGER_WORDS.firstOrNull { joined.contains(it) }
        val launcherUninstall = rootPkg in launcherPkgs && (joined.contains("disinstalla") || joined.contains("uninstall"))
        // Nelle impostazioni serve anche un'azione pericolosa in pagina (disinstalla, forza arresto,
        // disattiva accessibilità/admin...): la sola pagina Info app non viene interrotta.
        if (isInstaller || launcherUninstall || (rootPkg in SETTINGS_PKGS && danger != null)) {
            Guardian.onBypassAttempt("$rootPkg: ${danger ?: "installer"}")
        }
    }

    // ------------------------------------------------------------------ browser

    private fun scheduleBrowserScan(pkg: String, delay: Long) {
        scanPkg = pkg
        h.removeCallbacks(browserScan)
        h.postDelayed(browserScan, delay)
    }

    private val browserScan = Runnable { try { runBrowserScan() } catch (_: Throwable) { } }

    private fun runBrowserScan() {
        val pkg = scanPkg ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        val urlNode = findUrlNode(root, pkg)
        val urlText = urlNode?.text?.toString()
        if (urlText != null && urlNode.isFocused.not()) Guardian.onUrl(pkg, urlText)

        val web = findWebView(root)
        val host = urlText?.let { Blocklist.hostOf(it) }
        val title = web?.let { (it.text ?: it.contentDescription)?.toString() } ?: ""
        val isSerp = (host != null && Blocklist.isSearchEngine(host)) || SERP_TITLES.any { title.contains(it, true) }

        if (Store.serpCover && Store.adultFilter && web != null && isSerp && Store.isLocked().not()) {
            val query = urlText?.takeIf { host == null || it.contains(' ') } ?: ""
            overlay.show(findCoverTargets(web, query))
        } else {
            overlay.clear()
        }
    }

    private fun findUrlNode(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        URL_BAR_IDS[pkg]?.forEach { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        // browser sconosciuto: primo campo di testo in alto che sembra un URL
        return bfs(root, 300).firstOrNull { n ->
            val cls = n.className?.toString() ?: ""
            val r = Rect().also { n.getBoundsInScreen(it) }
            cls.contains("EditText") && r.top < resources.displayMetrics.heightPixels / 4 &&
                n.text?.let { Blocklist.hostOf(it.toString()) } != null
        }
    }

    private fun findWebView(root: AccessibilityNodeInfo): AccessibilityNodeInfo? =
        bfs(root, 600).firstOrNull { it.className?.toString() == "android.webkit.WebView" }

    /** Trova i risultati da coprire: link cliccabili con parole vietate o domini in blacklist. */
    private fun findCoverTargets(web: AccessibilityNodeInfo, query: String): List<BlockOverlay.Target> {
        val screenH = resources.displayMetrics.heightPixels
        val webRect = Rect().also { web.getBoundsInScreen(it) }
        val found = ArrayList<BlockOverlay.Target>()
        val q = query.trim().lowercase(Locale.ROOT)

        for (n in bfs(web, 2500)) {
            if (!n.isClickable || n.isEditable) continue
            if (n.className?.toString()?.contains("EditText") == true) continue
            if (!n.isVisibleToUser) continue
            val r = Rect().also { n.getBoundsInScreen(it) }
            if (r.height() < 24 || r.height() > screenH * 0.6 || !Rect.intersects(r, webRect)) continue
            r.intersect(webRect)

            val blob = collectTexts(n, 40, 5).joinToString(" ")
            if (blob.length < 3) continue
            if (q.isNotEmpty() && blob.trim().lowercase(Locale.ROOT) == q) continue

            val domains = Blocklist.extractDomains(blob).filterNot { Blocklist.isSearchEngine(it) }
            val adultText = Blocklist.looksAdult(blob)
            val adultDomain = domains.firstOrNull { Blocklist.isAdultDomain(it) }
            if (!adultText && adultDomain == null) continue

            // impara i siti dai risultati: se lo snippet dice "porn", il dominio va in blacklist
            if (Blocklist.looksStronglyAdult(blob)) domains.forEach { Blocklist.learn(it) }
            val label = adultDomain ?: domains.firstOrNull() ?: "ricerca"
            found.add(BlockOverlay.Target(r, label))
        }
        // tieni il riquadro più esterno: niente banner dentro banner
        val sorted = found.sortedByDescending { it.bounds.width() * it.bounds.height() }
        val kept = ArrayList<BlockOverlay.Target>()
        for (t in sorted) if (kept.none { it.bounds.contains(t.bounds) }) kept.add(t)
        return kept.sortedBy { it.bounds.top }
    }

    // ------------------------------------------------------------------ utilità albero

    private fun bfs(root: AccessibilityNodeInfo, limit: Int): Sequence<AccessibilityNodeInfo> = sequence {
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        var n = 0
        while (q.isNotEmpty() && n < limit) {
            val cur = q.poll() ?: break
            n++
            yield(cur)
            for (i in 0 until cur.childCount) cur.getChild(i)?.let { q.add(it) }
        }
    }

    private fun collectTexts(root: AccessibilityNodeInfo, limit: Int, maxDepth: Int = 64): List<String> {
        val out = ArrayList<String>()
        fun walk(n: AccessibilityNodeInfo, depth: Int) {
            if (out.size >= limit || depth > maxDepth) return
            n.text?.let { if (it.isNotBlank()) out.add(it.toString()) }
            n.contentDescription?.let { if (it.isNotBlank()) out.add(it.toString()) }
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it, depth + 1) }
        }
        walk(root, 0)
        return out
    }

    companion object {
        const val APP_LABEL = "Nero Block"

        val SETTINGS_PKGS = setOf(
            "com.android.settings", "com.samsung.android.settings", "com.google.android.packageinstaller",
            "com.android.packageinstaller", "com.google.android.permissioncontroller",
            "com.android.permissioncontroller", "com.miui.securitycenter", "com.miui.packageinstaller",
            "com.coloros.safecenter", "com.oplus.safecenter", "com.huawei.systemmanager",
            "com.samsung.android.lool", "com.samsung.android.sm.devicesecurity",
        )

        /**
         * Azioni che compaiono solo sulla pagina di gestione DI un'app (Info app, Accessibilità,
         * Amministratori dispositivo): servono per distinguere "sto guardando Gwackamole" da
         * "sto per disinstallarlo / fermarlo / togliergli i permessi". Niente parole generiche
         * come batteria, archiviazione o consenti, che comparirebbero anche in pagine innocue.
         */
        val DANGER_WORDS = listOf(
            "disinstalla", "uninstall", "forza interruzione", "forza arresto", "termina", "force stop",
            "disattiva", "disable", "cancella dati", "cancella memoria", "svuota", "clear data", "clear storage",
            "amministratore", "device admin", "usa nero block", "use nero block", "disattiva servizio",
            "turn off", "arresto forzato", "rimuovi", "remove",
        )

        val SERP_TITLES = listOf("Cerca con Google", "Google Search", "- Bing", "at DuckDuckGo", "su DuckDuckGo", "Ricerca Google")

        /** Browser Chromium su cui chrome://history funziona. */
        val CHROMIUM = setOf(
            "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.brave.browser",
            "com.microsoft.emmx", "com.vivaldi.browser", "com.kiwibrowser.browser",
            "com.mi.globalbrowser", "com.yandex.browser", "com.opera.browser",
        )

        /** Testo dei pulsanti che eliminano una voce dalla cronologia. */
        val HISTORY_DELETE_WORDS = listOf(
            "elimina dalla cronologia", "rimuovi dalla cronologia", "remove from history",
            "delete from history", "elimina", "rimuovi", "delete", "remove",
        )

        val URL_BAR_IDS = mapOf(
            "com.android.chrome" to listOf("com.android.chrome:id/url_bar"),
            "com.chrome.beta" to listOf("com.chrome.beta:id/url_bar"),
            "com.chrome.dev" to listOf("com.chrome.dev:id/url_bar"),
            "com.brave.browser" to listOf("com.brave.browser:id/url_bar"),
            "com.microsoft.emmx" to listOf("com.microsoft.emmx:id/url_bar"),
            "com.vivaldi.browser" to listOf("com.vivaldi.browser:id/url_bar"),
            "com.kiwibrowser.browser" to listOf("com.kiwibrowser.browser:id/url_bar"),
            "com.sec.android.app.sbrowser" to listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/custom_tab_toolbar_url_bar_text",
            ),
            "org.mozilla.firefox" to listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view"),
            "org.mozilla.firefox_beta" to listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view"),
            "org.mozilla.focus" to listOf("org.mozilla.focus:id/mozac_browser_toolbar_url_view"),
            "com.opera.browser" to listOf("com.opera.browser:id/url_field"),
            "com.opera.mini.native" to listOf("com.opera.mini.native:id/url_field"),
            "com.duckduckgo.mobile.android" to listOf("com.duckduckgo.mobile.android:id/omnibarTextInput"),
            "com.mi.globalbrowser" to listOf("com.mi.globalbrowser:id/url"),
            "com.yandex.browser" to listOf("com.yandex.browser:id/bro_omnibar_address_title_text"),
        )
    }
}

/** Schermo opaco a tutto campo mostrato mentre si pulisce la cronologia. */
private class Shield(private val ctx: Context) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    private var view: android.view.View? = null

    fun show(message: String) {
        if (view != null) return
        val dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val tv = android.widget.TextView(ctx).apply {
            text = message
            gravity = android.view.Gravity.CENTER
            textSize = 16f
            setTextColor(if (dark) 0xFFB0B0B0.toInt() else 0xFF5C4D3D.toInt())
            setBackgroundColor(if (dark) 0xFF1F1F1F.toInt() else 0xFFF7F1E3.toInt())
        }
        val lp = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.OPAQUE,
        ).apply { windowAnimations = 0 }
        try { wm.addView(tv, lp); view = tv } catch (_: Exception) { }
    }

    fun hide() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        view = null
    }
}
