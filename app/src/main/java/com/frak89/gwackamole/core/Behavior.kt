package com.frak89.gwackamole.core

import com.frak89.gwackamole.data.EventLog
import com.frak89.gwackamole.data.Store
import java.time.Instant
import java.time.ZoneId

/**
 * Analisi del comportamento sulle app/siti in blacklist, calcolata dal diario eventi.
 * Serve sia per la schermata Statistiche sia per adattare il gioco (seed iniziale, talpe a sorpresa).
 */
object Behavior {

    data class Profile(
        val target: String,
        val opensToday: Int,
        val opens7d: Int,
        val minutes7d: Long,
        val avgSessionSec: Long,
        val peakHour: Int?,
        val wins: Int,
        val losses: Int,
        val bypass: Int,
        val blocks: Int,
    ) {
        /** 0..1: quanto questo target "cattura" l'utente. */
        val risk: Double
            get() {
                val opensScore = (opensToday / 25.0).coerceAtMost(1.0)
                val shortSessions = if (avgSessionSec in 1..90) 0.25 else 0.0 // tante aperture brevi = compulsivo
                val lossScore = if (wins + losses == 0) 0.0 else losses.toDouble() / (wins + losses) * 0.3
                return (opensScore * 0.5 + shortSessions + lossScore + (bypass.coerceAtMost(3) * 0.1)).coerceIn(0.0, 1.0)
            }

        fun advice(): String = buildString {
            if (peakHour != null) append("Picco di aperture verso le ${peakHour}:00. ")
            if (avgSessionSec in 1..90 && opens7d > 20) append("Molte aperture brevi: probabilmente per abitudine, non per bisogno. ")
            if (losses > wins && losses > 2) append("Le talpe vincono spesso: valuta un limite più basso, così le sfide arrivano prima. ")
            if (bypass > 0) append("$bypass tentativi di aggirare le regole questa settimana. ")
            if (isEmpty()) append("Uso nella norma.")
        }.trim()
    }

    private var cache: Pair<Long, Map<String, Profile>>? = null

    fun profiles(force: Boolean = false): Map<String, Profile> {
        val now = System.currentTimeMillis()
        cache?.let { if (!force && now - it.first < 60_000) return it.second }
        val since = now - 7 * 86_400_000L
        val events = EventLog.read(since)
        val todayStr = Store.today()
        val zone = ZoneId.systemDefault()
        val byTarget = events.groupBy { it.target }
        val out = HashMap<String, Profile>()
        for ((t, ev) in byTarget) {
            val opens = ev.filter { it.type == "OPEN" }
            val sessions = ev.filter { it.type == "SESSION" }
            val hours = opens.groupingBy { Instant.ofEpochMilli(it.ts).atZone(zone).hour }.eachCount()
            out[t] = Profile(
                target = t,
                opensToday = opens.count { Instant.ofEpochMilli(it.ts).atZone(zone).toLocalDate().toString() == todayStr },
                opens7d = opens.size,
                minutes7d = sessions.sumOf { it.value } / 60_000,
                avgSessionSec = if (sessions.isEmpty()) 0 else sessions.sumOf { it.value } / sessions.size / 1000,
                peakHour = hours.maxByOrNull { it.value }?.key,
                wins = ev.count { it.type == "GAME_WIN" },
                losses = ev.count { it.type == "GAME_LOSS" },
                bypass = ev.count { it.type == "BYPASS" },
                blocks = ev.count { it.type == "SITE_BLOCK" },
            )
        }
        cache = now to out
        return out
    }

    fun risk(target: String): Double = profiles()[target]?.risk ?: 0.0
}
