package com.lastwave.wear.ui

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearCachePreferences
import com.lastwave.wear.WearApp
import com.lastwave.wear.playback.WearPlaybackService
import com.lastwave.wear.sync.WearDataLayerListenerService
import com.lastwave.wear.ui.ambient.AmbientState
import com.lastwave.wear.ui.ambient.LocalAmbientState
import com.lastwave.wear.ui.screens.BrowseScreen
import com.lastwave.wear.ui.screens.LyricsScreen
import com.lastwave.wear.ui.screens.NowPlayingScreen
import com.lastwave.wear.ui.screens.SettingsScreen
import com.lastwave.wear.ui.theme.LastWaveWearTheme
import kotlinx.coroutines.launch

enum class WearScreen {
    NOW_PLAYING,
    BROWSE,
    LYRICS,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val syncClient by lazy { WearApp.instance.syncClient }
    private val cacheManager by lazy { WearApp.instance.cacheManager }

    private var ambientState by mutableStateOf(AmbientState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure WearPlaybackService is running for local playback
        try {
            val serviceIntent = Intent(this, WearPlaybackService::class.java)
            startService(serviceIntent)
        } catch (_: Exception) {}

        setContent {
            LastWaveWearTheme {
                CompositionLocalProvider(LocalAmbientState provides ambientState) {
                    val scope = rememberCoroutineScope()
                    var currentScreen by remember { mutableStateOf(WearScreen.NOW_PLAYING) }
                    var isCompanionMode by remember { mutableStateOf(true) } // Default to companion mode to sync with phone
                    var selectedCapBytes by remember { mutableLongStateOf(WearCachePreferences.DEFAULT_CACHE_CAP_BYTES) }

                    val phoneState by WearDataLayerListenerService.phonePlaybackState.collectAsState()
                    val localState by WearPlaybackService.localPlaybackState.collectAsState()

                    // Automatically request state on launch
                    LaunchedEffect(Unit) {
                        syncClient.requestPlaybackStateFromPhone()
                        syncClient.requestQuickMixFromPhone()
                    }

                    // If phone is actively playing or has active metadata, ensure companion mode
                    LaunchedEffect(phoneState.title, phoneState.isPlaying) {
                        if (phoneState.title.isNotBlank() || phoneState.isPlaying) {
                            isCompanionMode = true
                        }
                    }

                    val activePlaybackState = if (isCompanionMode) phoneState else localState

                    when (currentScreen) {
                        WearScreen.NOW_PLAYING -> {
                            NowPlayingScreen(
                                state = activePlaybackState,
                                isCompanionMode = isCompanionMode,
                                onToggleCompanionMode = { isCompanionMode = !isCompanionMode },
                                onCommand = { action ->
                                    if (isCompanionMode) {
                                        scope.launch {
                                            syncClient.sendCommandToPhone(PlaybackCommand(action = action))
                                        }
                                    } else {
                                        when (action) {
                                            PlaybackCommand.Action.TOGGLE_PLAY_PAUSE -> WearPlaybackService.togglePlayPause()
                                            PlaybackCommand.Action.SKIP_NEXT -> WearPlaybackService.skipNext()
                                            PlaybackCommand.Action.SKIP_PREVIOUS -> WearPlaybackService.skipPrevious()
                                            else -> {}
                                        }
                                    }
                                },
                                onVolumeDelta = { delta ->
                                    if (isCompanionMode) {
                                        scope.launch {
                                            syncClient.sendCommandToPhone(
                                                PlaybackCommand(
                                                    action = PlaybackCommand.Action.ADJUST_VOLUME,
                                                    volumeDelta = delta
                                                )
                                            )
                                        }
                                    } else {
                                        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                        val dir = if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                                        am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0)
                                    }
                                },
                                onOpenBrowse = { currentScreen = WearScreen.BROWSE },
                                onOpenLyrics = { currentScreen = WearScreen.LYRICS },
                                onOpenSettings = { currentScreen = WearScreen.SETTINGS }
                            )
                        }
                        WearScreen.BROWSE -> {
                            BrowseScreen(
                                onPlayTrack = { song ->
                                    isCompanionMode = false // Switch to standalone watch playback
                                    val mediaItem = MediaItem.Builder()
                                        .setMediaId(song.id)
                                        .setUri(song.streamUrl)
                                        .setMediaMetadata(
                                            MediaMetadata.Builder()
                                                .setTitle(song.title)
                                                .setArtist(song.artist)
                                                .setArtworkUri(song.artworkUrl?.let { Uri.parse(it) })
                                                .build()
                                        )
                                        .build()
                                    WearPlaybackService.playTrack(mediaItem)
                                    currentScreen = WearScreen.NOW_PLAYING
                                },
                                onPlayPhoneTrack = { phoneTrack ->
                                    isCompanionMode = true
                                    scope.launch {
                                        syncClient.sendCommandToPhone(
                                            PlaybackCommand(
                                                action = PlaybackCommand.Action.PLAY_TRACK,
                                                songId = phoneTrack.id
                                            )
                                        )
                                    }
                                    currentScreen = WearScreen.NOW_PLAYING
                                },
                                onBack = { currentScreen = WearScreen.NOW_PLAYING }
                            )
                        }
                        WearScreen.LYRICS -> {
                            LyricsScreen(
                                currentPositionMs = activePlaybackState.positionMs,
                                lyrics = emptyList(),
                                onBack = { currentScreen = WearScreen.NOW_PLAYING }
                            )
                        }
                        WearScreen.SETTINGS -> {
                            SettingsScreen(
                                stats = cacheManager.getStorageStats(),
                                selectedCapBytes = selectedCapBytes,
                                onSelectCapBytes = { newCap ->
                                    selectedCapBytes = newCap
                                    cacheManager.setMaxCacheLimit(newCap)
                                },
                                onClearCache = {
                                    cacheManager.clearCache()
                                },
                                onBack = { currentScreen = WearScreen.NOW_PLAYING }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            syncClient.requestPlaybackStateFromPhone()
        }
    }
}
