package com.juandiego.priuslauncher

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var dateView: TextView
    private lateinit var greetingView: TextView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var albumArt: ImageView
    private lateinit var btnPlay: ImageButton
    private lateinit var dock: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val spanish = Locale("es", "MX")

    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateDate()
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateNowPlaying()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateNowPlaying()
        }

        override fun onSessionDestroyed() {
            bindController(null)
            refreshSessions()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    // ---------- Ciclo de vida ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hideSystemUi()

        prefs = getSharedPreferences(DockSlots.PREFS, MODE_PRIVATE)
        dateView = findViewById(R.id.date)
        greetingView = findViewById(R.id.greeting)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        albumArt = findViewById(R.id.albumArt)
        btnPlay = findViewById(R.id.btnPlay)
        dock = findViewById(R.id.dock)

        setupMediaButtons()
        buildDock()

        if (!prefs.getBoolean("hint_shown", false)) {
            Toast.makeText(this, R.string.hint_long_press, Toast.LENGTH_LONG).show()
            prefs.edit().putBoolean("hint_shown", true).apply()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        registerReceiver(timeReceiver, filter)
        updateDate()
        startMediaWatch()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        updateNowPlaying()
    }

    override fun onStop() {
        super.onStop()
        try {
            unregisterReceiver(timeReceiver)
        } catch (e: IllegalArgumentException) {
            // ya estaba desregistrado
        }
        stopMediaWatch()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Es la pantalla de inicio: el botón "atrás" no hace nada aquí.
    }

    // ---------- Fecha y saludo ----------

    private fun updateDate() {
        val text = SimpleDateFormat("EEEE d 'de' MMMM", spanish).format(Date())
        dateView.text = text.replaceFirstChar { it.titlecase(spanish) }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when {
            hour < 12 -> R.string.good_morning
            hour < 19 -> R.string.good_afternoon
            else -> R.string.good_evening
        }
        greetingView.text = getString(R.string.greeting_fmt, getString(part), getString(R.string.driver_name))
    }

    // ---------- Barra de apps ----------

    private fun buildDock() {
        dock.removeAllViews()
        DockSlots.ALL.forEachIndexed { i, slot ->
            addDockItem(getString(slot.labelRes), slot.iconRes, { openSlot(i) }, { pickForSlot(i) })
        }
        addDockItem(getString(R.string.slot_apps), R.drawable.ic_apps, {
            startActivity(Intent(this, AppsActivity::class.java))
        }, null)
    }

    private fun addDockItem(label: String, icon: Int, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
        val item = layoutInflater.inflate(R.layout.dock_item, dock, false)
        item.findViewById<ImageView>(R.id.icon).setImageResource(icon)
        item.findViewById<TextView>(R.id.label).text = label
        item.contentDescription = label
        item.setOnClickListener { onClick() }
        if (onLongClick != null) {
            item.setOnLongClickListener {
                onLongClick()
                true
            }
        }
        dock.addView(item)
    }

    private fun openSlot(index: Int) {
        val intent = DockSlots.resolve(this, prefs, index)
        if (intent == null) {
            pickForSlot(index)
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            pickForSlot(index)
        } catch (e: SecurityException) {
            pickForSlot(index)
        }
    }

    private fun pickForSlot(index: Int) {
        startActivity(Intent(this, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_PICK_SLOT, index))
    }

    // ---------- Música ----------

    private fun setupMediaButtons() {
        findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToPrevious() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
        btnPlay.setOnClickListener {
            val c = controller
            if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) {
                    c.transportControls.pause()
                } else {
                    c.transportControls.play()
                }
            } else {
                sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        }
        findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val c = controller
            if (c != null) c.transportControls.skipToNext() else sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
        }

        findViewById<View>(R.id.mediaCard).setOnClickListener {
            if (!hasNotificationAccess()) {
                openNotificationAccess()
                return@setOnClickListener
            }
            val pkg = controller?.packageName
            val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            if (launch != null) {
                try {
                    startActivity(launch)
                } catch (e: ActivityNotFoundException) {
                    openSlot(1)
                }
            } else {
                openSlot(1) // botón "Música"
            }
        }
    }

    private fun sendMediaKey(code: Int) {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        handler.postDelayed({ if (controller == null) setPlayIcon(am.isMusicActive) }, 600)
    }

    private fun setPlayIcon(playing: Boolean) {
        btnPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun listenerComponent() = ComponentName(this, MediaListenerService::class.java)

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return enabled?.contains("$packageName/") == true
    }

    private fun openNotificationAccess() {
        try {
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_access_screen, Toast.LENGTH_LONG).show()
        }
    }

    private fun startMediaWatch() {
        if (!hasNotificationAccess()) {
            bindController(null)
            return
        }
        val msm = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, listenerComponent())
            sessionManager = msm
            refreshSessions()
        } catch (e: SecurityException) {
            sessionManager = null
            bindController(null)
        }
    }

    private fun stopMediaWatch() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        sessionManager = null
        bindController(null)
    }

    private fun refreshSessions() {
        val msm = sessionManager ?: return
        try {
            pickController(msm.getActiveSessions(listenerComponent()))
        } catch (e: SecurityException) {
            bindController(null)
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val playing = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        bindController(playing ?: list?.firstOrNull())
    }

    private fun bindController(c: MediaController?) {
        val old = controller
        if (old != null && c != null && old.sessionToken == c.sessionToken) {
            updateNowPlaying()
            return
        }
        old?.unregisterCallback(mediaCallback)
        controller = c
        c?.registerCallback(mediaCallback)
        updateNowPlaying()
    }

    private fun updateNowPlaying() {
        if (!::trackTitle.isInitialized) return
        val c = controller
        if (c == null) {
            val access = hasNotificationAccess()
            trackTitle.text = getString(if (access) R.string.nothing_playing else R.string.grant_access_title)
            trackArtist.text = if (access) "" else getString(R.string.grant_access_sub)
            albumArt.visibility = View.GONE
            setPlayIcon((getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive)
            return
        }
        val md = c.metadata
        trackTitle.text = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: appLabel(c.packageName)
        trackArtist.text = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM)
            ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art != null) {
            albumArt.setImageBitmap(art)
            albumArt.visibility = View.VISIBLE
        } else {
            albumArt.visibility = View.GONE
        }
        setPlayIcon(c.playbackState?.state == PlaybackState.STATE_PLAYING)
    }

    private fun appLabel(pkg: String): String =
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
}
