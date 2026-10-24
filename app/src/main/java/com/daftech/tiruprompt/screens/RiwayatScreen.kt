package com.daftech.tiruprompt.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.daftech.tiruprompt.ui.TpGold
import com.daftech.tiruprompt.ui.TpRed
import com.daftech.tiruprompt.ui.TpSurface
import com.daftech.tiruprompt.ui.TpTextDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RiwayatScreen(vm: AppViewModel) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Riwayat analisa tersimpan di perangkat ini.", fontSize = 12.sp, color = TpTextDim)
            if (vm.historyItems.isNotEmpty()) {
                TextButton(onClick = { confirmClear = true }) {
                    Text("HAPUS SEMUA", color = TpRed, fontSize = 11.sp)
                }
            }
        }
        if (vm.historyItems.isEmpty()) {
            Text(
                "Belum ada analisa. Jalankan analisa pertama lo di tab Analisa.",
                fontSize = 13.sp, color = TpTextDim, modifier = Modifier.padding(top = 16.dp)
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(vm.historyItems) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = TpSurface)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        val isTemplate = item.source.startsWith("Template")
                        Text(
                            item.source, fontSize = 11.sp,
                            color = if (isTemplate) TpGold else TpTextDim,
                            fontWeight = FontWeight.Bold
                        )
                        val tgl = if (item.createdAtMs > 0)
                            SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id", "ID")).format(Date(item.createdAtMs))
                        else "-"
                        Text(
                            "${item.scenes.size} scene • ${item.targetEngine.name} • $tgl",
                            fontSize = 11.sp, color = TpTextDim
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.openHistoryItem(item) }) {
                                Text("BUKA", fontSize = 11.sp)
                            }
                            TextButton(onClick = { vm.deleteHistoryItem(item) }) {
                                Text("Hapus", color = TpRed, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Hapus semua riwayat?") },
            text = { Text("Semua hasil analisa yang tersimpan di perangkat akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearHistory() }) {
                    Text("HAPUS", color = TpRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Batal") }
            }
        )
    }
}
