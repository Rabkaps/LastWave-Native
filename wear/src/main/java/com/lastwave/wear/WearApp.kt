package com.lastwave.wear

import android.app.Application
import com.lastwave.wear.playback.WearCacheManager
import com.lastwave.wear.sync.WearSyncClient

class WearApp : Application() {

    lateinit var cacheManager: WearCacheManager
        private set

    lateinit var syncClient: WearSyncClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        cacheManager = WearCacheManager(this)
        syncClient = WearSyncClient(this)
    }

    companion object {
        lateinit var instance: WearApp
            private set
    }
}
