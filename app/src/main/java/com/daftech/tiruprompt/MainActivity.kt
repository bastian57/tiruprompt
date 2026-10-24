package com.daftech.tiruprompt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daftech.tiruprompt.screens.AnalisaScreen
import com.daftech.tiruprompt.screens.RiwayatScreen
import com.daftech.tiruprompt.screens.SettingsScreen
import com.daftech.tiruprompt.screens.TrendScreen
import com.daftech.tiruprompt.ui.TiruPromptTheme
import com.daftech.tiruprompt.ui.TpCyan
import com.daftech.tiruprompt.ui.TpSurface
import com.daftech.tiruprompt.ui.TpTextDim
import com.daftech.tiruprompt.ui.TpViolet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TiruPromptTheme {
                TiruPromptApp()
            }
        }
    }
}

@Composable
fun TiruPromptApp(vm: AppViewModel = viewModel()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TpSurface)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "TiruPrompt",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Video → prompt per scene 10 detik • by DAFTECH",
                    fontSize = 11.sp,
                    color = TpTextDim
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = TpSurface) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = TpCyan,
                    selectedTextColor = TpCyan,
                    indicatorColor = TpViolet.copy(alpha = 0.25f),
                    unselectedIconColor = TpTextDim,
                    unselectedTextColor = TpTextDim
                )
                NavigationBarItem(
                    selected = vm.currentTab == Tab.ANALISA,
                    onClick = { vm.selectTab(Tab.ANALISA) },
                    icon = { Icon(Icons.Filled.Movie, contentDescription = null) },
                    label = { Text("Analisa") },
                    colors = colors
                )
                NavigationBarItem(
                    selected = vm.currentTab == Tab.TREND,
                    onClick = { vm.selectTab(Tab.TREND) },
                    icon = { Icon(Icons.Filled.TrendingUp, contentDescription = null) },
                    label = { Text("Trend") },
                    colors = colors
                )
                NavigationBarItem(
                    selected = vm.currentTab == Tab.RIWAYAT,
                    onClick = { vm.selectTab(Tab.RIWAYAT); vm.refreshHistory() },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text("Riwayat") },
                    colors = colors
                )
                NavigationBarItem(
                    selected = vm.currentTab == Tab.SETTING,
                    onClick = { vm.selectTab(Tab.SETTING) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Setting") },
                    colors = colors
                )
            }
        }
    ) { pad ->
        Box(modifier = Modifier.fillMaxSize().padding(pad)) {
            when (vm.currentTab) {
                Tab.ANALISA -> AnalisaScreen(vm)
                Tab.TREND -> TrendScreen(vm)
                Tab.RIWAYAT -> RiwayatScreen(vm)
                Tab.SETTING -> SettingsScreen(vm)
            }
        }
    }
}
