package com.lastwave.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.lastwave.core.sync.WearPlaylistItem
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.wear.R
import com.lastwave.wear.WearApp
import com.lastwave.wear.sync.WearDataLayerListenerService
import com.lastwave.wear.ui.rotary.withRotaryHaptics
import kotlinx.coroutines.launch

@Composable
fun BrowseScreen(
    isCompanionMode: Boolean = true,
    onToggleCompanionMode: () -> Unit = {},
    onPlayTrack: (WearTrackItem) -> Unit = {},
    onPlayPlaylist: (String, List<WearTrackItem>) -> Unit = { _, _ -> },
    onPlayRadio: (String) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Playlists") }
    var activePlaylistDetail by remember { mutableStateOf<WearPlaylistItem?>(null) }

    val syncedPlaylists by WearDataLayerListenerService.syncedPlaylists.collectAsState()
    val playlistTracksMap by WearDataLayerListenerService.playlistTracks.collectAsState()
    val phoneTracks by WearDataLayerListenerService.quickMixTracks.collectAsState()

    val screenDp = config.screenWidthDp.coerceAtLeast(180)
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
        scope.launch {
            WearApp.instance.syncClient.requestPlaylistsFromPhone()
            WearApp.instance.syncClient.requestQuickMixFromPhone()
        }
    }

    LaunchedEffect(Unit) {
        com.lastwave.wear.ui.MainActivity.rotaryDeltaFlow.collect { delta ->
            listState.scrollBy(delta * 140f)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .withRotaryHaptics(context) { delta ->
                scope.launch {
                    listState.scrollBy(delta)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val activePlaylist = activePlaylistDetail
        if (activePlaylist != null) {
            // Drill-down Playlist Details View
            val tracks = playlistTracksMap[activePlaylist.id] ?: emptyList()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (screenDp * 0.05f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top circular screen safe spacer
                item {
                    Spacer(modifier = Modifier.height((screenDp * 0.08f).dp))
                }

                item {
                    // Header with back icon and companion toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = { activePlaylistDetail = null },
                            modifier = Modifier.size((screenDp * 0.14f).dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back",
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCompanionMode) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .clickable { onToggleCompanionMode() }
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = if (isCompanionMode) "PHONE" else "WATCH",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isCompanionMode) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = activePlaylist.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        FilledTonalButton(
                            onClick = { onPlayPlaylist(activePlaylist.id, tracks) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_play),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Play All",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (tracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Loading tracks from phone...",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(tracks) { track ->
                        Card(
                            onClick = { onPlayTrack(track) },
                            modifier = Modifier.fillMaxWidth(0.92f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((screenDp * 0.13f).dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!track.artworkUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = track.artworkUrl,
                                            contentDescription = track.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_tile_music),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.size(7.dp))

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.5.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist.ifBlank { "Track" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom circular screen safe spacer
                item {
                    Spacer(modifier = Modifier.height((screenDp * 0.18f).dp))
                }
            }
        } else {
            // Main Categories View
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = (screenDp * 0.05f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Top circular screen safe spacer
                item {
                    Spacer(modifier = Modifier.height((screenDp * 0.08f).dp))
                }

                // Header / Back
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(
                            onClick = onBack,
                            modifier = Modifier.size((screenDp * 0.14f).dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = "Back to Player",
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCompanionMode) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.primaryContainer
                                )
                                .clickable { onToggleCompanionMode() }
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                text = if (isCompanionMode) "PHONE" else "WATCH",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isCompanionMode) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Categories Row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        listOf("Playlists", "Radios", "Queue").forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        selectedCategory = cat
                                        if (cat == "Playlists" || cat == "Radios") {
                                            scope.launch {
                                                WearApp.instance.syncClient.requestPlaylistsFromPhone()
                                            }
                                        } else if (cat == "Queue") {
                                            scope.launch {
                                                WearApp.instance.syncClient.requestQuickMixFromPhone()
                                            }
                                        }
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                when (selectedCategory) {
                    "Playlists" -> {
                        val playlists = syncedPlaylists.filter { !it.isRadio }
                        if (playlists.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No Playlists Synced",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Open Phone App → Settings → Wear OS to pick playlists.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    FilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                WearApp.instance.syncClient.requestPlaylistsFromPhone()
                                            }
                                        },
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = "Sync Playlists",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            items(playlists) { pl ->
                                Card(
                                    onClick = {
                                        activePlaylistDetail = pl
                                        scope.launch {
                                            WearApp.instance.syncClient.requestPlaylistTracksFromPhone(pl.id)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(0.92f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((screenDp * 0.14f).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!pl.artworkUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = pl.artworkUrl,
                                                    contentDescription = pl.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_tile_music),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.size(7.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = pl.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = pl.subtitle.ifBlank { "${pl.trackCount} tracks" },
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                                .padding(horizontal = 4.5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = pl.source.uppercase(),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Radios" -> {
                        val radios = syncedPlaylists.filter { it.isRadio }
                        if (radios.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No Radios Synced",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Open Phone App → Settings → Wear OS to pick continuous radios.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(radios) { radio ->
                                Card(
                                    onClick = { onPlayRadio(radio.id) },
                                    modifier = Modifier.fillMaxWidth(0.92f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((screenDp * 0.14f).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_radio),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.size(7.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = radio.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = radio.subtitle,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "PLAY",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "Queue" -> {
                        if (phoneTracks.isEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .padding(vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Phone Queue Empty",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Play songs on your phone to view the active queue here.",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(phoneTracks) { track ->
                                Card(
                                    onClick = { onPlayTrack(track) },
                                    modifier = Modifier.fillMaxWidth(0.92f),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size((screenDp * 0.13f).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!track.artworkUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = track.artworkUrl,
                                                    contentDescription = track.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_tile_music),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.size(7.dp))

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontSize = 10.5.sp),
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = track.artist.ifBlank { "Phone Player" },
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom circular screen safe spacer
                item {
                    Spacer(modifier = Modifier.height((screenDp * 0.18f).dp))
                }
            }
        }
    }
}
