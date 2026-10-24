package com.daftech.tiruprompt.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftech.tiruprompt.AppViewModel
import com.daftech.tiruprompt.engine.AnalyseMode
import com.daftech.tiruprompt.engine.AnalysisResult
import com.daftech.tiruprompt.engine.ScenePrompt
import com.daftech.tiruprompt.engine.TargetEngine
import com.daftech.tiruprompt.engine.buildFullText
import com.daftech.tiruprompt.engine.buildSceneText
import com.daftech.tiruprompt.engine.formatTime
import com.daftech.tiruprompt.ui.AsyncThumb
import com.daftech.tiruprompt.ui.TpCyan
import com.daftech.tiruprompt.ui.TpGold
import com.daftech.tiruprompt.ui.TpRed
import com.daftech.tiruprompt.ui.TpSurface
import com.daftech.tiruprompt.ui.TpTextDim
import com.daftech.tiruprompt.ui.TpViolet
import java.io.File

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(ctx, "Tersalin: $label", Toast.LENGTH_SHORT).show()
}

@Composable
private fun FieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TpViolet,
    unfocusedBorderColor = TpTextDim.copy(alpha = 0.5f),
    focusedLabelColor = TpCyan,
    unfocusedLabelColor = TpTextDim
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalisaScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Tempel link video, TiruPrompt membedahnya jadi prompt final per scene 10 detik.",
                fontSize = 12.sp, color = TpTextDim
            )
        }
        item {
            OutlinedTextField(
                value = vm.linkInput,
                onValueChange = { vm.updateLink(it) },
                label = { Text("Link video (YouTube / Shorts / TikTok / Reels)") },
                singleLine = false, minLines = 1, maxLines = 3,
                colors = FieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            Text("Mode tiru", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TpCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = vm.mode == AnalyseMode.CLONE_STRUKTUR,
                    onClick = { vm.selectMode(AnalyseMode.CLONE_STRUKTUR) },
                    label = { Text("Clone Struktur") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                )
                FilterChip(
                    selected = vm.mode == AnalyseMode.GANTI_SUBJEK,
                    onClick = { vm.selectMode(AnalyseMode.GANTI_SUBJEK) },
                    label = { Text("Ganti Subjek") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                )
                FilterChip(
                    selected = vm.mode == AnalyseMode.AMBIL_STYLE,
                    onClick = { vm.selectMode(AnalyseMode.AMBIL_STYLE) },
                    label = { Text("Ambil Style") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                )
            }
        }
        if (vm.mode == AnalyseMode.GANTI_SUBJEK) {
            item {
                OutlinedTextField(
                    value = vm.replacementInput,
                    onValueChange = { vm.updateReplacement(it) },
                    label = { Text("Subjek / produk pengganti (wajib di mode ini)") },
                    minLines = 1, maxLines = 3,
                    colors = FieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        item {
            Text("Mesin video tujuan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TpCyan)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (e in TargetEngine.entries) {
                    FilterChip(
                        selected = vm.targetEngine == e,
                        onClick = { vm.selectTargetEngine(e) },
                        label = { Text(e.name.replace('_', ' ')) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TpViolet.copy(alpha = 0.35f))
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = vm.durationInput,
                    onValueChange = { vm.updateDuration(it) },
                    label = { Text("Durasi (detik)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = FieldColors(),
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Durasi + deskripsi dipakai jalur Template. Link YouTube + Gemini key (Setting) = analisa video asli, durasi terbaca otomatis.",
                fontSize = 11.sp, color = TpTextDim
            )
        }
        item {
            OutlinedTextField(
                value = vm.descriptionInput,
                onValueChange = { vm.updateDescription(it) },
                label = { Text("Deskripsi video (untuk jalur Template)") },
                minLines = 2, maxLines = 5,
                colors = FieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (vm.analysisError.isNotBlank()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = TpRed.copy(alpha = 0.15f))) {
                    Text(vm.analysisError, color = TpRed, fontSize = 12.sp,
                        modifier = Modifier.padding(10.dp))
                }
            }
        }
        item {
            Button(
                onClick = { vm.runAnalysis() },
                enabled = !vm.analysing,
                colors = ButtonDefaults.buttonColors(containerColor = TpViolet),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (vm.analysing) {
                    CircularProgressIndicator(color = TpCyan, modifier = Modifier.height(20.dp))
                    Text("  MENGANALISA…", fontWeight = FontWeight.Bold)
                } else {
                    Text("ANALISA — BEDAH JADI PROMPT", fontWeight = FontWeight.Bold)
                }
            }
            if (vm.analyseStatus.isNotBlank()) {
                Text(vm.analyseStatus, fontSize = 11.sp, color = TpTextDim)
            }
        }
        val result = vm.lastResult
        if (result != null) {
            item { ResultHeader(vm, result) }
            items(result.scenes.size) { i ->
                SceneCard(result, result.scenes[i])
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ResultHeader(vm: AppViewModel, r: AnalysisResult) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = TpSurface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val isTemplate = r.source.startsWith("Template")
            Text(
                "Sumber: ${r.source}",
                fontSize = 11.sp,
                color = if (isTemplate) TpGold else TpCyan,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${r.scenes.size} scene × 10 detik • durasi ${r.durationSec} dtk • ${r.mode.name} • ${r.targetEngine.name}",
                fontSize = 11.sp, color = TpTextDim
            )
            if (r.youtubeId.isNotBlank()) {
                AsyncThumb("https://i.ytimg.com/vi/${r.youtubeId}/hqdefault.jpg")
            }
            if (r.characterLock.isNotBlank()) {
                Text("CHARACTER / PRODUCT LOCK", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TpViolet)
                SelectionContainer { Text(r.characterLock, fontSize = 12.sp) }
                OutlinedButton(onClick = { copyToClipboard(ctx, "Character Lock", r.characterLock) }) {
                    Text("COPY LOCK", fontSize = 11.sp)
                }
            }
            if (r.styleSummary.isNotBlank()) {
                Text("RINGKASAN STYLE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TpViolet)
                Text(r.styleSummary, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { copyToClipboard(ctx, "Semua Prompt", buildFullText(r)) },
                    colors = ButtonDefaults.buttonColors(containerColor = TpCyan, contentColor = TpSurface)
                ) { Text("COPY SEMUA", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                OutlinedButton(onClick = {
                    val text = buildFullText(r)
                    runCatching {
                        val f = File(ctx.filesDir, "export_tiruprompt.txt")
                        f.writeText(text)
                    }
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "TiruPrompt — ${r.title}")
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Export TXT TiruPrompt"))
                }) { Text("EXPORT TXT", fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun SceneCard(r: AnalysisResult, s: ScenePrompt) {
    val ctx = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = TpSurface)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row {
                Text(
                    "Scene ${s.index + 1}",
                    fontWeight = FontWeight.Black, color = TpCyan, fontSize = 13.sp
                )
                Text(
                    "  •  ${formatTime(s.startSec)}–${formatTime(s.endSec)} (${s.endSec - s.startSec} dtk)",
                    fontSize = 12.sp, color = TpTextDim
                )
                if (s.index == 0) {
                    Text("  •  HOOK", fontSize = 11.sp, fontWeight = FontWeight.Black, color = TpGold)
                }
            }
            SelectionContainer { Text(s.prompt, fontSize = 13.sp) }
            DetailRow("Negative", s.negative)
            DetailRow("Kamera", s.camera)
            DetailRow("Lighting", s.lighting)
            DetailRow("Audio", s.audio)
            DetailRow("Dialog", s.dialog)
            DetailRow("Sub-shot", s.subshots)
            DetailRow("Transisi", s.transition)
            OutlinedButton(onClick = { copyToClipboard(ctx, "Scene ${s.index + 1}", buildSceneText(r, s)) }) {
                Text("COPY SCENE INI", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row {
        Text("$label: ", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TpTextDim)
        Text(value, fontSize = 11.sp, color = TpTextDim)
    }
}
