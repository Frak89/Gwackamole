package com.frak89.gwackamole.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Scatola nera: registra l'ultimo crash (su qualsiasi thread) in un file, così l'errore si può
 * leggere dentro l'app senza bisogno di un PC e di adb.
 */
object CrashLog {
    private var file: File? = null

    fun install(ctx: Context) {
        file = File(ctx.applicationContext.filesDir, "crash.log")
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { write("Thread ${t.name}", e) } catch (_: Throwable) { }
            prev?.uncaughtException(t, e)
        }
    }

    /** Registra un errore gestito (non fatale), per capire cosa non va senza far cadere l'app. */
    fun note(tag: String, e: Throwable) {
        try { write(tag, e) } catch (_: Throwable) { }
    }

    private fun write(tag: String, e: Throwable) {
        val f = file ?: return
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        f.writeText("${java.util.Date()}\n$tag\n$sw")
    }

    fun last(): String? = file?.takeIf { it.exists() }?.readText()?.take(4000)
    fun clear() { file?.delete() }
}
