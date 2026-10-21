package com.lastwave.app.sync

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.playlist.PlaylistRepository
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.PlayableTrack
import com.lastwave.app.playback.toPlayableTrack
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearPlaylistTracksRequest
import com.lastwave.core.sync.WearPlaylistTracksResponse
import com.lastwave.core.sync.WearPlaylistsPayload
import com.lastwave.core.sync.WearQuickMixPayload
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.core.sync.WearableProtocol
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

private const val TAG = "PhoneWearableListener"

@AndroidEntryPoint
class PhoneWearableListenerService : WearableListenerService() {

    @Inject
    lateinit var musicPlayer: MusicPlayer

    @Inject
    lateinit var phoneWearableBridge: PhoneWearableBridge

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    @Inject
    lateinit var downloadedTrackDao: DownloadedTrackDao

    private val json = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val sourceNodeId = messageEvent.sourceNodeId
        val messageClient = Wearable.getMessageClient(this)

        // Run synchronously on the Binder worker thread with a timeout so Play Services keeps service bound
        runBlocking {
            withTimeoutOrNull(10_000L) {
                try {
                    when (path) {
                        WearableProtocol.PATH_COMMAND -> {
                            val raw = messageEvent.data.toString(Charsets.UTF_8)
                            val command = json.decodeFromString<PlaybackCommand>(raw)
                            handleCommand(command)

                            // Send updated state back to watch AFTER command completes execution
                            val updatedState = phoneWearableBridge.getCurrentWearPlaybackState()
                            val respPayload = json.encodeToString(updatedState).toByteArray(Charsets.UTF_8)
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PLAYBACK_RESPONSE, respPayload)
                        }

                        WearableProtocol.PATH_PLAYBACK_REQUEST -> {
                            val currentState = phoneWearableBridge.getCurrentWearPlaybackState()
                            val payload = json.encodeToString(currentState).toByteArray(Charsets.UTF_8)
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PLAYBACK_RESPONSE, payload)
                            phoneWearableBridge.syncNow()
                        }

                        WearableProtocol.PATH_PLAYLISTS_REQUEST -> {
                            phoneWearableBridge.pushSyncedPlaylistsToWatch()
                            val playlists = phoneWearableBridge.getSyncedWatchPlaylists()
                            val payload = WearPlaylistsPayload(playlists = playlists)
                            val data = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PLAYLISTS_RESPONSE, data)
                        }

                        WearableProtocol.PATH_PLAYLIST_TRACKS_REQUEST -> {
                            val raw = messageEvent.data.toString(Charsets.UTF_8)
                            val playlistId = runCatching {
                                json.decodeFromString<WearPlaylistTracksRequest>(raw).playlistId
                            }.getOrDefault(raw)

                            val (title, tracks) = phoneWearableBridge.getTracksForWatchPlaylist(playlistId)
                            val response = WearPlaylistTracksResponse(
                                playlistId = playlistId,
                                playlistTitle = title,
                                tracks = tracks
                            )
                            val data = json.encodeToString(response).toByteArray(Charsets.UTF_8)
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PLAYLIST_TRACKS_RESPONSE, data)
                        }

                        WearableProtocol.PATH_QUICK_MIX_REQUEST -> {
                            val tracks = withContext(Dispatchers.IO) {
                                val liked = playlistRepository.getLikedSongs()?.tracks ?: emptyList()
                                val queue = musicPlayer.state.value.queue
                                val downloaded = runCatching { downloadedTrackDao.getAllList() }.getOrDefault(emptyList())
                                val allPlaylists = playlistRepository.getAll().flatMap { it.tracks }

                                val items = mutableListOf<WearTrackItem>()
                                queue.forEach {
                                    items.add(WearTrackItem(id = it.videoId ?: it.title, title = it.title, artist = it.artist, album = it.album ?: "", artworkUrl = it.artworkUrl, durationMs = it.durationMs ?: 0L))
                                }
                                liked.forEach {
                                    items.add(it.toWearTrackItem())
                                }
                                downloaded.forEach {
                                    items.add(it.toWearTrackItem())
                                }
                                allPlaylists.forEach {
                                    items.add(it.toWearTrackItem())
                                }
                                items.distinctBy { it.id }.shuffled().take(25)
                            }
                            val payload = WearQuickMixPayload(tracks = tracks)
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_QUICK_MIX_RESPONSE, json.encodeToString(payload).toByteArray(Charsets.UTF_8))
                        }

                        WearableProtocol.PATH_PING -> {
                            messageClient.sendMessage(sourceNodeId, WearableProtocol.PATH_PING_ACK, byteArrayOf())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling message on $path: ${e.message}", e)
                }
            }
        }
    }

    private suspend fun handleCommand(command: PlaybackCommand) {
        when (command.action) {
            PlaybackCommand.Action.PLAY -> withContext(Dispatchers.Main.immediate) { musicPlayer.resume() }
            PlaybackCommand.Action.PAUSE -> withContext(Dispatchers.Main.immediate) { musicPlayer.pause() }
            PlaybackCommand.Action.TOGGLE_PLAY_PAUSE -> withContext(Dispatchers.Main.immediate) { musicPlayer.togglePlayPause() }
            PlaybackCommand.Action.SKIP_NEXT -> withContext(Dispatchers.Main.immediate) { musicPlayer.next() }
            PlaybackCommand.Action.SKIP_PREVIOUS -> withContext(Dispatchers.Main.immediate) { musicPlayer.previous() }
            PlaybackCommand.Action.SEEK -> {
                command.positionMs?.let { pos ->
                    withContext(Dispatchers.Main.immediate) { musicPlayer.seekTo(pos) }
                }
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
                val targetId = command.songId ?: return
                val queue = musicPlayer.state.value.queue
                val queueIndex = queue.indexOfFirst { it.videoId == targetId }
                if (queueIndex >= 0) {
                    withContext(Dispatchers.Main.immediate) { musicPlayer.playQueue(queue, queueIndex) }
                } else {
                    val downloaded = withContext(Dispatchers.IO) {
                        downloadedTrackDao.getAllList().firstOrNull { it.trackKey == targetId }
                    }
                    if (downloaded != null) {
                        val playable = PlayableTrack(
                            title = downloaded.title,
                            artist = downloaded.artist,
                            album = downloaded.album,
                            artworkUrl = downloaded.artworkUrl,
                            videoId = downloaded.trackKey,
                            playbackUrl = downloaded.filePath,
                            durationMs = downloaded.durationMs
                        )
                        withContext(Dispatchers.Main.immediate) { musicPlayer.play(playable) }
                    }
                }
            }
            PlaybackCommand.Action.PLAY_PLAYLIST -> {
                command.playlistId?.let { playPlaylistInternal(it) }
            }
            PlaybackCommand.Action.PLAY_RADIO -> {
                val radioId = command.radioId ?: command.playlistId ?: return
                playRadioInternal(radioId)
            }
            PlaybackCommand.Action.TOGGLE_LIKE -> {
                // Like toggling is handled through TrackLikeController on the phone UI
            }
        }
    }

    private suspend fun playPlaylistInternal(playlistId: String) {
        when {
            playlistId == "liked_songs" -> {
                val tracks = withContext(Dispatchers.IO) {
                    playlistRepository.getLikedSongs()?.tracks?.map { it.toPlayableTrack() } ?: emptyList()
                }
                if (tracks.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        musicPlayer.playQueue(tracks, startIndex = 0, sourceLabel = "Liked Songs")
                    }
                }
            }
            playlistId == "local_songs" -> {
                val tracks = withContext(Dispatchers.IO) {
                    playlistRepository.getLocalSongs()?.tracks?.map { it.toPlayableTrack() } ?: emptyList()
                }
                if (tracks.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        musicPlayer.playQueue(tracks, startIndex = 0, sourceLabel = "Device Songs")
                    }
                }
            }
            playlistId == "downloaded_songs" -> {
                val tracks = withContext(Dispatchers.IO) {
                    downloadedTrackDao.getAllList().map {
                        PlayableTrack(
                            title = it.title,
                            artist = it.artist,
                            album = it.album,
                            artworkUrl = it.artworkUrl,
                            videoId = it.trackKey,
                            playbackUrl = it.filePath,
                            durationMs = it.durationMs
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    withContext(Dispatchers.Main.immediate) {
                        musicPlayer.playQueue(tracks, startIndex = 0, sourceLabel = "Downloaded FLAC")
                    }
                }
            }
            playlistId.startsWith("radio_") -> {
                playRadioInternal(playlistId)
            }
            else -> {
                val plIdLong = playlistId.toLongOrNull()
                if (plIdLong != null) {
                    val (title, tracks) = withContext(Dispatchers.IO) {
                        val p = playlistRepository.getById(plIdLong)
                        (p?.title ?: "Playlist") to (p?.tracks?.map { it.toPlayableTrack() } ?: emptyList())
                    }
                    if (tracks.isNotEmpty()) {
                        withContext(Dispatchers.Main.immediate) {
                            musicPlayer.playQueue(tracks, startIndex = 0, sourceLabel = title)
                        }
                    }
                }
            }
        }
    }

    private suspend fun playRadioInternal(radioId: String) {
        val radioTitle = when (radioId) {
            "radio_supermix" -> "My Supermix"
            "radio_infinite" -> "Infinite Radio"
            "radio_chill" -> "Chill Radio"
            "radio_energy" -> "Energy Radio"
            "radio_focus" -> "Focus Radio"
            else -> "Radio"
        }

        val pool = withContext(Dispatchers.IO) {
            val liked = playlistRepository.getLikedSongs()?.tracks ?: emptyList()
            val currentQueue = musicPlayer.state.value.queue
            val local = playlistRepository.getLocalSongs()?.tracks ?: emptyList()
            val downloaded = runCatching { downloadedTrackDao.getAllList() }.getOrDefault(emptyList()).map {
                PlayableTrack(
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    artworkUrl = it.artworkUrl,
                    videoId = it.trackKey,
                    playbackUrl = it.filePath,
                    durationMs = it.durationMs
                )
            }
            val allPlaylists = playlistRepository.getAll().flatMap { it.tracks }

            (currentQueue + liked.map { it.toPlayableTrack() } + local.map { it.toPlayableTrack() } + downloaded + allPlaylists.map { it.toPlayableTrack() })
                .filter { !it.videoId.isNullOrBlank() }
                .distinctBy { it.videoId }
                .shuffled()
        }

        if (pool.isNotEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                musicPlayer.playQueue(
                    tracks = pool.take(30),
                    startIndex = 0,
                    sourceLabel = radioTitle,
                    enableRadioContinuation = true
                )
            }
        }
    }
}
