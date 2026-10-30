package com.frak89.gwackamole

import android.content.Context
import java.security.MessageDigest
import java.util.Locale

/** All protected data remains in this device's private SharedPreferences. */
class Rules(context: Context) {
    private val pref = context.getSharedPreferences("gwackamole_rules", Context.MODE_PRIVATE)

    fun blacklist(packageName: String) = pref.getBoolean("black:" + packageName, false)
    fun setBlacklist(packageName: String, value: Boolean) = pref.edit().putBoolean("black:" + packageName, value).apply()
    fun minutes(packageName: String) = pref.getInt("mins:" + packageName, 15).coerceIn(1, 240)
    fun setMinutes(packageName: String, value: Int) = pref.edit().putInt("mins:" + packageName, value.coerceIn(1, 240)).apply()
    fun usedMs(packageName: String) = pref.getLong("used:" + today() + ":" + packageName, 0L)
    fun addUsedMs(packageName: String, elapsed: Long) {
        if (elapsed <= 0) return
        val k = "used:" + today() + ":" + packageName
        pref.edit().putLong(k, (pref.getLong(k, 0L) + elapsed.coerceAtMost(60000L))).apply()
    }
    fun extraMs(packageName: String) = pref.getLong("extra:" + today() + ":" + packageName, 0L)
    fun giveAppExtension(packageName: String, minutes: Int) {
        val k = "extra:" + today() + ":" + packageName
        pref.edit().putLong(k, extraMs(packageName) + minutes.coerceIn(1,15) * 60000L).apply()
    }
    fun hits(id: String) = pref.getInt("hits:" + id, 0)
    fun incrementAttempts(id: String): Int {
        val count = (hits(id) + 1).coerceAtMost(10)
        pref.edit().putInt("hits:" + id, count).apply()
        return count
    }
    fun isAllowed(id: String) = System.currentTimeMillis() < pref.getLong("allow:" + id, 0L)
    fun allow(id: String, durationMs: Long = 300000L) = pref.edit()
        .putLong("allow:" + id, System.currentTimeMillis() + durationMs).apply()
    fun inCooldown() = System.currentTimeMillis() < cooldownEnd()
    fun cooldownEnd() = pref.getLong("cooldown_end", 0L)
    fun startCooldown() = pref.edit().putLong(
        "cooldown_end", System.currentTimeMillis() + cooldownMinutes() * 60000L
    ).apply()
    fun cooldownMinutes() = pref.getInt("cooldown", 15).coerceIn(1, 180)
    fun setCooldownMinutes(v:Int) = pref.edit().putInt("cooldown",v.coerceIn(1,180)).apply()
    fun randomMinutes() = pref.getInt("random", 0).coerceIn(0,180)
    fun setRandomMinutes(v:Int) = pref.edit().putInt("random",v.coerceIn(0,180)).apply()
    fun onBrowsing() = pref.getBoolean("filter_on", true)
    fun setBrowsing(v: Boolean) = pref.edit().putBoolean("filter_on",v).apply()
    fun obscureDelaySeconds() = pref.getInt("delay", 0).coerceIn(0,5)
    fun setObscureDelaySeconds(v:Int) = pref.edit().putInt("delay",if(v==5) 5 else 0).apply()
    fun markLastChallenge() = pref.edit().putLong("last_challenge",System.currentTimeMillis()).apply()
    fun lastChallenge() = pref.getLong("last_challenge",System.currentTimeMillis())
    fun visit(packageName:String) {
        val k="visits:" + today() + ":" + packageName
        pref.edit().putInt(k,pref.getInt(k,0)+1).apply()
    }
    fun visits(packageName:String) = pref.getInt("visits:" + today() + ":" + packageName,0)

    private fun today():String = java.text.SimpleDateFormat("yyyyMMdd",Locale.US).format(java.util.Date())

    companion object {
        val browsers = setOf(
            "com.android.chrome", "org.mozilla.firefox", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
            "com.sec.android.app.sbrowser", "com.duckduckgo.mobile.android",
            "org.mozilla.focus", "com.vivaldi.browser"
        )
        // Local-only signals; never send page text or search history to a server.
        val adultTerms = Regex(
            """(?i)(?<![a-z])(?:porn(?:o|ography|ographic)?|xxx|nsfw|sex\s+videos?|big\s+tits|big\s+ass|hardcore|hentai)(?![a-z])"""
        )
        fun fingerprint(value:String):String {
            val hash=MessageDigest.getInstance("SHA-256").digest(value.lowercase(Locale.ROOT).trim().toByteArray())
            return hash.take(12).joinToString("") { "%02x".format(it) }
        }
    }
}
