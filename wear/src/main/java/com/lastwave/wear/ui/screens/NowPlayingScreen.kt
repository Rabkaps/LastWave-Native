package com.lastwave.wear.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.lastwave.core.sync.PlaybackCommand
import com.lastwave.core.sync.WearPlaybackState
import com.lastwave.wear.R
import com.lastwave.wear.ui.ambient.isAmbientMode
import com.lastwave.wear.ui.rotary.withRotaryHaptics
import kotlinx.coroutines.delay

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
    var showVolumeOverlay by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(showVolumeOverlay, volumeLevel) {
        if (showVolumeOverlay) {
            delay(1500L)
            showVolumeOverlay = false
        }
    }

    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val progressColor = MaterialTheme.colorScheme.primary
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .withRotaryHaptics(context) { delta ->
                val change = (delta / 800f)
                volumeLevel = (volumeLevel + change).coerceIn(0f, 1f)
                showVolumeOverlay = true
                onVolumeDelta(delta)
            },
        contentAlignment = Alignment.Center
    ) {
        if (ambient) {
            // Low-power Always-On Display (Ambient Mode): Monochrome, high contrast, zero animations
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.title.ifBlank { "LastWave" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.artist.ifBlank { "Not playing" },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (state.isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        } else {
            // Active Full Wear M3 UI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: Source Switcher & Favorite Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isCompanionMode) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                            .clickable { onToggleCompanionMode() }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCompanionMode) MaterialTheme.colorScheme.secondary
                                        else MaterialTheme.colorScheme.primary
                                    )
                            )
                            Text(
                                text = if (isCompanionMode) "PHONE" else "WATCH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isCompanionMode) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Like / Favorite button
                    FilledTonalIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.TOGGLE_LIKE) },
                        modifier = Modifier.size(26.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (state.isLiked) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = if (state.isLiked) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart
                            ),
                            contentDescription = if (state.isLiked) "Unlike" else "Like",
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                // Middle: Album Art with Circular Progress Ring & Track Info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable {
                            if (state.songId != null || state.title.isNotBlank()) {
                                onOpenLyrics()
                            } else {
                                onOpenBrowse()
                            }
                        }
                        .padding(vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier.size(76.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Playback Progress Arc around artwork
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 3.dp.toPx()
                            // Track background
                            drawArc(
                                color = trackBgColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth)
                            )
                            // Active progress
                            if (progress > 0f) {
                                drawArc(
                                    color = progressColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                        }

                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                                Icon(
                                    painter = painterResource(R.drawable.ic_tile_music),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.title.ifBlank { "LastWave" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )

                    Text(
                        text = if (state.title.isBlank()) "Tap to Browse" else state.artist.ifBlank { "Music" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.title.isBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )

                    if (state.qualityBadge.isNotBlank() && state.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = state.qualityBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isLossless) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Bottom: Transport Controls & Quick Navigation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Controls Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { onCommand(PlaybackCommand.Action.SKIP_PREVIOUS) },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = "Previous",
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        FilledIconButton(
                            onClick = {
                                if (state.title.isBlank() && !isCompanionMode) {
                                    onOpenBrowse()
                                } else {
                                    onCommand(PlaybackCommand.Action.TOGGLE_PLAY_PAUSE)
                                }
                            },
                            modifier = Modifier.size(50.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                                ),
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.size(12.dp))

                        FilledTonalIconButton(
                            onClick = { onCommand(PlaybackCommand.Action.SKIP_NEXT) },
                            modifier = Modifier.size(38.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = "Next",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Secondary Bar: Browse on Left, Elapsed/Volume in Center, Settings on Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onOpenBrowse,
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_browse),
                                contentDescription = "Browse Library",
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Center: Volume popup or Playback Timestamp
                        Box(contentAlignment = Alignment.Center) {
                            if (showVolumeOverlay) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_volume),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = "${(volumeLevel * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                val posMin = (state.positionMs / 1000) / 60
                                val posSec = (state.positionMs / 1000) % 60
                                val durMin = (state.durationMs / 1000) / 60
                                val durSec = (state.durationMs / 1000) % 60
                                val timeText = if (state.durationMs > 0) {
                                    String.format("%02d:%02d / %02d:%02d", posMin, posSec, durMin, durSec)
                                } else ""
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FilledTonalIconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier.size(28.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_settings),
                                contentDescription = "Settings",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
