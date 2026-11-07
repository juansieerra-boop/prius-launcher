package com.juandiego.priuslauncher

import android.app.Notification
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Con el acceso a notificaciones, Android deja que el launcher:
 *  - vea qué música suena (título, artista, portada), y
 *  - lea la notificación de navegación de Google Maps (siguiente vuelta, distancia, llegada).
 */
class MediaListenerService : NotificationListenerService() {

    private companion object {
        val NAV_APPS = setOf(
            "com.google.android.apps.maps", "com.waze", "app.organicmaps", "net.osmand", "net.osmand.plus", "com.here.app.maps"
        )
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
            // algunas pantallas no lo permiten al conectar
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NavState.update(null)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName in NAV_APPS && NavState.currentKey == sbn.key) NavState.update(null)
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName !in NAV_APPS) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_ONGOING_EVENT == 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val icon: Drawable? = try {
            n.getLargeIcon()?.loadDrawable(this)
        } catch (e: Exception) {
            null
        }
        NavState.update(NavState.Info(sbn.key, title, text, sub, icon))
    }
}

/** La navegación activa de Google Maps (o null si no estás navegando). */
object NavState {
    class Info(val key: String, val title: String, val text: String, val sub: String, val icon: Drawable?)

    @Volatile
    var current: Info? = null
        private set
    val currentKey: String? get() = current?.key

    private val listeners = CopyOnWriteArraySet<() -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun update(info: Info?) {
        current = info
        main.post { listeners.forEach { it() } }
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }
}
