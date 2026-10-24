package com.daftech.tiruprompt.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Thumbnail async tanpa library gambar: unduh via HttpURLConnection +
 * BitmapFactory di IO dispatcher, cache sederhana di memori proses.
 */
private val thumbCache = mutableMapOf<String, Bitmap>()

@Composable
fun AsyncThumb(url: String, modifier: Modifier = Modifier, heightDp: Int = 150) {
    var bmp by remember(url) { mutableStateOf(thumbCache[url]) }
    LaunchedEffect(url) {
        if (bmp == null && url.isNotBlank()) {
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
            if (loaded != null) {
                thumbCache[url] = loaded
                bmp = loaded
            }
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(TpSurface2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(heightDp.dp)
            )
        } else {
            Text("thumbnail…", color = TpTextDim, fontSize = 11.sp)
        }
    }
}
