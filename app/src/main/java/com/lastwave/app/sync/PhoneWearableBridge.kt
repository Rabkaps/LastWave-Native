package com.lastwave.app.sync

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.lastwave.app.playback.MusicPlayer
import com.lastwave.app.playback.qualityBadgeLabel
import com.lastwave.app.data.local.SessionPreferences
import com.lastwave.core.sync.WearAuthSession
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.core.sync.WearableProtocol
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

import com.google.android.gms.wearable.Node
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class PhoneWearableBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicPlayer: MusicPlayer,
    private val sessionPreferences: SessionPreferences,
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

                    _lastSyncTimestamp.value = System.currentTimeMillis()
                } catch (_: Exception) {}
            }
        }
    }
}
