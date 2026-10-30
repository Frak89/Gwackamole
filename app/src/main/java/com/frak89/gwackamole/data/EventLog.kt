package com.frak89.gwackamole.data

import android.content.Context
import java.io.File
import java.util.concurrent.Executors

/**
 * Diario degli eventi per l'analisi del comportamento (resta sul telefono).
 * Riga: timestamp|tipo|target|valore
 * Tipi: OPEN, SESSION (ms), GAME_WIN, GAME_LOSS, BYPASS, SITE_BLOCK, LEARNED, RANDOM, SCREEN
 */
object EventLog {
    data class Event(val ts: Long, val type: String, val target: String, val value: Long)

    private var file: File? = null
    private val io = Executors.newSingleThreadExecutor()
    private const val MAX_LINES = 8000

    fun init(ctx: Context) {
        if (file == null) file = File(ctx.applicationContext.filesDir, "events.log")
    }

    fun log(type: String, target: String, value: Long = 0) {
        val f = file ?: return
        val line = "${System.currentTimeMillis()}|$type|${target.replace('|', '_')}|$value\n"
        io.execute {
            f.appendText(line)
            if (f.length() > 600_000) {
                val keep = f.readLines().takeLast(MAX_LINES / 2)
                f.writeText(keep.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun read(sinceMs: Long): List<Event> {
        val f = file ?: return emptyList()
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { l ->
            val p = l.split('|')
            if (p.size < 4) null else {
                val ts = p[0].toLongOrNull() ?: return@mapNotNull null
                if (ts < sinceMs) null else Event(ts, p[1], p[2], p[3].toLongOrNull() ?: 0)
            }
        }
    }
}
