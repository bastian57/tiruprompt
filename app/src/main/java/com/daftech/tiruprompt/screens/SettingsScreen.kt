package com.daftech.tiruprompt.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daftech.tiruprompt.AppViewModel
import com.daftech.tiruprompt.ui.TpCyan
import com.daftech.tiruprompt.ui.TpGreen
import com.daftech.tiruprompt.ui.TpTextDim
import com.daftech.tiruprompt.ui.TpViolet

@Composable
fun SettingsScreen(vm: AppViewModel) {
    // Field lokal supaya ngetik tidak memicu simpan per huruf; simpan saat fokus lepas
    // dilakukan sederhana: nilai ViewModel diperbarui lewat tombol status di bawah tiap field
    // (onValueChange langsung update + simpan — key pendek, aman).
    var gemini by remember(vm.geminiKey) { mutableStateOf(vm.geminiKey) }
    var yt by remember(vm.youtubeKey) { mutableStateOf(vm.youtubeKey) }
    var model by remember(vm.modelName) { mutableStateOf(vm.modelName) }
    var region by remember(vm.region) { mutableStateOf(vm.region) }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = TpViolet, unfocusedBorderColor = TpTextDim.copy(alpha = 0.5f),
        focusedLabelColor = TpCyan, unfocusedLabelColor = TpTextDim
    )
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("API KEY", fontWeight = FontWeight.Black, color = TpCyan, fontSize = 13.sp)
        OutlinedTextField(
            value = gemini,
            onValueChange = { gemini = it; vm.updateGeminiKey(it) },
            label = { Text("Gemini API key (analisa video asli)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = yt,
            onValueChange = { yt = it; vm.updateYoutubeKey(it) },
            label = { Text("YouTube Data API key (trend resmi)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = model,
            onValueChange = { model = it; vm.updateModelName(it) },
            label = { Text("Nama model Gemini") },
            singleLine = true,
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = region,
            onValueChange = { region = it.uppercase().take(2); vm.updateRegion(region) },
            label = { Text("Region trend (kode negara, mis. ID / US)") },
            singleLine = true,
            colors = colors,
            modifier = Modifier.fillMaxWidth()
        )
        if (vm.settingsNote.isNotBlank()) {
            Text(vm.settingsNote, fontSize = 11.sp, color = TpGreen)
        }
        Text(
            "Kedua key GRATIS: Gemini key dari Google AI Studio (aistudio.google.com), " +
                "YouTube Data API key dari Google Cloud Console (aktifkan YouTube Data API v3). " +
                "Key hanya disimpan di perangkat ini (SharedPreferences), tidak dikirim ke mana pun " +
                "selain API resmi Google, dan tidak pernah ditulis ke log.",
            fontSize = 12.sp, color = TpTextDim
        )
        Text(
            "Tanpa Gemini key, tab Analisa tetap jalan lewat jalur Template (durasi + deskripsi manual) " +
                "dan hasilnya selalu dilabeli sebagai template — bukan analisa video asli.",
            fontSize = 12.sp, color = TpTextDim
        )
        Text("TiruPrompt v1.0.1 • by DAFTECH", fontSize = 11.sp, color = TpTextDim)
    }
}
