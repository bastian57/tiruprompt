package com.daftech.tiruprompt.engine

import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max

// ============================================================================
// Model data TiruPrompt. File ini MURNI Kotlin (tanpa import Android) supaya
// logika intinya bisa dikompilasi & dites di JVM biasa.
// ============================================================================

enum class AnalyseMode { CLONE_STRUKTUR, GANTI_SUBJEK, AMBIL_STYLE }

enum class TargetEngine { UNIVERSAL, VEO, KLING, WAN, MUSE_VIDEO }

enum class TrendPlatform { YOUTUBE, TIKTOK }

data class ScenePrompt(
    var index: Int = 0,
    var startSec: Int = 0,
    var endSec: Int = 0,
    var prompt: String = "",
    var negative: String = "",
    var camera: String = "",
    var lighting: String = "",
    var audio: String = "",
    var dialog: String = "",
    var transition: String = "",
    var subshots: String = ""
)

data class AnalysisResult(
    var title: String = "",
    var link: String = "",
    var youtubeId: String = "",
    var durationSec: Int = 0,
    var characterLock: String = "",
    var styleSummary: String = "",
    var mode: AnalyseMode = AnalyseMode.CLONE_STRUKTUR,
    var targetEngine: TargetEngine = TargetEngine.UNIVERSAL,
    var source: String = "",
    var createdAtMs: Long = 0L,
    var scenes: MutableList<ScenePrompt> = mutableListOf()
)

data class TrendVideo(
    var videoId: String = "",
    var title: String = "",
    var channel: String = "",
    var url: String = "",
    var thumbnailUrl: String = "",
    var views: Long = 0L,
    var likes: Long = 0L,
    var comments: Long = 0L,
    var publishedAtMs: Long = 0L,
    var ageHours: Double = 0.0,
    var hashtags: List<String> = emptyList(),
    var platform: TrendPlatform = TrendPlatform.YOUTUBE
) {
    val velocity: Double get() = computeVelocity(views, ageHours)
    val engagement: Double
        get() = if (views > 0) (likes + comments).toDouble() / views.toDouble() else 0.0
    val viralScore: Double get() = computeViralScore(views, velocity, engagement)
}

data class ThemeCard(
    var name: String = "",
    var videoCount: Int = 0,
    var avgViews: Long = 0L,
    var totalViews: Long = 0L,
    var avgVelocity: Double = 0.0,
    var topVideoTitles: List<String> = emptyList(),
    var hookPattern: String = "",
    var formatGuess: String = "",
    var status: String = "",
    var crossPlatform: Boolean = false
)

// ============================================================================
// Fungsi murni — TANPA dependensi Android/org.json.
// ============================================================================

/** Jumlah scene 10-detik untuk durasi tertentu (ceil). Durasi <=0 -> 0. */
fun sceneCountForDuration(durationSec: Int): Int =
    if (durationSec <= 0) 0 else ceil(durationSec / 10.0).toInt()

/**
 * Normalisasi cap waktu scene: scene i mulai di i*10, berakhir min(start+10,
 * durationSec) bila durationSec > 0, selain itu start+10.
 */
fun normalizeSceneTimestamps(scenes: MutableList<ScenePrompt>, durationSec: Int): MutableList<ScenePrompt> {
    for (i in scenes.indices) {
        val start = i * 10
        scenes[i].index = i
        scenes[i].startSec = start
        scenes[i].endSec = if (durationSec > 0) minOf(start + 10, durationSec) else start + 10
    }
    return scenes
}

/** Format detik -> "mm:ss". */
fun formatTime(totalSec: Int): String {
    val s = max(0, totalSec)
    return "%02d:%02d".format(s / 60, s % 60)
}

/** Format angka besar: 1234567 -> "1,2 jt", 45000 -> "45 rb", 900 -> "900". */
fun formatCount(n: Long): String = when {
    n >= 1_000_000_000L -> "%.1f M".format(n / 1_000_000_000.0).replace('.', ',')
    n >= 1_000_000L -> "%.1f jt".format(n / 1_000_000.0).replace('.', ',')
    n >= 1_000L -> "%.1f rb".format(n / 1_000.0).replace('.', ',')
    else -> n.toString()
}

/** Ekstrak videoId YouTube dari URL watch / youtu.be / shorts / embed / live. */
fun extractYoutubeId(url: String): String? {
    val u = url.trim()
    if (u.isEmpty()) return null
    // ID polos 11 karakter
    if (Regex("^[A-Za-z0-9_-]{11}$").matches(u)) return u
    val patterns = listOf(
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
        Regex("""/live/([A-Za-z0-9_-]{11})"""),
        Regex("""/v/([A-Za-z0-9_-]{11})""")
    )
    for (p in patterns) {
        val m = p.find(u)
        if (m != null) return m.groupValues[1]
    }
    return null
}

fun isYoutubeUrl(url: String): Boolean = extractYoutubeId(url) != null

fun youtubeWatchUrl(videoId: String): String = "https://www.youtube.com/watch?v=$videoId"

fun youtubeThumbUrl(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

/** Kecepatan sebaran: views per jam umur video (umur minimal 1 jam). */
fun computeVelocity(views: Long, ageHours: Double): Double =
    if (views <= 0) 0.0 else views.toDouble() / max(ageHours, 1.0)

/**
 * Skor viral 0..~100: gabungan log(views), log(velocity), engagement.
 * views 1 jt + velocity 50 rb/jam + engagement 5% mendekati 90+.
 */
fun computeViralScore(views: Long, velocity: Double, engagement: Double): Double {
    if (views <= 0) return 0.0
    val viewPart = (ln(views.toDouble()) / ln(10_000_000.0)).coerceIn(0.0, 1.0) * 45.0
    val velPart = if (velocity > 0) (ln(velocity) / ln(100_000.0)).coerceIn(0.0, 1.0) * 35.0 else 0.0
    val engPart = (engagement / 0.10).coerceIn(0.0, 1.0) * 20.0
    return viewPart + velPart + engPart
}

/**
 * Parse teks umur relatif ala YouTube/TikTok: "3 hours ago", "2 days ago",
 * "5 jam yang lalu", "1 week ago" -> jam (Double). Tidak dikenal -> 24.
 */
fun parseRelativeAgeHours(text: String): Double {
    val t = text.lowercase()
    val num = Regex("""(\d+(?:[.,]\d+)?)""").find(t)?.groupValues?.get(1)
        ?.replace(',', '.')?.toDoubleOrNull() ?: return 24.0
    return when {
        t.contains("second") || t.contains("detik") -> max(num / 3600.0, 0.05)
        t.contains("minute") || t.contains("menit") -> max(num / 60.0, 0.1)
        t.contains("hour") || t.contains("jam") -> num
        t.contains("day") || t.contains("hari") -> num * 24.0
        t.contains("week") || t.contains("minggu") -> num * 24.0 * 7.0
        t.contains("month") || t.contains("bulan") -> num * 24.0 * 30.0
        t.contains("year") || t.contains("tahun") -> num * 24.0 * 365.0
        else -> 24.0
    }
}

/** Parse teks jumlah: "1.2M views", "345K", "12 rb", "1,5 jt" -> Long. */
fun parseCountText(text: String): Long {
    var t = text.lowercase().replace("views", "").replace("view", "")
        .replace("x ditonton", "").replace("ditonton", "").trim()
    if (t.isEmpty()) return 0L
    var mult = 1.0
    when {
        t.contains("m") && (t.contains("m ") || t.endsWith("m") || Regex("""[\d.,]\s*m""").containsMatchIn(t)) -> { mult = 1_000_000_000.0; t = t.replace("m", "") }
        t.contains("jt") -> { mult = 1_000_000.0; t = t.replace("jt", "") }
        t.contains("juta") -> { mult = 1_000_000.0; t = t.replace("juta", "") }
        t.contains("k") -> { mult = 1_000.0; t = t.replace("k", "") }
        t.contains("rb") -> { mult = 1_000.0; t = t.replace("rb", "") }
        t.contains("ribu") -> { mult = 1_000.0; t = t.replace("ribu", "") }
    }
    t = t.replace(Regex("""[^0-9.,]"""), "").trim()
    if (t.isEmpty()) return 0L
    // "1.234.567" (ribuan titik) vs "1,2" (desimal koma)
    val normalized = when {
        t.count { it == '.' } >= 1 && !t.contains(',') && t.substringAfterLast('.').length == 3 ->
            t.replace(".", "")
        else -> t.replace(".", "").replace(',', '.')
    }
    val v = normalized.toDoubleOrNull() ?: return 0L
    return (v * mult).toLong()
}

/** Susun teks lengkap satu analisa untuk COPY SEMUA / EXPORT TXT. */
fun buildFullText(r: AnalysisResult): String {
    val sb = StringBuilder()
    sb.appendLine("TiruPrompt — by DAFTECH")
    sb.appendLine("Judul: ${r.title}")
    sb.appendLine("Link: ${r.link}")
    sb.appendLine("Sumber analisa: ${r.source}")
    sb.appendLine("Mode: ${r.mode.name} | Mesin tujuan: ${r.targetEngine.name}")
    sb.appendLine("Durasi: ${r.durationSec} detik | Jumlah scene: ${r.scenes.size} (per 10 detik)")
    sb.appendLine()
    if (r.characterLock.isNotBlank()) {
        sb.appendLine("CHARACTER / PRODUCT LOCK:")
        sb.appendLine(r.characterLock)
        sb.appendLine()
    }
    if (r.styleSummary.isNotBlank()) {
        sb.appendLine("RINGKASAN STYLE:")
        sb.appendLine(r.styleSummary)
        sb.appendLine()
    }
    for (s in r.scenes) {
        sb.appendLine("=== SCENE ${s.index + 1} • ${formatTime(s.startSec)}–${formatTime(s.endSec)} ===")
        sb.appendLine("PROMPT: ${s.prompt}")
        if (s.negative.isNotBlank()) sb.appendLine("NEGATIVE: ${s.negative}")
        if (s.camera.isNotBlank()) sb.appendLine("KAMERA: ${s.camera}")
        if (s.lighting.isNotBlank()) sb.appendLine("LIGHTING: ${s.lighting}")
        if (s.audio.isNotBlank()) sb.appendLine("AUDIO: ${s.audio}")
        if (s.dialog.isNotBlank()) sb.appendLine("DIALOG: ${s.dialog}")
        if (s.subshots.isNotBlank()) sb.appendLine("SUB-SHOT: ${s.subshots}")
        if (s.transition.isNotBlank()) sb.appendLine("TRANSISI: ${s.transition}")
        sb.appendLine()
    }
    return sb.toString().trim()
}

fun buildSceneText(r: AnalysisResult, s: ScenePrompt): String {
    val sb = StringBuilder()
    sb.appendLine("Scene ${s.index + 1} • ${formatTime(s.startSec)}–${formatTime(s.endSec)} — ${r.title}")
    if (r.characterLock.isNotBlank()) sb.appendLine("LOCK: ${r.characterLock}")
    sb.appendLine("PROMPT: ${s.prompt}")
    if (s.negative.isNotBlank()) sb.appendLine("NEGATIVE: ${s.negative}")
    if (s.camera.isNotBlank()) sb.appendLine("KAMERA: ${s.camera}")
    if (s.lighting.isNotBlank()) sb.appendLine("LIGHTING: ${s.lighting}")
    if (s.audio.isNotBlank()) sb.appendLine("AUDIO: ${s.audio}")
    if (s.dialog.isNotBlank()) sb.appendLine("DIALOG: ${s.dialog}")
    if (s.subshots.isNotBlank()) sb.appendLine("SUB-SHOT: ${s.subshots}")
    if (s.transition.isNotBlank()) sb.appendLine("TRANSISI: ${s.transition}")
    return sb.toString().trim()
}
