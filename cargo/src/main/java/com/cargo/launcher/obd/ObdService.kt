package com.cargo.launcher.obd

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.cargo.launcher.CarGoPrefs
import com.cargo.launcher.MainActivity
import com.cargo.launcher.R
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Estado de la telemetría que observa la pantalla. */
data class ObdState(
    val status: Status = Status.IDLE,
    val adapter: String? = null,
    val message: String? = null,
    val speedKmh: Int? = null,
    val rpm: Int? = null,
    val coolantC: Int? = null,
    val batteryV: Float? = null,
    val updatedAt: Long = 0L
) {
    enum class Status { IDLE, SEARCHING, CONNECTING, CONNECTED, NO_VEHICLE, NO_ADAPTER, NO_PERMISSION }
}

/** Punto único de lectura para la UI (no hace falta "bind" al servicio). */
object ObdRepository {
    private val _state = MutableStateFlow(ObdState())
    val state: StateFlow<ObdState> = _state.asStateFlow()
    internal fun update(f: (ObdState) -> ObdState) { _state.value = f(_state.value) }
}

/**
 * Servicio en primer plano que se conecta al adaptador ELM327 y lee la telemetría en un ciclo.
 *  - Elige el canal: USB si hay un adaptador USB conectado; si no, Bluetooth (el guardado o uno emparejado).
 *  - Reconexión automática con espera creciente (2 s → 30 s) si se pierde el adaptador o el carro está apagado.
 *  - Velocidad y RPM en cada vuelta (~5 por segundo); temperatura cada 5 vueltas; voltaje cada 10.
 */
class ObdService : Service() {

    companion object {
        private const val TAG = "CarGoObd"
        private const val CHANNEL = "obd"
        private const val NOTIF_ID = 42
        private const val ACTION_USB_PERMISSION = "com.cargo.launcher.USB_PERMISSION"
        private val BAUDS = intArrayOf(38400, 115200, 9600, 500000)
        private val NAME_HINT = Regex("OBD|ELM|V-?LINK|VGATE|ICAR|KONNWEI|VEEPEAK|CARISTA", RegexOption.IGNORE_CASE)

        fun start(c: Context) {
            val i = Intent(c, ObdService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo iniciar el servicio OBD", e)
            }
        }

        fun stop(c: Context) = c.stopService(Intent(c, ObdService::class.java))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    @Volatile private var usbPermission: CompletableDeferred<Boolean>? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                ACTION_USB_PERMISSION -> usbPermission?.complete(i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
                UsbManager.ACTION_USB_DEVICE_DETACHED -> restartLoop() // se desconectó: reintenta de inmediato
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag") // en API < 33 no existe la bandera; la acción es propia del paquete
    override fun onCreate() {
        super.onCreate()
        val f = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbReceiver, f, RECEIVER_NOT_EXPORTED)
        else registerReceiver(usbReceiver, f)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        goForeground(getString(R.string.obd_searching))
        if (loop?.isActive != true) restartLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(usbReceiver) } catch (_: IllegalArgumentException) { }
        scope.cancel()
        ObdRepository.update { ObdState() }
        super.onDestroy()
    }

    private fun restartLoop() {
        loop?.cancel()
        loop = scope.launch { connectionLoop() }
    }

    // ---------------------------------------------------------------- ciclo principal

    private suspend fun connectionLoop() {
        var backoff = 2000L
        while (scope.isActive) {
            ObdRepository.update { it.copy(status = ObdState.Status.SEARCHING, message = null) }
            val candidates = findTransports()
            if (candidates.isEmpty()) {
                // Status ya fue puesto por findTransports (sin adaptador o sin permiso)
                delay(backoff); backoff = (backoff * 2).coerceAtMost(30_000); continue
            }
            var sessionOk = false
            for (t in candidates) {
                ObdRepository.update { it.copy(status = ObdState.Status.CONNECTING, adapter = t.label) }
                goForeground(getString(R.string.obd_connecting, t.label))
                try {
                    t.open()
                    val elm = Elm327(t)
                    val version = elm.init()
                    Log.i(TAG, "Conectado: ${t.label} · $version")
                    if (t is UsbTransport) CarGoPrefs.usbBaud = t.baud
                    CarGoPrefs.lastObdLabel = t.label
                    ObdRepository.update { it.copy(status = ObdState.Status.CONNECTED, adapter = t.label, message = version) }
                    goForeground(getString(R.string.obd_connected, t.label))
                    sessionOk = true
                    backoff = 2000L
                    poll(elm)
                } catch (e: Elm327.NoVehicleException) {
                    // El adaptador responde pero el carro no (ignición apagada): espera más
                    ObdRepository.update { it.copy(status = ObdState.Status.NO_VEHICLE, message = e.message) }
                    goForeground(getString(R.string.obd_no_vehicle))
                    sessionOk = true
                    backoff = 15_000L
                } catch (e: kotlinx.coroutines.CancellationException) {
                    t.close(); throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Falló ${t.label}: ${e.message}")
                    ObdRepository.update { it.copy(status = ObdState.Status.SEARCHING, message = e.message) }
                } finally {
                    t.close()
                }
                if (sessionOk) break // ya hubo sesión con este adaptador: no probar los demás
            }
            ObdRepository.update { it.copy(speedKmh = null, rpm = null) }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(30_000)
        }
    }

    /** Lee PIDs sin parar hasta que falle la conexión (entonces lanza la excepción y se reconecta). */
    private suspend fun poll(elm: Elm327) {
        var cycle = 0
        var failures = 0
        while (true) {
            kotlin.coroutines.coroutineContext.ensureActive()
            try {
                val speed = elm.speedKmh()
                val rpm = elm.rpm()
                val coolant = if (cycle % 5 == 0) elm.coolantC() else null
                val volts = if (cycle % 10 == 0) elm.batteryVolts() else null
                failures = 0
                ObdRepository.update {
                    it.copy(
                        status = ObdState.Status.CONNECTED,
                        speedKmh = speed ?: it.speedKmh,
                        rpm = rpm ?: it.rpm,
                        coolantC = coolant ?: it.coolantC,
                        batteryV = volts ?: it.batteryV,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (e: Elm327.NoVehicleException) {
                throw e
            } catch (e: IOException) {
                if (++failures >= 5) throw e // 5 errores seguidos: se perdió el adaptador
            }
            cycle++
            delay(120)
        }
    }

    // ---------------------------------------------------------------- elegir adaptador

    private suspend fun findTransports(): List<ObdTransport> {
        val mode = CarGoPrefs.obdMode // AUTO / USB / BLUETOOTH
        val out = ArrayList<ObdTransport>()
        if (mode != CarGoPrefs.ObdMode.BLUETOOTH) out += usbTransports()
        if (mode != CarGoPrefs.ObdMode.USB && out.isEmpty()) bluetoothTransport()?.let { out += it }
        if (out.isEmpty() && ObdRepository.state.value.status == ObdState.Status.SEARCHING) {
            ObdRepository.update { it.copy(status = ObdState.Status.NO_ADAPTER) }
            goForeground(getString(R.string.obd_no_adapter))
        }
        return out
    }

    /** Un transporte por cada velocidad posible: el ELM USB más común va a 38400, OBDLink a 115200. */
    private suspend fun usbTransports(): List<ObdTransport> {
        val usb = getSystemService(USB_SERVICE) as UsbManager
        val driver: UsbSerialDriver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull() ?: return emptyList()
        if (!usb.hasPermission(driver.device) && !requestUsbPermission(usb, driver.device)) {
            ObdRepository.update { it.copy(status = ObdState.Status.NO_PERMISSION, message = "USB") }
            return emptyList()
        }
        val saved = CarGoPrefs.usbBaud
        val order = (listOfNotNull(saved.takeIf { it > 0 }) + BAUDS.toList()).distinct()
        return order.map { UsbTransport(usb, driver, it) }
    }

    private suspend fun requestUsbPermission(usb: UsbManager, device: UsbDevice): Boolean {
        val d = CompletableDeferred<Boolean>()
        usbPermission = d
        val flags = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION).setPackage(packageName), flags)
        usb.requestPermission(device, pi)
        return withTimeoutOrNull(60_000) { d.await() } ?: false
    }

    @SuppressLint("MissingPermission")
    private fun bluetoothTransport(): ObdTransport? {
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            ObdRepository.update { it.copy(status = ObdState.Status.NO_PERMISSION, message = "Bluetooth") }
            return null
        }
        val adapter = (getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter ?: return null
        if (!adapter.isEnabled) return null
        try { adapter.cancelDiscovery() } catch (_: SecurityException) { } // descubrir y conectar a la vez falla
        val bonded: Set<BluetoothDevice> = try { adapter.bondedDevices } catch (e: SecurityException) { emptySet() }
        val dev = CarGoPrefs.obdMac?.let { mac -> bonded.firstOrNull { it.address.equals(mac, true) } }
            ?: bonded.firstOrNull { d -> (try { d.name } catch (e: SecurityException) { null })?.let { NAME_HINT.containsMatchIn(it) } == true }
            ?: return null
        return BluetoothTransport(dev)
    }

    // ---------------------------------------------------------------- notificación

    private fun goForeground(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.obd_channel), NotificationManager.IMPORTANCE_MIN))
        }
        val immutable = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), immutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
        val n: Notification = builder
            .setSmallIcon(R.drawable.ic_obd)
            .setContentTitle(getString(R.string.obd_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(NOTIF_ID, n)
    }
}
