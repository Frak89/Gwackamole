package com.frak89.gwackamole.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Regola per un'app in blacklist. */
data class AppRule(
    val pkg: String,
    val label: String,
    /** Minuti al giorno. Allentare la regola richiede il cooldown. */
    val limitMin: Int,
    /** Secondi di tolleranza prima del blocco quando il tempo è finito. */
    val graceSec: Int,
    /** Controlli a sorpresa durante l'uso. */
    val randomChecks: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("pkg", pkg).put("label", label).put("limit", limitMin)
        .put("grace", graceSec).put("random", randomChecks)

    companion object {
        fun fromJson(o: JSONObject) = AppRule(
            o.getString("pkg"), o.optString("label", o.getString("pkg")),
            o.optInt("limit", 30), o.optInt("grace", 3), o.optBoolean("random", false),
        )
    }
}

/** Modifica che allenta le regole: parte solo dopo il cooldown (stile AppBlock "strict mode"). */
data class PendingChange(
    val id: Long,
    val type: String,
    val payload: String,
    val applyAt: Long,
    val description: String,
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type)
        .put("payload", payload).put("applyAt", applyAt).put("desc", description)

    companion object {
        fun fromJson(o: JSONObject) = PendingChange(
            o.getLong("id"), o.getString("type"), o.optString("payload"),
            o.getLong("applyAt"), o.optString("desc"),
        )
    }
}

object Store {
    private lateinit var sp: SharedPreferences

    fun init(ctx: Context) {
        if (!::sp.isInitialized) sp = ctx.applicationContext.getSharedPreferences("gwack", Context.MODE_PRIVATE)
    }

    fun today(): String = LocalDate.now().toString()

    // ---------- Impostazioni generali ----------
    var armed: Boolean
        get() = sp.getBoolean("armed", false)
        set(v) = sp.edit().putBoolean("armed", v).apply()

    /** Secondi prima del blocco per i siti per adulti (0 = subito, max 5). */
    var siteGraceSec: Int
        get() = sp.getInt("site_grace", 0)
        set(v) = sp.edit().putInt("site_grace", v.coerceIn(0, 5)).apply()

    /** Conferma allo sblocco: "Vuoi proprio sbloccarmi?" con 0–3 passaggi secondo l'uso. */
    var gateEnabled: Boolean
        get() = sp.getBoolean("gate_enabled", true)
        set(v) = sp.edit().putBoolean("gate_enabled", v).apply()

    /** Blocca automaticamente le app usate più di tot minuti al giorno (0 = spento). */
    var autoLimitMin: Int
        get() = sp.getInt("auto_limit", 60)
        set(v) = sp.edit().putInt("auto_limit", v).apply()

    /** Minuti d'uso totale oltre i quali, a fine soglia, compare il grafico (0 = solo a fine giornata). */
    var usageReportMin: Int
        get() = sp.getInt("report_min", 180)
        set(v) = sp.edit().putInt("report_min", v).apply()

    /** Durata (s) del cooldown prima di riaprire un'app poco dopo un uso intenso. */
    var reopenCooldownSec: Int
        get() = sp.getInt("reopen_cd_s", 60)
        set(v) = sp.edit().putInt("reopen_cd_s", v).apply()

    // ---------- Uso del telefono (per decidere conferme, severità, auto-limite) ----------
    /** Millisecondi di schermo acceso accumulati oggi. */
    fun screenMsToday(): Long = sp.getLong("screen_ms_${today()}", 0)
    fun addScreenMs(ms: Long) {
        if (ms in 1..7_200_000) sp.edit().putLong("screen_ms_${today()}", screenMsToday() + ms).apply()
    }

    /**
     * Livello d'uso di oggi: 0 fresco, 1 poco, 2 medio, 3 molto. È anche il numero di conferme
     * richieste dalla schermata di sblocco e la durezza dei messaggi.
     */
    fun usageTier(): Int {
        val min = screenMsToday() / 60_000
        return when {
            min < 25 -> 0
            min < 75 -> 1
            min < 150 -> 2
            else -> 3
        }
    }

    /** Fine di una sessione "intensa" su un'app (per il cooldown di riapertura). */
    fun lastIntenseEnd(pkg: String): Long = sp.getLong("intense_$pkg", 0)
    fun setLastIntenseEnd(pkg: String, t: Long) = sp.edit().putLong("intense_$pkg", t).apply()

    /** Finché questo istante, l'app ha già fatto il cooldown e può essere usata senza riproporlo. */
    fun cooldownClearedUntil(pkg: String): Long = sp.getLong("cdok_$pkg", 0)
    fun clearCooldown(pkg: String, until: Long) = sp.edit().putLong("cdok_$pkg", until).apply()

    /** Il grafico d'uso è già stato mostrato oggi? */
    var reportShownDate: String
        get() = sp.getString("report_date", "") ?: ""
        set(v) = sp.edit().putString("report_date", v).apply()

    /** Messaggio da mostrare nella schermata di pausa (impostato quando scatta un blocco). */
    var lockMessage: String
        get() = sp.getString("lock_msg", "") ?: ""
        set(v) = sp.edit().putString("lock_msg", v).apply()

    /** Durata del blocco, in secondi (default 1 minuto). */
    var kickLockSec: Int
        get() = sp.getInt("kick_lock_s", 60)
        set(v) = sp.edit().putInt("kick_lock_s", v).apply()

    /** Secondi in più per ogni blocco successivo nella stessa giornata. */
    var lockStepSec: Int
        get() = sp.getInt("lock_step_s", 60)
        set(v) = sp.edit().putInt("lock_step_s", v).apply()

    // ---------- Testi personalizzati ----------
    val DEFAULT_TEXTS = listOf(
        "Cosa stavi per fare prima di aprire questa app?",
        "Dieci minuti di scroll non ti ridanno dieci minuti.",
        "Alzati, bevi un bicchiere d'acqua, poi decidi.",
        "Sei tu a usare il telefono, o il contrario?",
        "La cosa difficile che stai evitando è ancora lì.",
    )

    fun customTexts(): List<String> {
        val raw = sp.getString("custom_texts", null) ?: return DEFAULT_TEXTS
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    fun setCustomTexts(l: List<String>) {
        val arr = JSONArray()
        l.forEach { arr.put(it) }
        sp.edit().putString("custom_texts", arr.toString()).apply()
    }

    /** Un testo a caso, diverso dall'ultimo mostrato quando possibile. */
    fun randomText(): String? {
        val l = customTexts()
        if (l.isEmpty()) return null
        val last = sp.getString("last_text", null)
        val pick = l.filter { it != last }.ifEmpty { l }.random()
        sp.edit().putString("last_text", pick).apply()
        return pick
    }

    var randomChecks: Boolean
        get() = sp.getBoolean("random_checks", true)
        set(v) = sp.edit().putBoolean("random_checks", v).apply()

    var adultFilter: Boolean
        get() = sp.getBoolean("adult_filter", true)
        set(v) = sp.edit().putBoolean("adult_filter", v).apply()

    var serpCover: Boolean
        get() = sp.getBoolean("serp_cover", true)
        set(v) = sp.edit().putBoolean("serp_cover", v).apply()

    /** Cancella dalla cronologia del browser la pagina bloccata (browser Chromium). */
    var cleanHistory: Boolean
        get() = sp.getBoolean("clean_history", true)
        set(v) = sp.edit().putBoolean("clean_history", v).apply()

    var cooldownHours: Int
        get() = sp.getInt("cooldown_h", 24)
        set(v) = sp.edit().putInt("cooldown_h", v).apply()

    /** Con Device Owner: vieta anche il debug USB (niente "adb uninstall"). */
    var blockAdb: Boolean
        get() = sp.getBoolean("block_adb", false)
        set(v) = sp.edit().putBoolean("block_adb", v).apply()

    var lastListUpdate: Long
        get() = sp.getLong("list_update", 0)
        set(v) = sp.edit().putLong("list_update", v).apply()

    // ---------- Regole app ----------
    fun rules(): Map<String, AppRule> {
        val arr = JSONArray(sp.getString("rules", "[]"))
        val out = LinkedHashMap<String, AppRule>()
        for (i in 0 until arr.length()) AppRule.fromJson(arr.getJSONObject(i)).let { out[it.pkg] = it }
        return out
    }

    private fun saveRules(m: Map<String, AppRule>) {
        val arr = JSONArray()
        m.values.forEach { arr.put(it.toJson()) }
        sp.edit().putString("rules", arr.toString()).apply()
    }

    fun putRule(r: AppRule) = saveRules(rules().toMutableMap().apply { put(r.pkg, r) })
    fun removeRule(pkg: String) = saveRules(rules().toMutableMap().apply { remove(pkg) })

    // ---------- Siti ----------
    fun userSites(): Set<String> = sp.getStringSet("sites", emptySet())!!.toSet()
    fun addSite(d: String) = sp.edit().putStringSet("sites", userSites() + d).apply()
    fun removeSite(d: String) = sp.edit().putStringSet("sites", userSites() - d).apply()

    // ---------- Contatori giornalieri ----------
    fun attemptsToday(target: String): Int = sp.getInt("att_${today()}_$target", 0)
    fun incAttempts(target: String): Int {
        val n = attemptsToday(target) + 1
        sp.edit().putInt("att_${today()}_$target", n).apply()
        return n
    }

    fun lossesToday(): Int = sp.getInt("loss_${today()}", 0)
    fun incLosses() = sp.edit().putInt("loss_${today()}", lossesToday() + 1).apply()

    fun bypassToday(): Int = sp.getInt("bypass_${today()}", 0)
    fun incBypass(): Int {
        val n = bypassToday() + 1
        sp.edit().putInt("bypass_${today()}", n).apply()
        return n
    }

    /** Tempo in primo piano misurato dal servizio (fallback se manca l'accesso all'utilizzo). */
    fun usedMsToday(pkg: String): Long = sp.getLong("used_${today()}_$pkg", 0)
    fun addUsedMs(pkg: String, ms: Long) =
        sp.edit().putLong("used_${today()}_$pkg", usedMsToday(pkg) + ms).apply()

    // ---------- Blocco ----------
    var lockUntil: Long
        get() = sp.getLong("lock_until", 0)
        set(v) = sp.edit().putLong("lock_until", v).apply()

    fun isLocked(now: Long = System.currentTimeMillis()) = lockUntil > now

    // ---------- Modifiche con cooldown ----------
    fun pending(): List<PendingChange> {
        val arr = JSONArray(sp.getString("pending", "[]"))
        return (0 until arr.length()).map { PendingChange.fromJson(arr.getJSONObject(it)) }
    }

    private fun savePending(l: List<PendingChange>) {
        val arr = JSONArray()
        l.forEach { arr.put(it.toJson()) }
        sp.edit().putString("pending", arr.toString()).apply()
    }

    fun addPending(type: String, payload: String, desc: String): PendingChange {
        val now = System.currentTimeMillis()
        val pc = PendingChange(now, type, payload, now + cooldownHours * 3_600_000L, desc)
        savePending(pending() + pc)
        return pc
    }

    fun cancelPending(id: Long) = savePending(pending().filterNot { it.id == id })
    fun takeDuePending(now: Long = System.currentTimeMillis()): List<PendingChange> {
        val all = pending()
        val due = all.filter { it.applyAt <= now }
        if (due.isNotEmpty()) savePending(all - due.toSet())
        return due
    }
}
