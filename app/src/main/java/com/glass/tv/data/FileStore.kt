package com.glass.tv.data

import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Escritura de los ficheros pequeños de la app (favoritos, progreso, grabaciones…).
 *
 * - Fuera del hilo principal: el texto se prepara al momento y se escribe en un único hilo, en orden.
 * - Atómica: se escribe a un temporal y se renombra. Si la app se cierra a mitad, queda el fichero anterior
 *   entero en vez de uno cortado que al leerlo se descartaría (y con él todos los favoritos).
 * - Si llegan varias escrituras del mismo fichero seguidas, solo se escribe la última.
 */
object FileStore {
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "glass-files").apply { isDaemon = true } }
    private val pending = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun writeAsync(file: File, text: String) {
        val key = file.absolutePath
        // Si ya había una escritura en cola para este fichero, se sustituye su contenido y no se encola otra
        if (pending.put(key, text) != null) return
        io.execute {
            val latest = pending.remove(key) ?: return@execute
            writeNow(file, latest)
        }
    }

    /** Escribe ya, en el hilo que llama. Devuelve false si no se pudo. */
    fun writeNow(file: File, text: String): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.outputStream().use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) } else true
    } catch (_: Exception) {
        false
    }

    /** Espera a que se escriba todo lo pendiente (antes de reiniciar la app, por ejemplo). */
    fun flush(timeoutMs: Long = 3000) {
        try { io.submit {}.get(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Exception) { }
    }

    /** Lee el fichero; si quedó un temporal de una escritura interrumpida, se ignora. */
    fun read(file: File): String? = try { if (file.exists()) file.readText() else null } catch (_: Exception) { null }
}
