package com.lastwave.wear.playback

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.lastwave.core.sync.WearCachePreferences
import com.lastwave.core.sync.WearStorageStats
import java.io.File

@OptIn(UnstableApi::class)
class WearCacheManager(
    private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "lastwave_media_cache")
    private val databaseProvider = StandaloneDatabaseProvider(context)

    @Volatile
    private var currentCapBytes: Long = WearCachePreferences.DEFAULT_CACHE_CAP_BYTES

    @Volatile
    private var simpleCacheInstance: SimpleCache? = null

    @Synchronized
    fun getCache(): SimpleCache {
        val existing = simpleCacheInstance
        if (existing != null) return existing

        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }

        val evictor = LeastRecentlyUsedCacheEvictor(currentCapBytes)
        val created = SimpleCache(cacheDir, evictor, databaseProvider)
        simpleCacheInstance = created
        return created
    }

    /**
     * Reconfigures cache max limit (e.g. 500MB, 1GB, 2GB, 4GB)
     */
    @Synchronized
    fun setMaxCacheLimit(bytes: Long) {
        if (bytes <= 0L || bytes == currentCapBytes) return
        currentCapBytes = bytes

        // Release and rebuild cache with the updated evictor cap
        try {
            simpleCacheInstance?.release()
        } catch (_: Exception) {}
        simpleCacheInstance = null
        getCache()
    }

    /**
     * Purges all cached media files
     */
    @Synchronized
    fun clearCache() {
        try {
            simpleCacheInstance?.release()
        } catch (_: Exception) {}
        simpleCacheInstance = null

        if (cacheDir.exists()) {
            cacheDir.deleteRecursively()
            cacheDir.mkdirs()
        }
        getCache()
    }

    /**
     * Calculates storage statistics including watch internal disk health
     */
    fun getStorageStats(): WearStorageStats {
        val usedBytes = simpleCacheInstance?.cacheSpace ?: computeDirSize(cacheDir)
        val statFs = StatFs(context.filesDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val isCritical = availableBytes < 500_000_000L // < 500 MB remaining on watch

        return WearStorageStats(
            usedCacheBytes = usedBytes,
            maxCacheBytes = currentCapBytes,
            availableDiskBytes = availableBytes,
            isCriticallyLowSpace = isCritical
        )
    }

    private fun computeDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }
}
