package com.cargo.launcher.obd

import java.io.IOException

/**
 * Protocolo ELM327 sobre cualquier [ObdTransport].
 * Envía un comando terminado en '\r' y lee hasta el aviso '>' del adaptador.
 */
class Elm327(private val io: ObdTransport) {

    /** Adaptador o carro sin respuesta (ignición apagada, protocolo no encontrado). */
    class NoVehicleException(msg: String) : IOException(msg)

    private val buf = ByteArray(256)

    /** Envía [cmd] y devuelve la respuesta limpia (sin eco, espacios ni el '>'). */
    @Throws(IOException::class)
    fun command(cmd: String, timeoutMs: Int = 1500): String {
        drain()
        io.write((cmd + "\r").toByteArray(Charsets.US_ASCII))
        val sb = StringBuilder()
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = io.read(buf, 50)
            if (n > 0) {
                sb.append(String(buf, 0, n, Charsets.US_ASCII))
                if (sb.indexOf(">") >= 0) break
            }
            if (Thread.currentThread().isInterrupted) throw IOException("Interrumpido")
        }
        if (sb.indexOf(">") < 0) throw IOException("Sin respuesta a $cmd")
        return sb.toString()
            .replace(">", "")
            .replace("\u0000", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.equals(cmd, ignoreCase = true) && !it.startsWith("SEARCHING") }
            .joinToString("\n")
    }

    /** Descarta bytes viejos que hayan quedado en el canal. */
    private fun drain() {
        var guard = 0
        while (guard++ < 10 && io.read(buf, 1) > 0) Unit
    }

    /**
     * Inicialización estándar: reinicio, sin eco, sin saltos de línea, sin espacios, sin encabezados,
     * protocolo automático. Devuelve la versión del adaptador (ej. "ELM327 v1.5").
     */
    @Throws(IOException::class)
    fun init(): String {
        val version = command("ATZ", 2500)       // reinicio (tarda ~1 s)
        command("ATE0")                            // sin eco
        command("ATL0")                            // sin saltos de línea extra
        command("ATS0")                            // sin espacios: "410D3C"
        command("ATH0")                            // sin encabezados CAN
        command("ATAT1")                           // tiempos de espera adaptativos
        command("ATSP0")                           // protocolo automático
        // La primera consulta hace que el ELM busque el protocolo del carro (puede tardar varios segundos)
        val probe = command("0100", 10000)
        checkVehicle(probe)
        return version.lines().firstOrNull { it.contains("ELM", true) || it.contains("OBD", true) } ?: version
    }

    private fun checkVehicle(resp: String) {
        val r = resp.uppercase()
        if (r.contains("UNABLE TO CONNECT") || r.contains("CAN ERROR") || (r.contains("BUS INIT") && r.contains("ERROR"))) {
            throw NoVehicleException(resp)
        }
    }

    /** Lee un PID de modo 01 y devuelve sus bytes de datos (A, B, ...), o null si no hay dato. */
    @Throws(IOException::class)
    fun pid(pid: String, timeoutMs: Int = 1000): IntArray? {
        val resp = command("01$pid", timeoutMs)
        checkVehicle(resp)
        val key = "41" + pid.uppercase()
        for (line in resp.uppercase().lines()) {
            val clean = line.replace(" ", "")
            val at = clean.indexOf(key)
            if (at < 0) continue
            val hex = clean.substring(at + key.length)
            val bytes = hex.chunked(2).takeWhile { it.length == 2 }.mapNotNull { it.toIntOrNull(16) }
            if (bytes.isNotEmpty()) return bytes.toIntArray()
        }
        return null // "NO DATA", "?" o PID no soportado
    }

    /** Voltaje en el puerto OBD (batería de 12 V) con ATRV, ej. "12.6V". */
    @Throws(IOException::class)
    fun batteryVolts(): Float? =
        Regex("""(\d{1,2}\.\d)""").find(command("ATRV"))?.groupValues?.get(1)?.toFloatOrNull()

    // ---------- PIDs ----------
    fun speedKmh(): Int? = pid("0D")?.let { it[0] }                                   // A km/h
    fun rpm(): Int? = pid("0C")?.takeIf { it.size >= 2 }?.let { (it[0] * 256 + it[1]) / 4 } // (256A+B)/4
    fun coolantC(): Int? = pid("05")?.let { it[0] - 40 }                              // A-40 °C
}
