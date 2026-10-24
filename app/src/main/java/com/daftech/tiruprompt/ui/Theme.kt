package com.daftech.tiruprompt.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palet TiruPrompt: dark charcoal + aksen violet/cyan (nuansa tool video/AI).
val TpBg = Color(0xFF0D0D12)
val TpSurface = Color(0xFF17171F)
val TpSurface2 = Color(0xFF1F1F2A)
val TpViolet = Color(0xFF8B5CF6)
val TpCyan = Color(0xFF22D3EE)
val TpText = Color(0xFFF1F0F5)
val TpTextDim = Color(0xFF9B98A8)
val TpGold = Color(0xFFF59E0B)
val TpRed = Color(0xFFF87171)
val TpGreen = Color(0xFF34D399)

private val DarkScheme = darkColorScheme(
    primary = TpViolet,
    onPrimary = Color.White,
    secondary = TpCyan,
    onSecondary = Color(0xFF062A33),
    background = TpBg,
    onBackground = TpText,
    surface = TpSurface,
    onSurface = TpText,
    surfaceVariant = TpSurface2,
    onSurfaceVariant = TpTextDim,
    error = TpRed
)

@Composable
fun TiruPromptTheme(content: @Composable () -> Unit) {
    // Tool video dipakai malam/gelap: paksa skema gelap premium.
    MaterialTheme(colorScheme = DarkScheme, content = content)
}
