package com.daftech.tiruprompt.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftech.tiruprompt.AppViewModel
import com.daftech.tiruprompt.engine.ThemeCard
import com.daftech.tiruprompt.engine.TrendPlatform
import com.daftech.tiruprompt.engine.TrendVideo
import com.daftech.tiruprompt.engine.formatCount
import com.daftech.tiruprompt.ui.AsyncThumb
import com.daftech.tiruprompt.ui.TpCyan
import com.daftech.tiruprompt.ui.TpGold
import com.daftech.tiruprompt.ui.TpGreen
import com.daftech.tiruprompt.ui.TpRed
import com.daftech.tiruprompt.ui.TpSurface
import com.daftech.tiruprompt.ui.TpTextDim
import com.daftech.tiruprompt.ui.TpViolet

@Composable
fun TrendScreen(vm: AppViewModel) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Scrape video viral/trending, dikelompokkan jadi tema + pola hook-nya. Tekan BEDAH pada video untuk mengirimnya ke tab Analisa.",
                fontSize = 12.sp, color = TpTextDim
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = vm.trendPlatform == TrendPlatform.YOUTUBE,
                    onClick = { vm.selectTrendPlatform(TrendPlatform.YOUTUBE) },
                    label = { Text("YouTube") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                )
                FilterChip(
                    selected = vm.trendPlatform == TrendPlatform.TIKTOK,
                    onClick = { vm.selectTrendPlatform(TrendPlatform.TIKTOK) },
                    label = { Text("TikTok") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                )
            }
        }
        item {
            OutlinedTextField(
                value = vm.nicheInput,
                onValueChange = { vm.updateNiche(it) },
                label = { Text("Niche / kata kunci (kosongkan = trending umum)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TpViolet, unfocusedBorderColor = TpTextDim.copy(alpha = 0.5f),
                    focusedLabelColor = TpCyan, unfocusedLabelColor = TpTextDim
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Text("Region scrape YouTube: ${vm.region} (ubah di Setting)", fontSize = 11.sp, color = TpTextDim)
        }
        if (vm.trendError.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = TpRed.copy(alpha = 0.15f))) {
                    Text(vm.trendError, color = TpRed, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
                }
            }
        }
        item {
            Button(
                onClick = { vm.runScrape() },
                enabled = !vm.scraping,
                colors = ButtonDefaults.buttonColors(containerColor = TpViolet),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (vm.scraping) {
                    CircularProgressIndicator(color = TpCyan, modifier = Modifier.height(20.dp))
                    Text("  MENARIK DATA…", fontWeight = FontWeight.Bold)
                } else {
                    Text("SCRAPE TRENDING", fontWeight = FontWeight.Bold)
                }
            }
            if (vm.trendNote.isNotBlank()) {
                Text(vm.trendNote, fontSize = 11.sp, color = TpTextDim)
            }
        }
        if (vm.trendThemes.isNotEmpty()) {
            item {
                Text("ANALISA TEMA", fontWeight = FontWeight.Black, color = TpCyan, fontSize = 13.sp)
            }
            items(vm.trendThemes) { theme -> ThemeCardView(theme) }
        }
        if (vm.trendVideos.isNotEmpty()) {
            item {
                Text(
                    "VIDEO (${vm.trendVideos.size}) — urut skor viral",
                    fontWeight = FontWeight.Black, color = TpCyan, fontSize = 13.sp
                )
            }
            items(vm.trendVideos) { video -> TrendVideoCard(vm, video) }
        }
    }
}

@Composable
private fun ThemeCardView(t: ThemeCard) {
    var expanded by remember(t.name) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = TpSurface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row {
                Text("#${t.name}", fontWeight = FontWeight.Black, color = TpViolet, fontSize = 14.sp)
                val statusColor = when (t.status) {
                    "LAGI NAIK" -> TpGreen
                    "RAMAI/JENUH" -> TpGold
                    else -> TpTextDim
                }
                Text("  •  ${t.status}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = statusColor)
                if (t.crossPlatform) {
                    Text("  •  LINTAS PLATFORM", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TpCyan)
                }
            }
            Text(
                "${t.videoCount} video • rata-rata ${formatCount(t.avgViews)} views • velocity ${formatCount(t.avgVelocity.toLong())}/jam",
                fontSize = 11.sp, color = TpTextDim
            )
            Text("Hook: ${t.hookPattern}", fontSize = 12.sp)
            Text("Format: ${t.formatGuess}", fontSize = 12.sp)
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "TUTUP CONTOH VIDEO" else "LIHAT CONTOH VIDEO", fontSize = 11.sp)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (title in t.topVideoTitles) {
                        Text("• $title", fontSize = 11.sp, color = TpTextDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendVideoCard(vm: AppViewModel, v: TrendVideo) {
    Card(colors = CardDefaults.cardColors(containerColor = TpSurface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (v.thumbnailUrl.isNotBlank()) {
                AsyncThumb(v.thumbnailUrl, heightDp = 130)
            }
            Text(v.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (v.channel.isNotBlank()) Text(v.channel, fontSize = 11.sp, color = TpTextDim)
            Text(
                "${formatCount(v.views)} views • ${formatCount(v.velocity.toLong())}/jam • skor ${"%.0f".format(v.viralScore)}" +
                    if (v.likes + v.comments > 0) " • engagement ${"%.1f".format(v.engagement * 100)}%" else "",
                fontSize = 11.sp, color = TpCyan
            )
            if (v.hashtags.isNotEmpty()) {
                Text(v.hashtags.joinToString(" "), fontSize = 11.sp, color = TpViolet)
            }
            Button(
                onClick = { vm.prefillAnalysisFromTrend(v) },
                colors = ButtonDefaults.buttonColors(containerColor = TpCyan, contentColor = TpSurface),
                modifier = Modifier.fillMaxWidth()
            ) { Text("BEDAH JADI PROMPT", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
