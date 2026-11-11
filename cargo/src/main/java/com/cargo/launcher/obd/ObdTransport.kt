package com.cargo.launcher.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Canal de bytes hacia el adaptador ELM327. Hay dos: Bluetooth (SPP/RFCOMM) y USB serie.
 * La lógica del protocolo (Elm327) es la misma para los dos.
 * Todas las llamadas son bloqueantes: se usan solo desde el hilo de IO del servicio.
 */
interface ObdTransport {
    val label: String

    @Throws(IOException::class)
    fun open()

    @Throws(IOException::class)
    fun write(data: ByteArray)

    /** Lee lo que haya llegado (o espera hasta [timeoutMs]). Devuelve 0 si no llegó nada. */
    @Throws(IOException::class)
    fun read(buf: ByteArray, timeoutMs: Int): Int

    fun close()
}

/** Bluetooth clásico SPP (ELM327, Vgate iCar Pro 3.0, OBDLink LX/MX+). */
class BluetoothTransport(private val device: BluetoothDevice) : ObdTransport {

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private var socket: BluetoothSocket? = null
    private var input: InputStream? = null
    private var output: OutputStream? = null

    @SuppressLint("MissingPermission") // el servicio revisa BLUETOOTH_CONNECT antes de crear este transporte
    override val label: String = "Bluetooth · " + (try { device.name } catch (e: SecurityException) { null } ?: device.address)

    @SuppressLint("MissingPermission")
    override fun open() {
        // 1) SPP seguro, 2) SPP inseguro (muchos clones ELM327), 3) canal RFCOMM 1 por reflexión (último recurso)
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
            {
                val m = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                m.invoke(device, 1) as BluetoothSocket
            }
        )
        var last: Exception? = null
        for (make in attempts) {
            val s = try { make() } catch (e: Exception) { last = e; continue }
            try {
                s.connect()
                socket = s
                input = s.inputStream
                output = s.outputStream
                return
            } catch (e: Exception) {
                last = e
                try { s.close() } catch (_: IOException) { }
            }
        }
        throw IOException("No se pudo conectar por Bluetooth", last)
    }

    override fun write(data: ByteArray) {
        val o = output ?: throw IOException("Bluetooth cerrado")
        o.write(data)
        o.flush()
    }

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        val i = input ?: throw IOException("Bluetooth cerrado")
        // InputStream de Bluetooth bloquea sin límite: se espera con available() para respetar el timeout
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val n = i.available()
            if (n > 0) return i.read(buf, 0, minOf(n, buf.size))
            Thread.sleep(5)
        }
        return 0
    }

    override fun close() {
        try { input?.close() } catch (_: IOException) { }
        try { output?.close() } catch (_: IOException) { }
        try { socket?.close() } catch (_: IOException) { }
        socket = null; input = null; output = null
    }
}

/** USB serie (OBDLink EX, vLinker FS USB, ELM327 USB con chip FTDI / CH340 / CP210x / PL2303). */
class UsbTransport(
    private val usb: UsbManager,
    private val driver: UsbSerialDriver,
    val baud: Int
) : ObdTransport {

    private var port: UsbSerialPort? = null

    override val label: String = "USB · " + (driver.device.productName ?: driver.javaClass.simpleName.removeSuffix("SerialDriver")) + " @$baud"

    override fun open() {
        val conn = usb.openDevice(driver.device) ?: throw IOException("Sin permiso para el adaptador USB")
        val p = driver.ports.firstOrNull() ?: throw IOException("El adaptador USB no tiene puerto serie")
        try {
            p.open(conn)
            p.setParameters(baud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            try { p.dtr = true; p.rts = true } catch (_: Exception) { } // algunos chips no lo soportan
        } catch (e: Exception) {
            try { p.close() } catch (_: IOException) { }
            throw IOException("No se pudo abrir el puerto USB", e)
        }
        port = p
    }

    override fun write(data: ByteArray) {
        (port ?: throw IOException("USB cerrado")).write(data, 1000)
    }

    override fun read(buf: ByteArray, timeoutMs: Int): Int {
        return (port ?: throw IOException("USB cerrado")).read(buf, timeoutMs.coerceAtLeast(1))
    }

    override fun close() {
        try { port?.close() } catch (_: IOException) { }
        port = null
    }
}
