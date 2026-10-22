package com.lastwave.wear.sync

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.lastwave.core.sync.WearAuthSession
import com.lastwave.core.sync.WearCachePreferences
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.core.sync.WearPlaylistItem
import com.lastwave.core.sync.WearPlaylistTracksResponse
import com.lastwave.core.sync.WearPlaylistsPayload
import com.lastwave.core.sync.WearQuickMixPayload
import com.lastwave.core.sync.WearStreamResolveResponse
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.core.sync.WearableProtocol
import com.lastwave.wear.WearApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private const val TAG = "WearDataLayerListener"

class WearDataLayerListenerService : WearableListenerService() {

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private val _phonePlaybackState = MutableStateFlow(WearPlaybackState())
        val phonePlaybackState = _phonePlaybackState.asStateFlow()

        private val _syncedAuthSession = MutableStateFlow<WearAuthSession?>(null)
        val syncedAuthSession = _syncedAuthSession.asStateFlow()

        private val _quickMixTracks = MutableStateFlow<List<WearTrackItem>>(emptyList())
        val quickMixTracks = _quickMixTracks.asStateFlow()

        private val DEFAULT_PLAYLISTS = listOf(
            WearPlaylistItem(id = "liked_songs", title = "Liked Songs", subtitle = "Your favorites", trackCount = 0, source = "LastWave", isRadio = false),
            WearPlaylistItem(id = "local_songs", title = "Device Songs", subtitle = "Offline on phone", trackCount = 0, source = "Local", isRadio = false),
            WearPlaylistItem(id = "downloaded_songs", title = "Downloaded FLAC", subtitle = "Lossless storage", trackCount = 0, source = "Downloads", isRadio = false),
            WearPlaylistItem(id = "radio_supermix", title = "My Supermix", subtitle = "Endless personalized mix", trackCount = 0, source = "Radio", isRadio = true),
            WearPlaylistItem(id = "radio_infinite", title = "Infinite Radio", subtitle = "Algorithmic discovery mix", trackCount = 0, source = "Radio", isRadio = true),
            WearPlaylistItem(id = "radio_chill", title = "Chill Radio", subtitle = "Relaxing & acoustic vibes", trackCount = 0, source = "Radio", isRadio = true),
            WearPlaylistItem(id = "radio_energy", title = "Energy Radio", subtitle = "Upbeat & workout tempo", trackCount = 0, source = "Radio", isRadio = true),
            WearPlaylistItem(id = "radio_focus", title = "Focus Radio", subtitle = "Deep work & ambient", trackCount = 0, source = "Radio", isRadio = true)
        )
        private val _syncedPlaylists = MutableStateFlow<List<WearPlaylistItem>>(DEFAULT_PLAYLISTS)
        val syncedPlaylists = _syncedPlaylists.asStateFlow()

        private val _playlistTracks = MutableStateFlow<Map<String, List<WearTrackItem>>>(emptyMap())
        val playlistTracks = _playlistTracks.asStateFlow()

        fun updatePhonePlaybackState(state: WearPlaybackState) {
            _phonePlaybackState.value = state
        }

        fun updateSyncedPlaylists(playlists: List<WearPlaylistItem>) {
            if (playlists.isNotEmpty()) {
                _syncedPlaylists.value = playlists
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val uri = event.dataItem.uri
                val path = uri.path ?: continue

                when (path) {
                    WearableProtocol.PATH_PLAYBACK_STATE -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val jsonString = dataMap.getString("payload")
                        if (!jsonString.isNullOrBlank()) {
                            try {
                                val state = json.decodeFromString<WearPlaybackState>(jsonString)
                                _phonePlaybackState.value = state
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding playback state data: ${e.message}")
                            }
                        }
                    }
                    WearableProtocol.PATH_AUTH_DATA -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val jsonString = dataMap.getString("payload")
                        if (!jsonString.isNullOrBlank()) {
                            try {
                                val session = json.decodeFromString<WearAuthSession>(jsonString)
                                _syncedAuthSession.value = session
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding auth data: ${e.message}")
                            }
                        }
                    }
                    WearableProtocol.PATH_SYNCED_PLAYLISTS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val jsonString = dataMap.getString("payload")
                        if (!jsonString.isNullOrBlank()) {
                            try {
                                val payload = json.decodeFromString<WearPlaylistsPayload>(jsonString)
                                _syncedPlaylists.value = payload.playlists
                            } catch (e: Exception) {
                                Log.e(TAG, "Error decoding synced playlists data: ${e.message}")
                            }
                        }
                    }
                    WearableProtocol.PATH_CACHE_SETTINGS -> {
                        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                        val jsonString = dataMap.getString("payload")
                        if (!jsonString.isNullOrBlank()) {
                            try {
                                val prefs = json.decodeFromString<WearCachePreferences>(jsonString)
                                WearApp.instance.cacheManager.setMaxCacheLimit(prefs.maxCacheSizeBytes)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error applying cache settings: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        when (messageEvent.path) {
            WearableProtocol.PATH_PLAYBACK_RESPONSE -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val state = json.decodeFromString<WearPlaybackState>(jsonStr)
                        _phonePlaybackState.value = state
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding playback response: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_PLAYLISTS_RESPONSE -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val payload = json.decodeFromString<WearPlaylistsPayload>(jsonStr)
                        _syncedPlaylists.value = payload.playlists
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding playlists response: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_PLAYLIST_TRACKS_RESPONSE -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val payload = json.decodeFromString<WearPlaylistTracksResponse>(jsonStr)
                        _playlistTracks.value = _playlistTracks.value + (payload.playlistId to payload.tracks)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding playlist tracks response: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_QUICK_MIX_RESPONSE -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val payload = json.decodeFromString<WearQuickMixPayload>(jsonStr)
                        _quickMixTracks.value = payload.tracks
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding quick mix payload: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_STREAM_RESOLVE_RESPONSE -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val response = json.decodeFromString<WearStreamResolveResponse>(jsonStr)
                        WearSyncClient.onStreamResolveResponseReceived(response)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed decoding stream resolve response: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_PING -> {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }
                scope.launch {
                    try {
                        Tasks.await(
                            Wearable.getMessageClient(this@WearDataLayerListenerService)
                                .sendMessage(messageEvent.sourceNodeId, WearableProtocol.PATH_PING_ACK, byteArrayOf())
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to respond to ping: ${e.message}")
                    }
                }
            }
            WearableProtocol.PATH_CACHE_SETTINGS -> {
                val jsonStr = messageEvent.data.toString(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    try {
                        val prefs = json.decodeFromString<WearCachePreferences>(jsonStr)
                        WearApp.instance.cacheManager.setMaxCacheLimit(prefs.maxCacheSizeBytes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update cache cap from message: ${e.message}")
                    }
                }
            }
        }
    }
}
