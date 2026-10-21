package com.lastwave.app.sync

import android.content.Context
import android.media.AudioManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearQuickMixPayload
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.core.sync.WearableProtocol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class PhoneWearableListenerService : WearableListenerService() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var phoneWearableBridge: PhoneWearableBridge

    @Inject
    lateinit var downloadedTrackDao: DownloadedTrackDao

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
        val messageClient = Wearable.getMessageClient(this)

        when (path) {
            WearableProtocol.PATH_COMMAND -> {
                val raw = messageEvent.data.toString(Charsets.UTF_8)
                try {
                    val command = json.decodeFromString<PlaybackCommand>(raw)
                    when (command.action) {
                        PlaybackCommand.Action.PLAY -> musicPlayer.resume()
                        PlaybackCommand.Action.PAUSE -> musicPlayer.pause()
                        PlaybackCommand.Action.TOGGLE_PLAY_PAUSE -> musicPlayer.togglePlayPause()
                        PlaybackCommand.Action.SKIP_NEXT -> musicPlayer.next()
                        PlaybackCommand.Action.SKIP_PREVIOUS -> musicPlayer.previous()
                        PlaybackCommand.Action.SEEK -> {
                            command.positionMs?.let { musicPlayer.seekTo(it) }
                        }
                        PlaybackCommand.Action.ADJUST_VOLUME -> {
                            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            if (audioManager != null) {
                                val delta = command.volumeDelta ?: 0f
                                val direction = if (delta >= 0f) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
                            }
                        }
                        PlaybackCommand.Action.PLAY_TRACK -> {
                            val targetId = command.songId
                            if (!targetId.isNullOrBlank()) {
                                val queue = musicPlayer.state.value.queue
                                val queueIndex = queue.indexOfFirst { it.videoId == targetId }
                                if (queueIndex >= 0) {
                                    musicPlayer.playQueue(queue, queueIndex)
                                } else {
                                    serviceScope.launch {
                                        val downloaded = downloadedTrackDao.getAllList().firstOrNull { it.trackKey == targetId }
                                        if (downloaded != null) {
                                            val playable = com.lastwave.app.playback.PlayableTrack(
                                                title = downloaded.title,
                                                artist = downloaded.artist,
                                                album = downloaded.album,
                                                artworkUrl = downloaded.artworkUrl,
                                                videoId = downloaded.trackKey,
                                                playbackUrl = downloaded.filePath,
                                                durationMs = downloaded.durationMs
                                            )
                                            musicPlayer.play(playable)
                                        }
                                    }
                                }
                            }
                        }
                        PlaybackCommand.Action.TOGGLE_LIKE -> {
                            // Can be hooked to loved track repository if desired
                        }
                    }
                } catch (_: Exception) {}
            }

            WearableProtocol.PATH_PLAYBACK_REQUEST -> {
                serviceScope.launch {
                    val currentState = phoneWearableBridge.getCurrentWearPlaybackState()
                    val payload = json.encodeToString(currentState).toByteArray(Charsets.UTF_8)
                    messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PLAYBACK_RESPONSE, payload)
                    phoneWearableBridge.syncNow()
                }
            }

            WearableProtocol.PATH_QUICK_MIX_REQUEST -> {
                serviceScope.launch {
                    val tracksList = mutableListOf<WearTrackItem>()
                    val queue = musicPlayer.state.value.queue
                    for (track in queue.take(15)) {
                        tracksList.add(
                            WearTrackItem(
                                id = track.videoId ?: "${track.artist}_${track.title}",
                                title = track.title,
                                artist = track.artist,
                                album = track.album ?: "",
                                artworkUrl = track.artworkUrl,
                                durationMs = track.durationMs ?: 0L,
                                streamUrl = track.playbackUrl,
                                isLossless = true,
                                qualityBadge = "FLAC Lossless"
                            )
                        )
                    }

                    if (tracksList.size < 15) {
                        runCatching {
                            val downloaded = downloadedTrackDao.getAllList()
                            for (track in downloaded.take(15 - tracksList.size)) {
                                tracksList.add(
                                    WearTrackItem(
                                        id = track.trackKey,
                                        title = track.title,
                                        artist = track.artist,
                                        album = track.album,
                                        artworkUrl = track.artworkUrl,
                                        durationMs = track.durationMs,
                                        streamUrl = track.filePath,
                                        isLossless = true,
                                        qualityBadge = "Downloaded FLAC"
                                    )
                                )
                            }
                        }
                    }

                    val quickMixPayload = WearQuickMixPayload(tracks = tracksList)
                    val data = json.encodeToString(quickMixPayload).toByteArray(Charsets.UTF_8)
                    messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_QUICK_MIX_RESPONSE, data)
                }
            }

            WearableProtocol.PATH_PING -> {
                serviceScope.launch {
                    messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PING_ACK, "pong".toByteArray(Charsets.UTF_8))
                }
            }

            WearableProtocol.PATH_AUTH_REQUEST -> {
                serviceScope.launch {
                    phoneWearableBridge.syncNow()
                }
            }
        }
    }
}
