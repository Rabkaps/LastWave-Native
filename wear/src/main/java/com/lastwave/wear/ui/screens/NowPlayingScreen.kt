package com.lastwave.wear.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.wear.ui.ambient.isAmbientMode
import com.lastwave.wear.ui.rotary.withRotaryHaptics

@Composable
fun NowPlayingScreen(
    state: WearPlaybackState,
    isCompanionMode: Boolean,
    onToggleCompanionMode: () -> Unit,
    onCommand: (PlaybackCommand.Action) -> Unit,
    onVolumeDelta: (Float) -> Unit = {},
    onOpenBrowse: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ambient = isAmbientMode()
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }
    val focusRequester = remember { FocusRequester() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .withRotaryHaptics(context) { delta ->
                // Adjust volume with rotary crown and tactile ticks
                val change = (delta / 1000f)
                volumeLevel = (volumeLevel + change).coerceIn(0f, 1f)
                onVolumeDelta(delta)
            },
        contentAlignment = Alignment.Center
    ) {
        if (ambient) {
            // Low-power Always-On Display (Ambient Mode): Monochrome, no animations
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.title.ifBlank { "LastWave" },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.artist.ifBlank { "Not playing" },
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (state.isPlaying) "▶ Playing" else "⏸ Paused",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        } else {
            // Active Full UI with round watch safe-insets
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top row: Source badge, Quality, Library Browse & Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mode Pill (Phone vs Watch)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isCompanionMode) Color(0xFF2E3A1C) else Color(0xFF1E2838))
                            .clickable { onToggleCompanionMode() }
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isCompanionMode) "PHONE" else "WATCH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isCompanionMode) Color(0xFFC6F100) else Color(0xFF82B1FF)
                        )
                    }

                    // Browse Library Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF202020))
                            .clickable { onOpenBrowse() }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "🎵 BROWSE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC6F100)
                        )
                    }

                    // Settings trigger
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF202020))
                            .clickable { onOpenSettings() }
                            .padding(5.dp)
                    ) {
                        Text(
                            text = "⚙",
                            fontSize = 10.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Middle: Album Art & Track Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        if (state.songId != null || state.title.isNotBlank()) {
                            onOpenLyrics()
                        } else {
                            onOpenBrowse()
                        }
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1A1A1A)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!state.artworkUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = state.artworkUrl,
                                contentDescription = "Album Art",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = "🎵", fontSize = 24.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.title.ifBlank { "No Track Selected" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = if (state.title.isBlank()) "Tap to Browse Music" else state.artist.ifBlank { "LastWave" },
                        fontSize = 10.sp,
                        color = if (state.title.isBlank()) Color(0xFFC6F100) else Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )

                    if (state.qualityBadge.isNotBlank() && state.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = state.qualityBadge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (state.isLossless) Color(0xFFFFB4A2) else Color.Gray
                        )
                    }
                }

                // Bottom: Playback Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.SKIP_PREVIOUS) },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF222222),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "⏮", fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.size(14.dp))

                    FilledIconButton(
                        onClick = {
                            if (state.title.isBlank() && !isCompanionMode) {
                                onOpenBrowse()
                            } else {
                                onCommand(PlaybackCommand.Action.TOGGLE_PLAY_PAUSE)
                            }
                        },
                        modifier = Modifier.size(46.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFFC6F100),
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = if (state.isPlaying) "⏸" else "▶",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.size(14.dp))

                    FilledIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.SKIP_NEXT) },
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color(0xFF222222),
                            contentColor = Color.White
                        )
                    ) {
                        Text(text = "⏭", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
