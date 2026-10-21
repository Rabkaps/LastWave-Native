package com.lastwave.wear.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.wear.R
import com.lastwave.wear.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class WearPlaybackService : MediaSessionService() {

    private val cacheManager by lazy { com.lastwave.wear.WearApp.instance.cacheManager }

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val CHANNEL_ID = "lastwave_wear_playback"
        const val NOTIFICATION_ID = 1001

        private val _localPlaybackState = MutableStateFlow(WearPlaybackState())
        val localPlaybackState = _localPlaybackState.asStateFlow()

        @Volatile
        var currentServiceInstance: WearPlaybackService? = null

        fun playTrack(mediaItem: MediaItem) {
            currentServiceInstance?.player?.let { p ->
                p.setMediaItem(mediaItem)
                p.prepare()
                p.play()
            }
        }

        fun togglePlayPause() {
            currentServiceInstance?.player?.let { p ->
                if (p.isPlaying) p.pause() else p.play()
            }
        }

        fun skipNext() {
            currentServiceInstance?.player?.let { p ->
                if (p.hasNextMediaItem()) p.seekToNextMediaItem()
            }
        }

        fun skipPrevious() {
            currentServiceInstance?.player?.let { p ->
                if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        val upstreamFactory = DefaultDataSource.Factory(this)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cacheManager.getCache())
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val mediaSourceFactory = DefaultMediaSourceFactory(cacheDataSourceFactory)

        val exo = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true // Handle audio focus automatically
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateStateFromPlayer()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                updateStateFromPlayer()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStateFromPlayer()
            }
        })

        player = exo
        currentServiceInstance = this

        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .build()
    }

    private fun updateStateFromPlayer() {
        val p = player ?: return
        val currentMedia = p.currentMediaItem
        val metadata = currentMedia?.mediaMetadata

        val state = WearPlaybackState(
            songId = currentMedia?.mediaId,
            title = metadata?.title?.toString() ?: "",
            artist = metadata?.artist?.toString() ?: "",
            album = metadata?.albumTitle?.toString() ?: "",
            artworkUrl = metadata?.artworkUri?.toString(),
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition,
            durationMs = p.duration.coerceAtLeast(0L),
            qualityBadge = if (p.audioFormat?.sampleMimeType?.contains("flac", ignoreCase = true) == true) "FLAC" else "OPUS 160",
            isLossless = p.audioFormat?.sampleMimeType?.contains("flac", ignoreCase = true) == true,
            isLiked = false,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _localPlaybackState.value = state
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LastWave Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Watch media playback controls"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        currentServiceInstance = null
        super.onDestroy()
    }
}
