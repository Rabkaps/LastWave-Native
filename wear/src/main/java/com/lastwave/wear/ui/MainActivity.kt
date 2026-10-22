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

                    // Automatically load cached local DataItems and request fresh state on launch
                    LaunchedEffect(Unit) {
                        syncClient.loadLocalCachedDataItems()
                        syncClient.requestPlaybackStateFromPhone()
                        syncClient.requestPlaylistsFromPhone()
                        syncClient.requestQuickMixFromPhone()
                    }

                    // If local watch playback starts, switch target to watch
                    LaunchedEffect(localState.isPlaying) {
                        if (localState.isPlaying) {
                            isCompanionMode = false
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
                                            PlaybackCommand.Action.PLAY -> WearPlaybackService.resume()
                                            PlaybackCommand.Action.PAUSE -> WearPlaybackService.pause()
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
                                isCompanionMode = isCompanionMode,
                                onToggleCompanionMode = { isCompanionMode = !isCompanionMode },
                                onPlayTrack = { track ->
                                    if (isCompanionMode) {
                                        scope.launch {
                                            syncClient.sendCommandToPhone(
                                                PlaybackCommand(
                                                    action = PlaybackCommand.Action.PLAY_TRACK,
                                                    songId = track.id
                                                )
                                            )
                                        }
                                    } else {
                                        scope.launch {
                                            if (phoneState.isPlaying) {
                                                syncClient.sendCommandToPhone(PlaybackCommand(action = PlaybackCommand.Action.PAUSE))
                                            }
                                            playTrackOnWatch(track)
                                        }
                                    }
                                    currentScreen = WearScreen.NOW_PLAYING
                                },
                                onPlayPlaylist = { playlistId, tracks ->
                                    if (isCompanionMode) {
                                        scope.launch {
                                            syncClient.playPlaylistOnPhone(playlistId)
                                        }
                                    } else {
                                        scope.launch {
                                            if (phoneState.isPlaying) {
                                                syncClient.sendCommandToPhone(PlaybackCommand(action = PlaybackCommand.Action.PAUSE))
                                            }
                                            val list = tracks.ifEmpty {
                                                WearDataLayerListenerService.playlistTracks.value[playlistId] ?: emptyList()
                                            }
                                            if (list.isNotEmpty()) {
                                                playTrackOnWatch(list.first())
                                            }
                                        }
                                    }
                                    currentScreen = WearScreen.NOW_PLAYING
                                },
                                onPlayRadio = { radioId ->
                                    if (isCompanionMode) {
                                        scope.launch {
                                            syncClient.playRadioOnPhone(radioId)
                                        }
                                    } else {
                                        scope.launch {
                                            if (phoneState.isPlaying) {
                                                syncClient.sendCommandToPhone(PlaybackCommand(action = PlaybackCommand.Action.PAUSE))
                                            }
                                            val quick = WearDataLayerListenerService.quickMixTracks.value
                                            if (quick.isNotEmpty()) {
                                                playTrackOnWatch(quick.first())
                                            }
                                        }
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
            syncClient.loadLocalCachedDataItems()
            syncClient.requestPlaybackStateFromPhone()
            syncClient.requestPlaylistsFromPhone()
        }
    }

    private suspend fun playTrackOnWatch(track: com.lastwave.core.sync.WearTrackItem) {
        try {
            var playableUrl = track.streamUrl
            var mimeType: String? = null
            if (playableUrl.isNullOrBlank() || !playableUrl.startsWith("http")) {
                val response = syncClient.requestStreamResolveFromPhone(
                    songId = track.id,
                    title = track.title,
                    artist = track.artist
                )
                playableUrl = response?.streamUrl
                mimeType = response?.mimeType
            }

            if (!playableUrl.isNullOrBlank()) {
                val mediaItemBuilder = MediaItem.Builder()
                    .setMediaId(track.id)
                    .setUri(playableUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setAlbumTitle(track.album)
                            .setArtworkUri(track.artworkUrl?.let { Uri.parse(it) })
                            .build()
                    )
                if (!mimeType.isNullOrBlank()) {
                    mediaItemBuilder.setMimeType(mimeType)
                }
                WearPlaybackService.playTrack(mediaItemBuilder.build())
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error playing track on watch: ${e.message}", e)
        }
    }
}
