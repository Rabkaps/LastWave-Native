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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.lastwave.core.sync.WearCachePreferences
import com.lastwave.core.sync.WearStorageStats

@Composable
fun SettingsScreen(
    stats: WearStorageStats,
    selectedCapBytes: Long,
    onSelectCapBytes: (Long) -> Unit,
    onClearCache: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val usedMb = stats.usedCacheBytes / 1_000_000L
    val maxMb = selectedCapBytes / 1_000_000L
    val freeDiskMb = stats.availableDiskBytes / 1_000_000L

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = "← Back",
                fontSize = 11.sp,
                color = Color(0xFFC6F100),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(bottom = 4.dp)
            )
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E2838))
                    .clickable {
                        runCatching {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "🎧 Bluetooth Audio",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF82B1FF)
                        )
                        Text(
                            text = "Pair watch earbuds",
                            fontSize = 8.sp,
                            color = Color.LightGray
                        )
                    }
                    Text(text = "→", fontSize = 11.sp, color = Color(0xFF82B1FF))
                }
            }
        }

        item {
            Text(
                text = "Storage & Cache",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Storage usage summary
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Cache: $usedMb MB / $maxMb MB",
                        fontSize = 11.sp,
                        color = Color(0xFFC6F100),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Watch Free Disk: $freeDiskMb MB",
                        fontSize = 9.sp,
                        color = if (stats.isCriticallyLowSpace) Color(0xFFFF5252) else Color.Gray
                    )
                }
            }
        }

        // Cache Limit Selector
        item {
            Text(
                text = "Max Cache Limit",
                fontSize = 11.sp,
                color = Color.LightGray,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        val caps = listOf(
            WearCachePreferences.CAP_500_MB to "500 MB",
            WearCachePreferences.CAP_1_GB to "1.0 GB",
            WearCachePreferences.CAP_2_GB to "2.0 GB",
            WearCachePreferences.CAP_4_GB to "4.0 GB"
        )

        items(caps.size) { idx ->
            val (capVal, capLabel) = caps[idx]
            val isSelected = capVal == selectedCapBytes

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF2E3A1C) else Color(0xFF161616))
                    .clickable { onSelectCapBytes(capVal) }
                    .padding(vertical = 6.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = capLabel,
                        fontSize = 11.sp,
                        color = if (isSelected) Color(0xFFC6F100) else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSelected) {
                        Text(text = "✓", fontSize = 11.sp, color = Color(0xFFC6F100))
                    }
                }
            }
        }

        // Clear Cache button
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onClearCache,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF381814),
                    contentColor = Color(0xFFFFB4A2)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Text(text = "Purge Cache", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
