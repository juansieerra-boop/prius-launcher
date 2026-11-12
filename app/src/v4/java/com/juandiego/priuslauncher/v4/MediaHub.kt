package com.juandiego.priuslauncher.v4

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import com.juandiego.priuslauncher.DockSlots
import com.juandiego.priuslauncher.MediaListenerService

/**
 * Música para la barra de arriba (misma lógica de la versión aprobada: Spotify primero,
 * si no la app que esté sonando, y si no hay acceso, teclas multimedia).
 */
class MediaHub(private val act: Activity, private val onChange: () -> Unit) {

    private companion object {
        const val SPOTIFY = "com.spotify.music"
    }

    class Now(
        val hasAccess: Boolean, val title: String?, val artist: String?, val art: Bitmap?,
        val playing: Boolean, val positionMs: Long, val durationMs: Long
    )

    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
        override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()
        override fun onSessionDestroyed() {
            bind(null)
            refresh()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private fun component() = ComponentName(act, MediaListenerService::class.java)

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners")
        return enabled?.contains("${act.packageName}/") == true
    }

    fun start() {
        if (!hasAccess()) {
            bind(null)
            return
        }
        val msm = act.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component())
            sessionManager = msm
            refresh()
        } catch (e: SecurityException) {
            sessionManager = null
            bind(null)
        }
    }

    fun stop() {
        try {
            sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // ya no estaba
        }
        sessionManager = null
        bind(null)
    }

    private fun refresh() {
        val msm = sessionManager ?: return
        try {
            pick(msm.getActiveSessions(component()))
        } catch (e: SecurityException) {
            bind(null)
        }
    }

    private fun pick(list: List<MediaController>?) {
        val spotify = list?.firstOrNull { it.packageName == SPOTIFY }
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        bind(playing ?: spotify ?: list?.firstOrNull())
    }

    private fun bind(c: MediaController?) {
        val old = controller
        if (old != null && c != null && old.sessionToken == c.sessionToken) {
            onChange()
            return
        }
        try {
            old?.unregisterCallback(callback)
        } catch (e: Exception) {
            // sesión ya cerrada
        }
        controller = c
        c?.registerCallback(callback)
        onChange()
    }

    fun now(): Now {
        val access = hasAccess()
        val c = controller
        val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (c == null) return Now(access, null, null, null, am.isMusicActive, 0L, 0L)
        val md = c.metadata
        val st = c.playbackState
        val duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        var pos = st?.position ?: 0L
        if (st != null && st.state == PlaybackState.STATE_PLAYING && st.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - st.lastPositionUpdateTime) * st.playbackSpeed).toLong()
        }
        return Now(
            access,
            md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: appLabel(c.packageName),
            md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
            md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            st?.state == PlaybackState.STATE_PLAYING,
            if (duration > 0) pos.coerceIn(0L, duration) else 0L,
            duration
        )
    }

    /** Nombre de la app que suena (se muestra junto al artista). */
    fun sourceLabel(): String? = controller?.packageName?.let { appLabel(it) }

    fun prev() {
        val c = controller
        if (c != null) c.transportControls.skipToPrevious() else key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun next() {
        val c = controller
        if (c != null) c.transportControls.skipToNext() else key(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun playPause() {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
            return
        }
        if (!installed(SPOTIFY)) {
            key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        key(KeyEvent.KEYCODE_MEDIA_PLAY)
        handler.postDelayed({
            val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (controller?.playbackState?.state != PlaybackState.STATE_PLAYING && !am.isMusicActive) openApp()
        }, 2000)
    }

    /** Abre la app de música: la que suena, Spotify o la elegida en el botón de música. */
    fun openApp() {
        if (!hasAccess()) {
            if (!start(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))) start(Intent(Settings.ACTION_SETTINGS))
            return
        }
        val pm = act.packageManager
        controller?.packageName?.let { pkg -> pm.getLaunchIntentForPackage(pkg)?.let { if (start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return } }
        pm.getLaunchIntentForPackage(SPOTIFY)?.let { if (start(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) return }
        DockSlots.resolve(act, act.getSharedPreferences(DockSlots.PREFS, Context.MODE_PRIVATE), DockSlots.MUSIC)?.let { start(it) }
    }

    private fun installed(pkg: String) = act.packageManager.getLaunchIntentForPackage(pkg) != null

    private fun key(code: Int) {
        if (installed(SPOTIFY) && controller == null) {
            for (a in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                act.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(a, code)))
            }
        } else {
            val am = act.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
        handler.postDelayed({ onChange() }, 600)
    }

    private fun start(i: Intent): Boolean = try {
        act.startActivity(i)
        true
    } catch (e: Exception) {
        false
    }

    private fun appLabel(pkg: String): String = try {
        act.packageManager.getApplicationLabel(act.packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }
}
