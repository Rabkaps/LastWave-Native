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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
    val config = LocalConfiguration.current
    val ambient = isAmbientMode()
    var volumeLevel by remember { mutableFloatStateOf(0.7f) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Dynamic responsive sizing based on watch physical screen canvas
    val screenDp = config.screenWidthDp.coerceAtLeast(180)
    val artworkSize = (screenDp * 0.23f).dp
    val playBtnSize = (screenDp * 0.20f).dp
    val skipBtnSize = (screenDp * 0.16f).dp
    val dockBtnSize = (screenDp * 0.13f).dp

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    LaunchedEffect(showVolumeOverlay, volumeLevel) {
        if (showVolumeOverlay) {
            delay(1600L)
            showVolumeOverlay = false
        }
    }

    val progress = if (state.durationMs > 0L) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val progressColor = MaterialTheme.colorScheme.primary
    val volumeColor = MaterialTheme.colorScheme.secondary
    val trackBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)

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
        if (!ambient) {
            // 270° Outer Bezel Circular Arc tracing the watch rim
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 3.5.dp.toPx()
                val inset = strokeWidth / 2f + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                // 270° Bezel Track (Starts at 135° = bottom-left, sweeps 270° around top to 45° = bottom-right)
                val startAngle = 135f
                val totalSweep = 270f

                drawArc(
                    color = trackBgColor,
                    startAngle = startAngle,
                    sweepAngle = totalSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )

                if (showVolumeOverlay) {
                    // When rotary is actively turning, bezel displays volume arc
                    val volSweep = totalSweep * volumeLevel
                    if (volSweep > 0f) {
                        drawArc(
                            color = volumeColor,
                            startAngle = startAngle,
                            sweepAngle = volSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }
                } else if (progress > 0f) {
                    // Active Playback Progress Arc along the bezel
                    val progSweep = totalSweep * progress
                    drawArc(
                        color = progressColor,
                        startAngle = startAngle,
                        sweepAngle = progSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        if (ambient) {
            // Low-power Always-On Display (Ambient Mode): Monochrome, high contrast, zero animations
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (screenDp * 0.12f).dp, vertical = (screenDp * 0.12f).dp),
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.isPlaying) "Playing" else "Paused",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        } else {
            // Active Full Wear M3 UI contoured to circular display
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = (screenDp * 0.08f).dp,
                        vertical = (screenDp * 0.05f).dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Arc: Mode Switcher & Favorite Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(top = 2.dp),
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
                            .padding(horizontal = 8.dp, vertical = 2.5.dp)
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
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isCompanionMode) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Like / Favorite button
                    FilledTonalIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.TOGGLE_LIKE) },
                        modifier = Modifier.size(dockBtnSize),
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
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Middle: Responsive Album Art & Track Info
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
                ) {
                    // Artwork Thumbnail
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(artworkSize * 0.45f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = state.title.ifBlank { "LastWave" },
                        style = MaterialTheme.typography.titleSmall.copy(fontSize = (screenDp * 0.052f).sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    )

                    Text(
                        text = if (state.title.isBlank()) "Tap to Browse" else state.artist.ifBlank { "Music" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = (screenDp * 0.040f).sp),
                        color = if (state.title.isBlank()) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.82f)
                    )

                    if (state.qualityBadge.isNotBlank() && state.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = state.qualityBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.isLossless) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Lower: Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.SKIP_PREVIOUS) },
                        modifier = Modifier.size(skipBtnSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_previous),
                            contentDescription = "Previous",
                            modifier = Modifier.size(skipBtnSize * 0.48f)
                        )
                    }

                    Spacer(modifier = Modifier.size((screenDp * 0.045f).dp))

                    FilledIconButton(
                        onClick = {
                            if (state.title.isBlank() && !isCompanionMode) {
                                onOpenBrowse()
                            } else {
                                onCommand(PlaybackCommand.Action.TOGGLE_PLAY_PAUSE)
                            }
                        },
                        modifier = Modifier.size(playBtnSize),
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
                            modifier = Modifier.size(playBtnSize * 0.52f)
                        )
                    }

                    Spacer(modifier = Modifier.size((screenDp * 0.045f).dp))

                    FilledTonalIconButton(
                        onClick = { onCommand(PlaybackCommand.Action.SKIP_NEXT) },
                        modifier = Modifier.size(skipBtnSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_skip_next),
                            contentDescription = "Next",
                            modifier = Modifier.size(skipBtnSize * 0.48f)
                        )
                    }
                }

                // Bottom Circular Dock: Browse on Left, Timestamp/Volume in Center, Settings on Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onOpenBrowse,
                        modifier = Modifier.size(dockBtnSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_browse),
                            contentDescription = "Browse Library",
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    // Center: Volume popup or Playback Timestamp
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (showVolumeOverlay) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_volume),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    text = "${(volumeLevel * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        } else {
                            val posMin = (state.positionMs / 1000) / 60
                            val posSec = (state.positionMs / 1000) % 60
                            val durMin = (state.durationMs / 1000) / 60
                            val durSec = (state.durationMs / 1000) % 60
                            val timeText = if (state.durationMs > 0) {
                                String.format("%02d:%02d / %02d:%02d", posMin, posSec, durMin, durSec)
                            } else "--:-- / --:--"
                            Text(
                                text = timeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.size(dockBtnSize),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_settings),
                            contentDescription = "Settings",
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}
