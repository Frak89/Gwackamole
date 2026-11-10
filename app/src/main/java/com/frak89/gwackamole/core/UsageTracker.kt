package com.frak89.gwackamole.core

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.frak89.gwackamole.data.Store
import java.time.LocalDate
import java.time.ZoneId

/** Tempo in primo piano di oggi per ogni app, da UsageStatsManager (o dal contatore interno). */
object UsageTracker {

    fun hasPermission(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun startOfDay(): Long =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Millisecondi in primo piano oggi. */
    fun usedTodayMs(ctx: Context, pkg: String): Long {
        if (!hasPermission(ctx)) return Store.usedMsToday(pkg)
        return foregroundTimes(ctx, startOfDay(), System.currentTimeMillis())[pkg] ?: 0L
    }

    /** Mappa pacchetto → ms in primo piano nell'intervallo, ricostruita dagli eventi resume/pause. */
    fun foregroundTimes(ctx: Context, from: Long, to: Long): Map<String, Long> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to)
        val started = HashMap<String, Long>()
        val total = HashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                1 /* ACTIVITY_RESUMED / MOVE_TO_FOREGROUND */ -> started[e.packageName] = e.timeStamp
                2 /* ACTIVITY_PAUSED / MOVE_TO_BACKGROUND */ -> {
                    val s = started.remove(e.packageName) ?: continue
                    total[e.packageName] = (total[e.packageName] ?: 0L) + (e.timeStamp - s)
                }
            }
        }
        // app ancora aperta
        started.forEach { (p, s) -> total[p] = (total[p] ?: 0L) + (to - s) }
        return total
    }

    /** Minuti rimasti oggi per una regola (limite − usati). */
    fun remainingMs(ctx: Context, pkg: String): Long {
        val rule = Store.rules()[pkg] ?: return Long.MAX_VALUE
        val allowed = rule.limitMin * 60_000L
        return allowed - usedTodayMs(ctx, pkg)
    }

    /** Tempo in primo piano di oggi per ogni app (escluse Nero Block, launcher e UI di sistema). */
    fun todayByApp(ctx: Context): Map<String, Long> {
        if (!hasPermission(ctx)) return emptyMap()
        val skip = setOf("android", ctx.packageName)
        return foregroundTimes(ctx, startOfDay(), System.currentTimeMillis())
            .filterKeys { it !in skip && !it.contains("systemui") && !it.contains("launcher") && !it.contains("nexuslauncher") }
            .filterValues { it >= 60_000 } // almeno 1 minuto
    }

    fun totalTodayMs(ctx: Context): Long = todayByApp(ctx).values.sum()

    /** Le [n] app più usate oggi, dalla più usata. */
    fun topTodayApps(ctx: Context, n: Int = 5): List<Pair<String, Long>> =
        todayByApp(ctx).entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

    /** Media giornaliera di schermo attivo negli ultimi [days] giorni. */
    fun avgDailyMs(ctx: Context, days: Int = 7): Long {
        if (!hasPermission(ctx)) return 0
        val skip = setOf("android", ctx.packageName)
        val from = startOfDay() - (days - 1) * 86_400_000L
        val total = foregroundTimes(ctx, from, System.currentTimeMillis())
            .filterKeys { it !in skip && !it.contains("systemui") && !it.contains("launcher") }
            .values.sum()
        return total / days
    }
}
