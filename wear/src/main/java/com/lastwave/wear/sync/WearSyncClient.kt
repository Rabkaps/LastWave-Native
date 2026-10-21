package com.lastwave.wear.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.lastwave.core.sync.PlaybackCommand
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
