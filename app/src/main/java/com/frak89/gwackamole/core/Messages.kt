package com.frak89.gwackamole.core

import com.frak89.gwackamole.data.Store
import java.util.Locale
import kotlin.random.Random

/** Categoria di un'app, usata per adattare i messaggi al contenuto. */
enum class AppKind { SOCIAL, VIDEO, GAME, CHAT, SHOP, NEWS, BROWSER, ADULT, OTHER }

/**
 * Messaggi mostrati nel blocco, nella conferma di sblocco e nel cooldown.
 * Sono adattati a:
 *  - il tipo e il contenuto dell'app che stai aprendo (scroll, video, gioco, chat…);
 *  - quanto hai usato il telefono oggi: più sali di livello, più i toni diventano duri ed espliciti.
 * Restano fermi e diretti, mai offensivi sul tuo valore personale.
 */
object Messages {

    fun kindOf(pkg: String?, label: String? = null): AppKind {
        val p = (pkg ?: "").lowercase(Locale.ROOT)
        val l = (label ?: "").lowercase(Locale.ROOT)
        fun has(vararg k: String) = k.any { p.contains(it) || l.contains(it) }
        return when {
            has("instagram", "tiktok", "facebook", "snapchat", "twitter", "x.android", "reddit",
                "pinterest", "tumblr", "threads", "bereal", "lemon8") -> AppKind.SOCIAL
            has("youtube", "netflix", "primevideo", "disney", "twitch", "vlc", "hulu", "dazn",
                "raiplay", "mediaset", "nowtv", "crunchyroll", "video") -> AppKind.VIDEO
            has("game", "gioco", "clash", "roblox", "minecraft", "pubg", "genshin", "candy",
                "supercell", "king.", "playrix", "miHoYo".lowercase()) -> AppKind.GAME
            has("whatsapp", "telegram", "messenger", "discord", "signal", "wechat", "viber") -> AppKind.CHAT
            has("amazon", "shop", "ebay", "aliexpress", "shein", "temu", "zalando", "vinted") -> AppKind.SHOP
            has("news", "google.android.apps.magazines", "flipboard", "feedly") -> AppKind.NEWS
            has("chrome", "browser", "firefox", "opera", "brave", "edge", "duckduckgo", "samsung.app.sbrowser") -> AppKind.BROWSER
            else -> AppKind.OTHER
        }
    }

    /** Frase sul contenuto dell'app, per categoria. */
    private fun contentLine(kind: AppKind, label: String): List<String> = when (kind) {
        AppKind.SOCIAL -> listOf(
            "Un altro giro di scroll infinito su $label?",
            "$label è fatta per non farti smettere. Lo sai.",
            "Quante vite degli altri prima di tornare alla tua?",
        )
        AppKind.VIDEO -> listOf(
            "Un altro video su $label e poi un altro ancora.",
            "L'autoplay di $label non si ferma da solo: fallo tu.",
            "Quante ore di $label oggi?",
        )
        AppKind.GAME -> listOf(
            "Ancora una partita a $label? È sempre 'ancora una'.",
            "$label può aspettare. La tua giornata no.",
        )
        AppKind.CHAT -> listOf(
            "$label può aspettare qualche minuto.",
            "Nessun messaggio urgente scappa in un minuto.",
        )
        AppKind.SHOP -> listOf(
            "Ti serve davvero, o stai solo scorrendo $label?",
            "$label vive del tuo tempo e dei tuoi soldi.",
        )
        AppKind.NEWS -> listOf(
            "Aggiornare le notizie di continuo non le cambia.",
            "$label sarà lì anche fra un'ora.",
        )
        AppKind.BROWSER -> listOf(
            "Cosa cercavi davvero prima di perderti qui?",
            "Una ricerca, non un pozzo senza fondo.",
        )
        AppKind.ADULT -> listOf(
            "Fermati un attimo prima di continuare.",
            "Questo contenuto è bloccato.",
        )
        AppKind.OTHER -> listOf(
            "Ti serve davvero aprire $label adesso?",
            "$label può aspettare.",
        )
    }

    /** Intensificatore secondo il livello d'uso di oggi (0 lieve → 3 duro ed esplicito). */
    private fun pressure(tier: Int): List<String> = when (tier) {
        0 -> listOf("")
        1 -> listOf("Prenditi un secondo.", "Respira e scegli.")
        2 -> listOf(
            "Oggi stai già esagerando.",
            "Lo stai facendo senza accorgertene.",
            "È la stessa app, di nuovo.",
        )
        else -> listOf(
            "BASTA. Stai buttando via la giornata e nemmeno te ne accorgi.",
            "Hai passato ore qui dentro oggi. Posa il telefono, adesso.",
            "Questo è uso compulsivo. Chiudi e fai qualcos'altro di reale.",
            "Ti stai rubando il tempo da solo. Smettila ora.",
        )
    }

    /** Un messaggio completo per l'app e il livello d'uso. Mescola i tuoi testi personalizzati. */
    fun forApp(label: String, pkg: String?, tier: Int = Store.usageTier(), kind: AppKind? = null): String {
        // ogni tanto usa un testo scritto dall'utente
        val custom = Store.customTexts()
        if (custom.isNotEmpty() && Random.nextDouble() < if (tier >= 3) 0.25 else 0.5) return custom.random()
        val k = kind ?: kindOf(pkg, label)
        val base = contentLine(k, label).random()
        val push = pressure(tier).random()
        return if (push.isEmpty()) base else "$base $push"
    }

    /** Titolo della schermata di sblocco, più severo se usi tanto. */
    fun gateTitle(tier: Int): String = when (tier) {
        1 -> "Vuoi proprio sbloccarmi?"
        2 -> "Di nuovo? Conferma."
        else -> "Fermati e conferma, sul serio."
    }
}
