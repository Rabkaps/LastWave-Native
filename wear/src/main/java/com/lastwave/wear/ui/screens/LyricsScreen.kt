package com.lastwave.wear.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.lastwave.wear.ui.ambient.isAmbientMode
import com.lastwave.wear.ui.rotary.withRotaryHaptics

data class LyricLine(
    val timeMs: Long,
    val text: String
)

@Composable
fun LyricsScreen(
    currentPositionMs: Long,
    lyrics: List<LyricLine>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ambient = isAmbientMode()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    // Identify active singing lyric line
    val activeIndex = lyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)

    LaunchedEffect(activeIndex) {
        if (lyrics.isNotEmpty() && !ambient) {
            listState.animateScrollToItem(activeIndex)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .withRotaryHaptics(context) { delta ->
                // Manual rotary scroll over lyrics
            },
        contentAlignment = Alignment.Center
    ) {
        if (lyrics.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onBack() }
            ) {
                Text(
                    text = "No Synced Lyrics",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to return",
                    fontSize = 10.sp,
                    color = Color(0xFFC6F100)
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                item {
                    Text(
                        text = "← Back",
                        fontSize = 10.sp,
                        color = Color(0xFFC6F100),
                        modifier = Modifier
                            .clickable { onBack() }
                            .padding(bottom = 12.dp)
                    )
                }

                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeIndex
                    Text(
                        text = line.text,
                        fontSize = if (isActive) 14.sp else 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = when {
                            ambient -> if (isActive) Color.White else Color.DarkGray
                            isActive -> Color(0xFFC6F100)
                            else -> Color.Gray
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
