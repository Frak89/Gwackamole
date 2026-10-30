package com.frak89.gwackamole.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Domini e parole vietate.
 *
 * Fonti, in ordine:
 *  1. lista di partenza inclusa nell'app (SEED);
 *  2. domini imparati sul telefono dai risultati di ricerca (Google, Bing, DuckDuckGo...)
 *     quando titolo o snippet del risultato contengono parole come "porn", "porno", "xxx"...;
 *  3. liste pubbliche scaricabili (formato hosts o un dominio per riga) — opzionale.
 *  4. siti aggiunti a mano dall'utente.
 */
object Blocklist {
    private lateinit var appCtx: Context
    private val io = Executors.newSingleThreadExecutor()

    @Volatile private var downloaded: Set<String> = emptySet()
    @Volatile private var learned: MutableSet<String> = HashSet()

    val SEED = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
        "spankbang.com", "eporner.com", "tube8.com", "youjizz.com", "beeg.com", "motherless.com",
        "porn.com", "brazzers.com", "onlyfans.com", "fansly.com", "chaturbate.com", "stripchat.com",
        "livejasmin.com", "bongacams.com", "cam4.com", "camsoda.com", "myfreecams.com", "xhamsterlive.com",
        "rule34.xxx", "e-hentai.org", "nhentai.net", "hentaihaven.xxx", "hqporner.com", "porntrex.com",
        "txxx.com", "upornia.com", "tnaflix.com", "drtuber.com", "sunporno.com", "pornone.com",
        "erome.com", "thumbzilla.com", "4tube.com", "porndig.com", "fapello.com", "xxxbunker.com",
    )

    /** Liste pubbliche opzionali (scaricate solo quando l'utente preme "Aggiorna"). */
    val REMOTE_LISTS = listOf(
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts",
        "https://nsfw.oisd.nl/domainswild",
    )

    /** Domini che non vengono mai "imparati" anche se compaiono vicino a parole vietate. */
    private val NEVER_LEARN = listOf(
        "google.", "gstatic.", "bing.com", "microsoft.", "duckduckgo.com", "yahoo.", "ecosia.org",
        "wikipedia.org", "wikimedia.org", "youtube.com", "reddit.com", "facebook.com", "instagram.com",
        "twitter.com", "x.com", "amazon.", "apple.com", "github.com", "gov", "edu", "gwackamole",
    )

    private val ADULT_WORDS = listOf(
        "porn", "porno", "pornhub", "xxx", "nsfw", "hentai", "milf", "nude", "nudes", "naked",
        "big tits", "bigtits", "big ass", "bigass", "boobs", "tits", "pussy", "dick", "cock",
        "blowjob", "anal", "cumshot", "creampie", "onlyfans", "camgirl", "cam girl", "sex video",
        "sex tape", "sexy video", "escort", "fuck", "erotic", "erotico", "erotica", "nudo", "nuda",
        "tette", "culo", "figa", "pompino", "scopata", "scopare", "troia", "porca", "video hard",
        "film hard", "hard gratis", "sesso", "sex", "lesbian", "lesbica", "gay porn", "jav",
        "rule34", "r34", "camsex", "webcam sex",
    )

    /** Radici che valgono anche con un suffisso (pornografia, pornostar, xxxvideos, hentaihaven...). */
    private val ADULT_STEMS = listOf(
        "porn", "xxx", "hentai", "nsfw", "onlyfans", "rule34", "milf", "blowjob", "cumshot",
        "creampie", "camgirl", "pompin", "bigtits", "bigass",
    )

    private fun alt(words: List<String>) = words.sortedByDescending { it.length }
        .joinToString("|") { it.replace(" ", "[\\s_\\-.]*") } // solo lettere/cifre/spazi: niente da escapare

    private val adultRegex: Regex by lazy {
        Regex("(?<![a-z])(?:(?:${alt(ADULT_STEMS)})|(?:${alt(ADULT_WORDS)})(?![a-z]))")
    }

    private val domainRegex = Regex("(?:https?://)?((?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,24})(?![a-z0-9-])")

    private val SEARCH_ENGINES = listOf(
        "google.", "bing.com", "duckduckgo.com", "search.yahoo.", "ecosia.org", "yandex.",
        "startpage.com", "qwant.com", "search.brave.com",
    )

    fun init(ctx: Context) {
        if (::appCtx.isInitialized) return
        appCtx = ctx.applicationContext
        io.execute {
            learned = readSet(learnedFile()).toMutableSet()
            downloaded = readSet(downloadedFile())
        }
    }

    private fun learnedFile() = File(appCtx.filesDir, "learned.txt")
    private fun downloadedFile() = File(appCtx.filesDir, "downloaded.txt")

    private fun readSet(f: File): Set<String> =
        if (f.exists()) f.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toHashSet() else HashSet()

    fun learnedDomains(): Set<String> = learned.toSet()
    fun downloadedCount(): Int = downloaded.size

    /** Normalizza testo: minuscolo, senza accenti, leetspeak base (p0rn → porn). */
    fun normalize(text: String): String {
        val n = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return n.replace('0', 'o').replace('@', 'a').replace('$', 's')
            .replace('1', 'i').replace('3', 'e')
    }

    fun looksAdult(text: String?): Boolean {
        if (text.isNullOrBlank() || !Store.adultFilter) return false
        return adultRegex.containsMatchIn(normalize(text))
    }

    private val strongRegex = Regex("(?<![a-z])(porn|porno|pornhub|xxx|nsfw|hentai|onlyfans|rule34|camsex|sex[\\s_.-]*video|video[\\s_.-]*hard)")

    /** Solo parole inequivocabili: usato per decidere se "imparare" un dominio. */
    fun looksStronglyAdult(text: String?): Boolean =
        !text.isNullOrBlank() && Store.adultFilter && strongRegex.containsMatchIn(normalize(text))

    fun hostOf(urlOrText: String): String? {
        val t = urlOrText.trim().lowercase(Locale.ROOT)
        if (t.contains(' ')) return null
        val m = domainRegex.find(t) ?: return null
        return m.groupValues[1].removePrefix("www.").removePrefix("m.")
    }

    fun extractDomains(text: String): List<String> =
        domainRegex.findAll(text.lowercase(Locale.ROOT)).map { it.groupValues[1].removePrefix("www.") }
            .filter { d -> d.count { it == '.' } >= 1 && !d.endsWith(".png") && !d.endsWith(".jpg") }
            .distinct().toList()

    fun isSearchEngine(host: String) = SEARCH_ENGINES.any { host.contains(it) }

    private fun suffixes(host: String): Sequence<String> = sequence {
        var h = host
        while (h.contains('.')) {
            yield(h)
            h = h.substringAfter('.')
        }
    }

    fun isAdultDomain(host: String): Boolean {
        if (!Store.adultFilter) return false
        return suffixes(host).any { it in SEED || it in learned || it in downloaded } ||
            // dominio con parole vietate nel nome (es. "freepornvideos.net")
            ADULT_WORDS.any { w -> w.length >= 4 && !w.contains(' ') && host.contains(w) && w != "anal" && w != "sesso" }
    }

    fun isUserSite(host: String): String? = suffixes(host).firstOrNull { it in Store.userSites() }

    fun learn(domain: String): Boolean {
        val d = domain.removePrefix("www.")
        if (NEVER_LEARN.any { d.contains(it) } || d in learned || d in SEED) return false
        learned.add(d)
        io.execute { learnedFile().appendText(d + "\n") }
        EventLog.log("LEARNED", d)
        return true
    }

    fun forget(domain: String) {
        learned.remove(domain)
        io.execute { learnedFile().writeText(learned.joinToString("\n", postfix = "\n")) }
    }

    /** Scarica le liste pubbliche (thread in background). */
    fun updateRemote(done: (Int, String?) -> Unit) {
        io.execute {
            val acc = HashSet<String>()
            var err: String? = null
            for (u in REMOTE_LISTS) {
                try {
                    val c = URL(u).openConnection() as HttpURLConnection
                    c.connectTimeout = 15000; c.readTimeout = 30000
                    c.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { raw ->
                            val l = raw.substringBefore('#').trim()
                            if (l.isEmpty()) return@forEach
                            val parts = l.split(Regex("\\s+"))
                            val d = (if (parts.size >= 2) parts[1] else parts[0])
                                .removePrefix("*.").removePrefix("www.").lowercase(Locale.ROOT)
                            if (d.contains('.') && d != "0.0.0.0" && d != "localhost") acc.add(d)
                        }
                    }
                } catch (e: Exception) {
                    err = "${e.javaClass.simpleName}: ${e.message}"
                }
            }
            if (acc.isNotEmpty()) {
                downloadedFile().writeText(acc.joinToString("\n"))
                downloaded = acc
                Store.lastListUpdate = System.currentTimeMillis()
            }
            done(acc.size, err)
        }
    }
}
