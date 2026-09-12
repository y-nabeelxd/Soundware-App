package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.example.MainActivity
import com.example.R
import com.example.media.MediaStateManager
import com.example.media.TrackInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BackgroundAudioService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val CHANNEL_ID = "soundwave_media_playback"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.soundwave.action.START"
        const val ACTION_STOP = "com.soundwave.action.STOP"
        const val ACTION_PLAY_PAUSE = "com.soundwave.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.soundwave.action.NEXT"
        const val ACTION_PREVIOUS = "com.soundwave.action.PREVIOUS"

        fun startService(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackgroundAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        requestAudioFocus()
        setupMediaSession()
        observeMediaState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                abandonAudioFocus()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY_PAUSE -> {
                MediaStateManager.triggerPlayPause()
            }
            ACTION_NEXT -> {
                MediaStateManager.triggerNext()
            }
            ACTION_PREVIOUS -> {
                MediaStateManager.triggerPrevious()
            }
        }

        val track = MediaStateManager.trackState.value
        updateNotificationAndSession(track)

        return START_NOT_STICKY
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "SoundwaveMediaSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    MediaStateManager.triggerPlayPause()
                }

                override fun onPause() {
                    MediaStateManager.triggerPlayPause()
                }

                override fun onSkipToNext() {
                    MediaStateManager.triggerNext()
                }

                override fun onSkipToPrevious() {
                    MediaStateManager.triggerPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    MediaStateManager.triggerSeekTo(pos)
                }

                override fun onStop() {
                    stopSelf()
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    private fun observeMediaState() {
        serviceScope.launch {
            MediaStateManager.trackState.collect { track ->
                updateNotificationAndSession(track)
            }
        }
    }

    private var lastNotificationTrackKey: String = ""

    private fun updateNotificationAndSession(track: TrackInfo) {
        // 1. Update MediaSession state & metadata
        val safePos = track.positionMs.coerceAtLeast(0L)
        val playbackSpeed = if (track.isPlaying) 1.0f else 0.0f

        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            )
            .setState(
                if (track.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                safePos,
                playbackSpeed
            )

        mediaSession?.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Soundwave")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, track.durationMs)

        if (track.artworkBitmap != null) {
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, track.artworkBitmap)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ART, track.artworkBitmap)
        }

        mediaSession?.setMetadata(metadataBuilder.build())

        // 2. Update Foreground Notification when metadata, artwork, duration, or play state changes
        val currentKey = "${track.title}|${track.artist}|${track.isPlaying}|${track.artworkUrl}|${track.durationMs}"
        if (currentKey != lastNotificationTrackKey) {
            lastNotificationTrackKey = currentKey
            val notification = buildMediaNotification(track)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildMediaNotification(track: TrackInfo): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val prevPendingIntent = PendingIntent.getService(
            this,
            1,
            prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this,
            2,
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, BackgroundAudioService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this,
            3,
            nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_media_previous),
            "Previous",
            prevPendingIntent
        ).build()

        val playPauseAction = Notification.Action.Builder(
            Icon.createWithResource(
                this,
                if (track.isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play
            ),
            if (track.isPlaying) "Pause" else "Play",
            playPausePendingIntent
        ).build()

        val nextAction = Notification.Action.Builder(
            Icon.createWithResource(this, R.drawable.ic_media_next),
            "Next",
            nextPendingIntent
        ).build()

        val mediaStyle = Notification.MediaStyle()
        mediaSession?.let {
            mediaStyle.setMediaSession(it.sessionToken)
        }
        // Show Previous, Play/Pause, Next in compact view (0, 1, 2)
        mediaStyle.setShowActionsInCompactView(0, 1, 2)

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setStyle(mediaStyle)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSubText("Soundwave")
            .setSmallIcon(R.drawable.ic_soundwave_notification)
            .setColor(0xFF1DB954.toInt()) // Spotify green
            .setContentIntent(contentPendingIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(track.isPlaying)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)

        if (track.artworkBitmap != null) {
            builder.setLargeIcon(track.artworkBitmap)
        }

        return builder.build()
    }

    private fun requestAudioFocus() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { focusChange ->
                        // Audio focus change listener
                    }
                    .build()
                audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(
                    { /* Audio focus callback */ },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        } catch (e: Exception) {
            // Audio focus request failure fallback
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus { }
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Soundwave::BackgroundAudioWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(12 * 60 * 60 * 1000L) // Safe 12-hour timeout
                }
            }
        } catch (e: Exception) {
            // Wake lock fallback
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            wakeLock = null
        } catch (ignored: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        cleanupAndStop()
    }

    private fun cleanupAndStop() {
        try {
            serviceScope.cancel()
        } catch (ignored: Exception) {}
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (ignored: Exception) {}
        abandonAudioFocus()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        cleanupAndStop()
        super.onDestroy()
    }
}
