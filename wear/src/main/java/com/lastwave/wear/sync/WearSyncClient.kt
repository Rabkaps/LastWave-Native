package com.lastwave.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearPlaylistTracksRequest
import com.lastwave.core.sync.WearStreamResolveRequest
import com.lastwave.core.sync.WearStreamResolveResponse
import com.lastwave.core.sync.WearableProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "WearSyncClient"

class WearSyncClient(
    private val context: Context
) {
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)
    private val nodeClient = Wearable.getNodeClient(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val dataClient = Wearable.getDataClient(context)

    /**
     * Loads locally cached DataItems instantly from Wearable DataClient storage without network wait.
     */
    suspend fun loadLocalCachedDataItems() = withContext(Dispatchers.IO) {
        runCatching {
            val uriPlaylists = android.net.Uri.parse("wear://*" + WearableProtocol.PATH_SYNCED_PLAYLISTS)
            val dataItemBuffer = Tasks.await(dataClient.getDataItems(uriPlaylists))
            for (item in dataItemBuffer) {
                val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                val jsonStr = dataMap.getString("payload")
                if (!jsonStr.isNullOrBlank()) {
                    val payload = json.decodeFromString<com.lastwave.core.sync.WearPlaylistsPayload>(jsonStr)
                    WearDataLayerListenerService.updateSyncedPlaylists(payload.playlists)
                }
            }
            dataItemBuffer.release()
        }

        runCatching {
            val uriPlayback = android.net.Uri.parse("wear://*" + WearableProtocol.PATH_PLAYBACK_STATE)
            val dataItemBuffer = Tasks.await(dataClient.getDataItems(uriPlayback))
            for (item in dataItemBuffer) {
                val dataMap = com.google.android.gms.wearable.DataMapItem.fromDataItem(item).dataMap
                val jsonStr = dataMap.getString("payload")
                if (!jsonStr.isNullOrBlank()) {
                    val state = json.decodeFromString<com.lastwave.core.sync.WearPlaybackState>(jsonStr)
                    WearDataLayerListenerService.updatePhonePlaybackState(state)
                }
            }
            dataItemBuffer.release()
        }
    }


    /**
     * Resolves connected phone nodes, using CapabilityClient with fallback to NodeClient.
     */
    suspend fun getConnectedPhoneNodes(): Set<Node> = withContext(Dispatchers.IO) {
        try {
            val capabilityInfo = Tasks.await(
                capabilityClient.getCapability(
                    WearableProtocol.CAPABILITY_PHONE_APP,
                    CapabilityClient.FILTER_REACHABLE
                )
            )
            if (capabilityInfo.nodes.isNotEmpty()) {
                return@withContext capabilityInfo.nodes
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query capability: ${e.message}")
        }

        // Fallback: Check all directly connected companion nodes (typically the paired phone)
        try {
            val allNodes = Tasks.await(nodeClient.connectedNodes)
            allNodes.toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get connected nodes: ${e.message}")
            emptySet()
        }
    }

    /**
     * Dispatches PlaybackCommand to connected phone
     */
    suspend fun sendCommandToPhone(command: PlaybackCommand): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) {
                Log.w(TAG, "sendCommandToPhone: No connected phone found")
                return@withContext false
            }

            val payload = json.encodeToString(command).toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_COMMAND, payload))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendCommandToPhone failed: ${e.message}")
            false
        }
    }

    /**
     * Requests immediate playback state synchronization from paired phone
     */
    suspend fun requestPlaybackStateFromPhone(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_PLAYBACK_REQUEST, byteArrayOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestPlaybackStateFromPhone failed: ${e.message}")
            false
        }
    }

    /**
     * Requests dynamic Quick Mix and library tracks from paired phone
     */
    suspend fun requestQuickMixFromPhone(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_QUICK_MIX_REQUEST, byteArrayOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestQuickMixFromPhone failed: ${e.message}")
            false
        }
    }

    /**
     * Requests synced Playlists & Radios chosen on the phone app
     */
    suspend fun requestPlaylistsFromPhone(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_PLAYLISTS_REQUEST, byteArrayOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestPlaylistsFromPhone failed: ${e.message}")
            false
        }
    }

    /**
     * Requests tracks for a specific playlist from the phone
     */
    suspend fun requestPlaylistTracksFromPhone(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            val payload = json.encodeToString(WearPlaylistTracksRequest(playlistId)).toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_PLAYLIST_TRACKS_REQUEST, payload))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestPlaylistTracksFromPhone failed: ${e.message}")
            false
        }
    }

    /**
     * Commands phone to play an entire playlist
     */
    suspend fun playPlaylistOnPhone(playlistId: String): Boolean = sendCommandToPhone(
        PlaybackCommand(
            action = PlaybackCommand.Action.PLAY_PLAYLIST,
            playlistId = playlistId
        )
    )

    /**
     * Commands phone to start a specific radio or mix
     */
    suspend fun playRadioOnPhone(radioId: String): Boolean = sendCommandToPhone(
        PlaybackCommand(
            action = PlaybackCommand.Action.PLAY_RADIO,
            radioId = radioId
        )
    )

    /**
     * Sends diagnostic ping to phone and awaits delivery
     */
    suspend fun sendPing(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_PING, byteArrayOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "sendPing failed: ${e.message}")
            false
        }
    }

    /**
     * Requests auth credentials from paired phone
     */
    /**
     * Sends request to phone to resolve a playable HTTP/HTTPS stream URL for [songId].
     */
    suspend fun requestStreamResolveFromPhone(
        songId: String,
        title: String = "",
        artist: String = "",
        timeoutMs: Long = 8000L
    ): WearStreamResolveResponse? = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No phone node connected to resolve stream for $songId")
                return@withContext null
            }

            val req = WearStreamResolveRequest(songId = songId, title = title, artist = artist)
            val payload = json.encodeToString(req).toByteArray(Charsets.UTF_8)
            val deferred = kotlinx.coroutines.CompletableDeferred<WearStreamResolveResponse>()
            pendingStreamResolutions[songId] = deferred

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_STREAM_RESOLVE_REQUEST, payload))
            }

            kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
                deferred.await()
            }.also {
                pendingStreamResolutions.remove(songId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "requestStreamResolveFromPhone failed for $songId: ${e.message}")
            pendingStreamResolutions.remove(songId)
            null
        }
    }

    companion object {
        val pendingStreamResolutions = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<WearStreamResolveResponse>>()

        fun onStreamResolveResponseReceived(response: WearStreamResolveResponse) {
            pendingStreamResolutions[response.songId]?.complete(response)
        }
    }

    suspend fun requestAuthFromPhone(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = getConnectedPhoneNodes()
            if (nodes.isEmpty()) return@withContext false

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, WearableProtocol.PATH_AUTH_REQUEST, byteArrayOf()))
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "requestAuthFromPhone failed: ${e.message}")
            false
        }
    }
}
