package com.lastwave.core.sync

import kotlinx.serialization.Serializable

/**
 * Shared Wearable Data Layer contract and paths between LastWave Phone and Wear OS.
 */
object WearableProtocol {
    // Message paths
    const val PATH_COMMAND = "/lastwave/playback/command"
    const val PATH_AUTH_REQUEST = "/lastwave/auth/request"
    const val PATH_QUICK_MIX_REQUEST = "/lastwave/library/quick_mix_request"
    const val PATH_QUICK_MIX_RESPONSE = "/lastwave/library/quick_mix_response"
    const val PATH_PLAYBACK_REQUEST = "/lastwave/playback/request_state"
    const val PATH_PLAYBACK_RESPONSE = "/lastwave/playback/response_state"
    const val PATH_PING = "/lastwave/system/ping"
    const val PATH_PING_ACK = "/lastwave/system/ping_ack"
    const val PATH_CACHE_STATS = "/lastwave/storage/cache_stats"
    const val PATH_CACHE_SETTINGS = "/lastwave/storage/set_cap"

    // DataClient Item paths
    const val PATH_PLAYBACK_STATE = "/lastwave/data/playback_state"
    const val PATH_AUTH_DATA = "/lastwave/data/auth_session"
    const val PATH_FAVORITES = "/lastwave/data/favorites"
    const val PATH_SETTINGS = "/lastwave/data/settings"

    // Capabilities
    const val CAPABILITY_PHONE_APP = "lastwave_phone_app"
    const val CAPABILITY_WEAR_APP = "lastwave_wear_app"
}

/**
 * Commands sent from Watch to Phone (or Phone to Watch)
 */
@Serializable
data class PlaybackCommand(
    val action: Action,
    val positionMs: Long? = null,
    val volumeDelta: Float? = null,
    val songId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    enum class Action {
        PLAY,
        PAUSE,
        TOGGLE_PLAY_PAUSE,
        SKIP_NEXT,
        SKIP_PREVIOUS,
        SEEK,
        TOGGLE_LIKE,
        ADJUST_VOLUME,
        PLAY_TRACK,
    }
}

/**
 * Playback state broadcast over Wearable Data Layer
 */
@Serializable
data class WearPlaybackState(
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val qualityBadge: String = "AUDIO",
    val isLossless: Boolean = false,
    val isLiked: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Account sessions synced seamlessly from Phone to Watch
 */
@Serializable
data class WearAuthSession(
    val ytMusicVisitorData: String? = null,
    val ytMusicCookie: String? = null,
    val lastFmUsername: String? = null,
    val lastFmSessionKey: String? = null,
    val syncedAtEpochMs: Long = System.currentTimeMillis()
)

/**
 * Cache limit and storage preferences for the Watch
 */
@Serializable
data class WearCachePreferences(
    val maxCacheSizeBytes: Long = DEFAULT_CACHE_CAP_BYTES, // Default 1.5 GB
    val preferLosslessDownloads: Boolean = false,
    val streamingQuality: String = QUALITY_HIGH // HIGH, LOSSLESS, SAVER
) {
    companion object {
        const val DEFAULT_CACHE_CAP_BYTES = 1_500_000_000L // ~1.5 GB
        const val CAP_500_MB = 500_000_000L
        const val CAP_1_GB = 1_000_000_000L
        const val CAP_2_GB = 2_000_000_000L
        const val CAP_4_GB = 4_000_000_000L

        const val QUALITY_HIGH = "HIGH"
        const val QUALITY_LOSSLESS = "LOSSLESS"
        const val QUALITY_SAVER = "SAVER"
    }
}

/**
 * Storage telemetry for Watch Settings UI
 */
@Serializable
data class WearStorageStats(
    val usedCacheBytes: Long = 0L,
    val maxCacheBytes: Long = WearCachePreferences.DEFAULT_CACHE_CAP_BYTES,
    val availableDiskBytes: Long = 0L,
    val isCriticallyLowSpace: Boolean = false
)

/**
 * Individual track item transferred to Watch for Browse / Quick Mix screens
 */
@Serializable
data class WearTrackItem(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String? = null,
    val durationMs: Long = 0L,
    val streamUrl: String? = null,
    val isLossless: Boolean = false,
    val qualityBadge: String = "LOSSLESS FLAC"
)

/**
 * Payload response for PATH_QUICK_MIX_REQUEST
 */
@Serializable
data class WearQuickMixPayload(
    val tracks: List<WearTrackItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

