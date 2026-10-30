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
    /** Minuti al giorno. Si allunga subito solo vincendo il minigioco; allentare la regola richiede il cooldown. */
    val limitMin: Int,
    /** Secondi di tolleranza prima che compaia il minigioco quando il tempo è finito. */
    val graceSec: Int,
    /** Talpe a sorpresa durante l'uso. */
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

    var bonusMinutes: Int
        get() = sp.getInt("bonus_minutes", 10)
        set(v) = sp.edit().putInt("bonus_minutes", v).apply()

    var lockBaseMinutes: Int
        get() = sp.getInt("lock_base", 15)
        set(v) = sp.edit().putInt("lock_base", v).apply()

    var lockStepMinutes: Int
        get() = sp.getInt("lock_step", 5)
        set(v) = sp.edit().putInt("lock_step", v).apply()

    /** Secondi prima del blocco per i siti per adulti (0 = subito, max 5). */
    var siteGraceSec: Int
        get() = sp.getInt("site_grace", 0)
        set(v) = sp.edit().putInt("site_grace", v.coerceIn(0, 5)).apply()

    /** Minuti di schermo acceso continuo prima di una talpa (0 = spento). */
    var screenLimitMin: Int
        get() = sp.getInt("screen_limit", 45)
        set(v) = sp.edit().putInt("screen_limit", v).apply()

    var randomChecks: Boolean
        get() = sp.getBoolean("random_checks", true)
        set(v) = sp.edit().putBoolean("random_checks", v).apply()

    var adultFilter: Boolean
        get() = sp.getBoolean("adult_filter", true)
        set(v) = sp.edit().putBoolean("adult_filter", v).apply()

    var serpCover: Boolean
        get() = sp.getBoolean("serp_cover", true)
        set(v) = sp.edit().putBoolean("serp_cover", v).apply()

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
    fun bonusToday(target: String): Int = sp.getInt("bonus_${today()}_$target", 0)
    fun addBonus(target: String, min: Int) =
        sp.edit().putInt("bonus_${today()}_$target", bonusToday(target) + min).apply()

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

    /** Permessi temporanei per siti dell'utente vinti al minigioco. */
    fun siteAllowedUntil(host: String): Long = sp.getLong("allow_$host", 0)
    fun allowSite(host: String, until: Long) = sp.edit().putLong("allow_$host", until).apply()

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
