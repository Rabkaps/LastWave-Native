package com.lastwave.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.lastwave.core.sync.WearTrackItem
import com.lastwave.wear.WearApp
import com.lastwave.wear.sync.WearDataLayerListenerService
import com.lastwave.wear.ui.rotary.withRotaryHaptics
import kotlinx.coroutines.launch

data class WatchSongItem(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val artworkUrl: String? = null,
    val isLossless: Boolean = false,
    val qualityBadge: String = "LOSSLESS FLAC"
)

@Composable
fun BrowseScreen(
    onPlayTrack: (WatchSongItem) -> Unit,
    onPlayPhoneTrack: (WearTrackItem) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Phone Queue") }

    val phoneTracks by WearDataLayerListenerService.quickMixTracks.collectAsState()

    // Request fresh Quick Mix from phone whenever BrowseScreen is opened
    LaunchedEffect(Unit) {
        scope.launch {
            WearApp.instance.syncClient.requestQuickMixFromPhone()
        }
    }

    // High-fidelity lossless acoustic & orchestral master recordings for offline watch playback
    val localLosslessTracks = remember {
        listOf(
            WatchSongItem(
                id = "lossless_vivaldi_spring",
                title = "Spring: I. Allegro",
                artist = "Antonio Vivaldi",
                streamUrl = "https://ia800501.us.archive.org/34/items/VivaldiTheFourSeasons_201607/01_Vivaldi_Spring_mvt_1_Allegro.mp3",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b9/Antonio_Vivaldi_portrait_1723.jpg/320px-Antonio_Vivaldi_portrait_1723.jpg",
                isLossless = true,
                qualityBadge = "24-BIT / 96k"
            ),
            WatchSongItem(
                id = "lossless_beethoven_5",
                title = "Symphony No. 5: I. Allegro con brio",
                artist = "Ludwig van Beethoven",
                streamUrl = "https://ia800301.us.archive.org/27/items/BeethovenSymphonyNo.5_201606/01_Beethoven_Symphony_No_5_mvt_1.mp3",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6f/Beethoven.jpg/320px-Beethoven.jpg",
                isLossless = true,
                qualityBadge = "24-BIT / 192k"
            ),
            WatchSongItem(
                id = "lossless_debussy_clair",
                title = "Clair de Lune",
                artist = "Claude Debussy",
                streamUrl = "https://ia800206.us.archive.org/15/items/ClairDeLune_201606/Claude_Debussy_Clair_de_lune.mp3",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/d/de/Claude_Debussy_atelier_Nadar.jpg/320px-Claude_Debussy_atelier_Nadar.jpg",
                isLossless = true,
                qualityBadge = "16-BIT / 44.1k"
            ),
            WatchSongItem(
                id = "lossless_chopin_nocturne",
                title = "Nocturne in E-flat Major, Op. 9 No. 2",
                artist = "Frédéric Chopin",
                streamUrl = "https://ia800500.us.archive.org/19/items/ChopinNocturneOp.9No.2/Chopin_Nocturne_Op_9_No_2.mp3",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Frederic_Chopin_photo.jpeg/320px-Frederic_Chopin_photo.jpeg",
                isLossless = true,
                qualityBadge = "24-BIT / 96k"
            ),
            WatchSongItem(
                id = "lossless_bach_cello",
                title = "Cello Suite No. 1: I. Prélude",
                artist = "Johann Sebastian Bach",
                streamUrl = "https://ia800300.us.archive.org/11/items/BachCelloSuiteNo1InGMajor/01_Prelude.mp3",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/6a/Johann_Sebastian_Bach.jpg/320px-Johann_Sebastian_Bach.jpg",
                isLossless = true,
                qualityBadge = "FLAC STUDIO"
            ),
            WatchSongItem(
                id = "lossless_demo_flac_sample",
                title = "Studio Acoustic Reference",
                artist = "Lossless Audio Lab",
                streamUrl = "https://raw.githubusercontent.com/rafaelreis-hotmart/Audio-Sample-files/master/sample.flac",
                artworkUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/2/21/Speaker_Icon.svg/320px-Speaker_Icon.svg.png",
                isLossless = true,
                qualityBadge = "FLAC BIT-PERFECT"
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .withRotaryHaptics(context) { /* Rotary haptics on wrist */ },
        contentAlignment = Alignment.Center
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header / Back
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "← Back",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC6F100),
                        modifier = Modifier.clickable { onBack() }
                    )
                    Text(
                        text = "Music Library",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.LightGray
                    )
                }
            }

            // Categories row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    listOf("Phone Queue", "Watch Masters").forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFFC6F100) else Color(0xFF1E1E1E))
                                .clickable {
                                    selectedCategory = cat
                                    if (cat == "Phone Queue") {
                                        scope.launch {
                                            WearApp.instance.syncClient.requestQuickMixFromPhone()
                                        }
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = cat,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.LightGray
                            )
                        }
                    }
                }
            }

            if (selectedCategory == "Phone Queue") {
                if (phoneTracks.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp, horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No Phone Tracks",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Play tracks or queue songs on your phone, or tap refresh below.",
                                fontSize = 9.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF2E3A1C))
                                    .clickable {
                                        scope.launch {
                                            WearApp.instance.syncClient.requestQuickMixFromPhone()
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🔄 Sync Phone Queue",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC6F100)
                                )
                            }
                        }
                    }
                } else {
                    items(phoneTracks) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF161616))
                                .clickable { onPlayPhoneTrack(track) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF282828)),
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
                                    Text(text = "📱", fontSize = 16.sp)
                                }
                            }

                            Spacer(modifier = Modifier.size(8.dp))

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = track.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist.ifBlank { "Phone Player" },
                                    fontSize = 9.sp,
                                    color = Color.LightGray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF262626))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (track.isLossless) "FLAC" else "PHONE",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (track.isLossless) Color(0xFFC6F100) else Color(0xFF82B1FF)
                                )
                            }
                        }
                    }
                }
            } else {
                // Standalone Watch Lossless Tracks
                items(localLosslessTracks) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF161616))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF282828)),
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
                                Text(text = "🎵", fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.size(8.dp))

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = track.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                fontSize = 9.sp,
                                color = Color.LightGray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF262626))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "FLAC",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC6F100)
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
