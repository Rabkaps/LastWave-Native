package com.lastwave.app.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.lastwave.app.data.generate.GeneratedTrack
import com.lastwave.app.data.generate.youtubeVideoIdOrNull
import com.lastwave.app.data.local.SessionPreferences
import com.lastwave.app.data.local.SettingsPreferences
import com.lastwave.app.data.local.db.DownloadedTrackDao
import com.lastwave.app.data.local.db.DownloadedTrackEntity
import com.lastwave.app.data.playlist.PlaylistRepository
import com.lastwave.app.data.playlist.isYouTubeOnly
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.PlayableTrack
import com.lastwave.app.playback.qualityBadgeLabel
import com.lastwave.core.sync.WearStreamResolveResponse
import com.lastwave.core.sync.WearAuthSession
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.core.sync.WearPlaylistItem
import com.lastwave.core.sync.WearPlaylistsPayload
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.core.sync.WearableProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhoneWearableBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicPlayer: MusicPlayer,
    private val sessionPreferences: SessionPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val playlistRepository: PlaylistRepository,
    private val downloadedTrackDao: DownloadedTrackDao,
) {
    private val dataClient = Wearable.getDataClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val json = Json { ignoreUnknownKeys = true }

    private val _connectedWatches = MutableStateFlow<List<Node>>(emptyList())
    val connectedWatches = _connectedWatches.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow<Long?>(null)
    val lastSyncTimestamp = _lastSyncTimestamp.asStateFlow()

    suspend fun refreshConnectedWatches(): List<Node> = withContext(Dispatchers.IO) {
        try {
            val nodes = Tasks.await(nodeClient.connectedNodes)
            _connectedWatches.value = nodes
            nodes
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getCurrentWearPlaybackState(): WearPlaybackState {
        val state = musicPlayer.state.value
        val currentTrack = state.current
        return WearPlaybackState(
            songId = currentTrack?.videoId,
            title = currentTrack?.title ?: "",
            artist = currentTrack?.artist ?: "",
            album = currentTrack?.album ?: "",
            artworkUrl = currentTrack?.artworkUrl,
            isPlaying = state.isPlaying,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            qualityBadge = qualityBadgeLabel(state),
            isLossless = state.isLossless,
            isLiked = false,
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }

    suspend fun sendPingToWatch(nodeId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            Tasks.await(messageClient.sendMessage(nodeId, WearableProtocol.PATH_PING, "ping".toByteArray(Charsets.UTF_8)))
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setWatchStorageCap(capBytes: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = com.lastwave.core.sync.WearCachePreferences(maxCacheSizeBytes = capBytes)
            val putDataMapReq = PutDataMapRequest.create(WearableProtocol.PATH_CACHE_SETTINGS).apply {
                dataMap.putString("payload", json.encodeToString(prefs))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Tasks.await(dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()))
            val nodes = refreshConnectedWatches()
            val payload = json.encodeToString(prefs).toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                runCatching {
                    Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_CACHE_SETTINGS, payload))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAvailableWatchPlaylists(): List<WearPlaylistItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<WearPlaylistItem>()

        // 1. Liked Songs
        val liked = playlistRepository.getLikedSongs()
        val likedCount = liked?.tracks?.size ?: 0
        list.add(
            WearPlaylistItem(
                id = "liked_songs",
                title = "Liked Songs",
                subtitle = if (likedCount > 0) "$likedCount tracks" else "Your favorites",
                artworkUrl = liked?.tracks?.firstOrNull()?.artworkUrl,
                trackCount = likedCount,
                source = "LastWave",
                isRadio = false
            )
        )

        // 2. Local Songs (Device storage)
        val local = playlistRepository.getLocalSongs()
        val localCount = local?.tracks?.size ?: 0
        list.add(
            WearPlaylistItem(
                id = "local_songs",
                title = "Device Songs",
                subtitle = if (localCount > 0) "$localCount tracks" else "Offline on phone",
                artworkUrl = local?.tracks?.firstOrNull()?.artworkUrl,
                trackCount = localCount,
                source = "Local",
                isRadio = false
            )
        )

        // 3. Downloaded FLAC
        val downloaded = runCatching { downloadedTrackDao.getAllList() }.getOrDefault(emptyList())
        if (downloaded.isNotEmpty()) {
            list.add(
                WearPlaylistItem(
                    id = "downloaded_songs",
                    title = "Downloaded FLAC",
                    subtitle = "${downloaded.size} tracks offline",
                    artworkUrl = downloaded.firstOrNull()?.artworkUrl,
                    trackCount = downloaded.size,
                    source = "Downloads",
                    isRadio = false
                )
            )
        }

        // 4. User Saved & Synced Playlists
        val allPlaylists = playlistRepository.getAll()
        for (pl in allPlaylists) {
            val count = pl.remoteTrackCount ?: pl.tracks.size
            list.add(
                WearPlaylistItem(
                    id = pl.id.toString(),
                    title = pl.title,
                    subtitle = if (pl.isYouTubeOnly) "YouTube Playlist • $count tracks" else "$count tracks",
                    artworkUrl = pl.customCoverUri ?: pl.remoteArtworkUrl ?: pl.tracks.firstOrNull()?.artworkUrl,
                    trackCount = count,
                    source = if (pl.isYouTubeOnly) "YouTube" else "LastWave",
                    isRadio = false
                )
            )
        }

        // 5. Radios & Mixes
        list.add(
            WearPlaylistItem(
                id = "radio_supermix",
                title = "My Supermix",
                subtitle = "Endless personalized mix",
                isRadio = true,
                source = "Radio"
            )
        )
        list.add(
            WearPlaylistItem(
                id = "radio_infinite",
                title = "Infinite Radio",
                subtitle = "Algorithmic discovery mix",
                isRadio = true,
                source = "Radio"
            )
        )
        list.add(
            WearPlaylistItem(
                id = "radio_chill",
                title = "Chill Radio",
                subtitle = "Relaxing & acoustic vibes",
                isRadio = true,
                source = "Radio"
            )
        )
        list.add(
            WearPlaylistItem(
                id = "radio_energy",
                title = "Energy Radio",
                subtitle = "Upbeat & workout tempo",
                isRadio = true,
                source = "Radio"
            )
        )
        list.add(
            WearPlaylistItem(
                id = "radio_focus",
                title = "Focus Radio",
                subtitle = "Ambient & deep work",
                isRadio = true,
                source = "Radio"
            )
        )

        list
    }

    suspend fun getSyncedWatchPlaylists(): List<WearPlaylistItem> = withContext(Dispatchers.IO) {
        val all = getAvailableWatchPlaylists()
        val chosenIds = settingsPreferences.watchSyncedPlaylistIds.value
        val filtered = all.filter { it.id in chosenIds }
        if (filtered.isNotEmpty()) filtered else all.take(6)
    }

    suspend fun pushSyncedPlaylistsToWatch(): Boolean = withContext(Dispatchers.IO) {
        try {
            val playlists = getSyncedWatchPlaylists()
            val payload = WearPlaylistsPayload(playlists = playlists)
            val jsonStr = json.encodeToString(payload)

            val putDataMapReq = PutDataMapRequest.create(WearableProtocol.PATH_SYNCED_PLAYLISTS).apply {
                dataMap.putString("payload", jsonStr)
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            Tasks.await(dataClient.putDataItem(putDataMapReq.asPutDataRequest().setUrgent()))

            val nodes = refreshConnectedWatches()
            val bytes = jsonStr.toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                runCatching {
                    Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_PLAYLISTS_RESPONSE, bytes))
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getTracksForWatchPlaylist(playlistId: String): Pair<String, List<WearTrackItem>> = withContext(Dispatchers.IO) {
        when {
            playlistId == "liked_songs" -> {
                val p = playlistRepository.getLikedSongs()
                val tracks = p?.tracks?.take(50)?.map { it.toWearTrackItem() } ?: emptyList()
                Pair("Liked Songs", tracks)
            }
            playlistId == "local_songs" -> {
                val p = playlistRepository.getLocalSongs()
                val tracks = p?.tracks?.take(50)?.map { it.toWearTrackItem() } ?: emptyList()
                Pair("Device Songs", tracks)
            }
            playlistId == "downloaded_songs" -> {
                val tracks = downloadedTrackDao.getAllList().take(50).map { it.toWearTrackItem() }
                Pair("Downloaded FLAC", tracks)
            }
            playlistId.startsWith("radio_") -> {
                val radioTitle = when (playlistId) {
                    "radio_supermix" -> "My Supermix"
                    "radio_infinite" -> "Infinite Radio"
                    "radio_chill" -> "Chill Radio"
                    "radio_energy" -> "Energy Radio"
                    "radio_focus" -> "Focus Radio"
                    else -> "Radio"
                }
                val queueTracks = musicPlayer.state.value.queue.take(15).map {
                    WearTrackItem(
                        id = it.videoId ?: "${it.artist}_${it.title}",
                        title = it.title,
                        artist = it.artist,
                        album = it.album ?: "",
                        artworkUrl = it.artworkUrl,
                        durationMs = it.durationMs ?: 0L,
                        streamUrl = it.playbackUrl,
                        isLossless = true,
                        qualityBadge = "RADIO"
                    )
                }
                val fallbackLiked = if (queueTracks.size < 10) {
                    playlistRepository.getLikedSongs()?.tracks?.take(15)?.map { it.toWearTrackItem() } ?: emptyList()
                } else emptyList()
                Pair(radioTitle, (queueTracks + fallbackLiked).distinctBy { it.id }.take(25))
            }
            else -> {
                val plIdLong = playlistId.toLongOrNull()
                if (plIdLong != null) {
                    val p = playlistRepository.getById(plIdLong)
                    val tracks = p?.tracks?.take(50)?.map { it.toWearTrackItem() } ?: emptyList()
                    Pair(p?.title ?: "Playlist", tracks)
                } else {
                    Pair("Playlist", emptyList())
                }
            }
        }
    }

    suspend fun resolveStreamForWatch(
        songId: String,
        title: String = "",
        artist: String = ""
    ): WearStreamResolveResponse = withContext(Dispatchers.IO) {
        try {
            val directVideoId = songId.takeIf {
                it.isNotBlank() && !it.contains("/") && !it.contains(":") && !it.contains("_")
            }
            val playable = PlayableTrack(
                title = title.ifBlank { "Track" },
                artist = artist.ifBlank { "Artist" },
                videoId = directVideoId
            )
            val resolved = musicPlayer.resolveWatchStream(playable, directVideoId)
            android.util.Log.i("PhoneWearableBridge", "resolveStreamForWatch success for '$title' ($songId): ${resolved.url}")
            WearStreamResolveResponse(
                songId = songId,
                streamUrl = resolved.url,
                mimeType = resolved.mimeType,
                isLossless = resolved.isLossless
            )
        } catch (e: Exception) {
            android.util.Log.e("PhoneWearableBridge", "Failed to resolve stream for watch: ${e.message}", e)
            WearStreamResolveResponse(
                songId = songId,
                error = e.message ?: "Failed to resolve stream"
            )
        }
    }

    suspend fun syncNow(): Boolean = withContext(Dispatchers.IO) {
        val wearState = getCurrentWearPlaybackState()

        try {
            val putDataMapReq = PutDataMapRequest.create(WearableProtocol.PATH_PLAYBACK_STATE).apply {
                dataMap.putString("payload", json.encodeToString(wearState))
                dataMap.putLong("timestamp", System.currentTimeMillis())
            }
            val request = putDataMapReq.asPutDataRequest().setUrgent()
            Tasks.await(dataClient.putDataItem(request))

            val session = sessionPreferences.session.value
            if (session.isAuthenticated) {
                val authSession = WearAuthSession(
                    lastFmUsername = session.username,
                    lastFmSessionKey = session.sessionKey,
                    syncedAtEpochMs = System.currentTimeMillis()
                )
                val authReq = PutDataMapRequest.create(WearableProtocol.PATH_AUTH_DATA).apply {
                    dataMap.putString("payload", json.encodeToString(authSession))
                    dataMap.putLong("timestamp", System.currentTimeMillis())
                }
                Tasks.await(dataClient.putDataItem(authReq.asPutDataRequest().setUrgent()))
            }

            pushSyncedPlaylistsToWatch()

            _lastSyncTimestamp.value = System.currentTimeMillis()
            refreshConnectedWatches()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun startSync(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            refreshConnectedWatches()
            pushSyncedPlaylistsToWatch()

            musicPlayer.state.collectLatest { state ->
                val currentTrack = state.current
                val wearState = WearPlaybackState(
                    songId = currentTrack?.videoId,
                    title = currentTrack?.title ?: "",
                    artist = currentTrack?.artist ?: "",
                    album = currentTrack?.album ?: "",
                    artworkUrl = currentTrack?.artworkUrl,
                    isPlaying = state.isPlaying,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    qualityBadge = qualityBadgeLabel(state),
                    isLossless = state.isLossless,
                    isLiked = false,
                    updatedAtEpochMs = System.currentTimeMillis()
                )

                try {
                    val wearStateJson = json.encodeToString(wearState)
                    val wearStateBytes = wearStateJson.toByteArray(Charsets.UTF_8)

                    val putDataMapReq = PutDataMapRequest.create(WearableProtocol.PATH_PLAYBACK_STATE).apply {
                        dataMap.putString("payload", wearStateJson)
                        dataMap.putLong("timestamp", System.currentTimeMillis())
                    }
                    val request = putDataMapReq.asPutDataRequest().setUrgent()
                    Tasks.await(dataClient.putDataItem(request))

                    val nodes = _connectedWatches.value.ifEmpty { refreshConnectedWatches() }
                    for (node in nodes) {
                        runCatching {
                            messageClient.sendMessage(node.id, WearableProtocol.PATH_PLAYBACK_RESPONSE, wearStateBytes)
                        }
                    }

                    val session = sessionPreferences.session.value
                    if (session.isAuthenticated) {
                        val authSession = WearAuthSession(
                            lastFmUsername = session.username,
                            lastFmSessionKey = session.sessionKey,
                            syncedAtEpochMs = System.currentTimeMillis()
                        )
                        val authReq = PutDataMapRequest.create(WearableProtocol.PATH_AUTH_DATA).apply {
                            dataMap.putString("payload", json.encodeToString(authSession))
                            dataMap.putLong("timestamp", System.currentTimeMillis())
                        }
                        Tasks.await(dataClient.putDataItem(authReq.asPutDataRequest().setUrgent()))
                    }

                    _lastSyncTimestamp.value = System.currentTimeMillis()
                } catch (_: Exception) {}
            }
        }
    }
}

fun GeneratedTrack.toWearTrackItem(): WearTrackItem {
    val vid = this.youtubeVideoIdOrNull() ?: "${artist}_${name}"
    return WearTrackItem(
        id = vid,
        title = name,
        artist = artist,
        album = album ?: "",
        artworkUrl = artworkUrl,
        durationMs = 0L,
        isLossless = true,
        qualityBadge = "FLAC"
    )
}

fun DownloadedTrackEntity.toWearTrackItem(): WearTrackItem {
    return WearTrackItem(
        id = trackKey,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        streamUrl = null,
        isLossless = true,
        qualityBadge = "Downloaded FLAC"
    )
}
