package com.daftech.tiruprompt.engine

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// ============================================================================
// TrendScraper — ambil daftar video trending dari sumber PUBLIK.
//  - YouTube + API key : YouTube Data API v3 (chart mostPopular + search).
//  - YouTube tanpa key : scrape halaman hasil pencarian (ytInitialData).
//  - TikTok            : coba halaman search (__UNIVERSAL_DATA__) + Creative
//                        Center. TikTok sering memblokir scrape otomatis;
//                        kalau gagal, tampilkan pesan JUJUR — hasil TikTok
//                        TIDAK PERNAH dikarang.
// ============================================================================

object TrendScraper {

    const val TIKTOK_BLOCKED_MSG =
        "TikTok memblokir scrape otomatis dari perangkat ini — tempel link video TikTok langsung di tab Analisa (mode Template) atau coba lagi nanti."

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 13; Redmi Note 10S) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class Outcome {
        data class Ok(val videos: List<TrendVideo>, val note: String) : Outcome()
        data class Fail(val message: String) : Outcome()
    }

    private fun httpGet(url: String, ua: String): String? {
        val req = Request.Builder().url(url)
            .header("User-Agent", ua)
            .header("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            .build()
        return client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }

    // ---------------- YouTube Data API v3 ----------------

    fun scrapeYoutubeApi(apiKey: String, region: String, niche: String): Outcome {
        return try {
            val out = mutableListOf<TrendVideo>()
            // 1) Chart paling populer di region
            val chartUrl = "https://youtube.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
                .addQueryParameter("part", "snippet,statistics")
                .addQueryParameter("chart", "mostPopular")
                .addQueryParameter("regionCode", region.ifBlank { "ID" })
                .addQueryParameter("maxResults", "25")
                .addQueryParameter("key", apiKey)
                .build().toString()
            httpGet(chartUrl, DESKTOP_UA)?.let { out.addAll(parseApiVideoList(JSONObject(it))) }

            // 2) Pencarian per niche (diurutkan jumlah tontonan)
            if (niche.isNotBlank()) {
                val searchUrl = "https://youtube.googleapis.com/youtube/v3/search".toHttpUrl().newBuilder()
                    .addQueryParameter("part", "snippet")
                    .addQueryParameter("type", "video")
                    .addQueryParameter("order", "viewCount")
                    .addQueryParameter("q", niche)
                    .addQueryParameter("regionCode", region.ifBlank { "ID" })
                    .addQueryParameter("maxResults", "25")
                    .addQueryParameter("key", apiKey)
                    .build().toString()
                val searchJson = httpGet(searchUrl, DESKTOP_UA)
                val ids = mutableListOf<String>()
                if (searchJson != null) {
                    val items = JSONObject(searchJson).optJSONArray("items") ?: JSONArray()
                    for (i in 0 until items.length()) {
                        val id = items.optJSONObject(i)?.optJSONObject("id")?.optString("videoId").orEmpty()
                        if (id.isNotBlank()) ids.add(id)
                    }
                }
                if (ids.isNotEmpty()) {
                    val detailUrl = "https://youtube.googleapis.com/youtube/v3/videos".toHttpUrl().newBuilder()
                        .addQueryParameter("part", "snippet,statistics")
                        .addQueryParameter("id", ids.joinToString(","))
                        .addQueryParameter("key", apiKey)
                        .build().toString()
                    httpGet(detailUrl, DESKTOP_UA)?.let { out.addAll(parseApiVideoList(JSONObject(it))) }
                }
            }
            val merged = out.distinctBy { it.videoId }
                .sortedByDescending { it.viralScore }.take(50)
            if (merged.isEmpty()) Outcome.Fail("YouTube API tidak mengembalikan video. Cek API key di Setting atau coba niche lain.")
            else Outcome.Ok(merged, "Sumber: YouTube Data API v3 (resmi)")
        } catch (e: Exception) {
            Outcome.Fail("Scrape YouTube (API) gagal: ${e.message}")
        }
    }

    private fun parseApiVideoList(root: JSONObject): List<TrendVideo> {
        val items = root.optJSONArray("items") ?: return emptyList()
        val out = mutableListOf<TrendVideo>()
        val now = System.currentTimeMillis()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            val sn = item.optJSONObject("snippet") ?: JSONObject()
            val st = item.optJSONObject("statistics") ?: JSONObject()
            val publishedMs = parseIsoMs(sn.optString("publishedAt"))
            val ageH = if (publishedMs > 0) maxOf((now - publishedMs) / 3_600_000.0, 0.1) else 24.0
            val desc = sn.optString("description")
            out.add(
                TrendVideo(
                    videoId = id,
                    title = sn.optString("title"),
                    channel = sn.optString("channelTitle"),
                    url = youtubeWatchUrl(id),
                    thumbnailUrl = sn.optJSONObject("thumbnails")?.optJSONObject("high")
                        ?.optString("url") ?: youtubeThumbUrl(id),
                    views = st.optString("viewCount").toLongOrNull() ?: 0L,
                    likes = st.optString("likeCount").toLongOrNull() ?: 0L,
                    comments = st.optString("commentCount").toLongOrNull() ?: 0L,
                    publishedAtMs = publishedMs,
                    ageHours = ageH,
                    hashtags = Regex("""#[\p{L}0-9_]+""").findAll(desc).map { it.value }.take(8).toList(),
                    platform = TrendPlatform.YOUTUBE
                )
            )
        }
        return out
    }

    private fun parseIsoMs(iso: String): Long {
        if (iso.isBlank()) return 0L
        val fmts = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        for (f in fmts) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(iso)?.time ?: 0L
            } catch (e: Exception) { /* coba format berikutnya */ }
        }
        return 0L
    }

    // ---------------- YouTube Web (tanpa API key) ----------------

    fun scrapeYoutubeWeb(niche: String): Outcome {
        return try {
            val q = niche.ifBlank { "trending" }
            val url = "https://www.youtube.com/results".toHttpUrl().newBuilder()
                .addQueryParameter("search_query", q)
                .addQueryParameter("sp", "CAMSAhAB") // urutkan view count (best effort)
                .build().toString()
            val html = httpGet(url, DESKTOP_UA)
                ?: return Outcome.Fail("Halaman pencarian YouTube tidak bisa diambil. Cek koneksi, atau isi YouTube API key di Setting untuk jalur resmi.")
            val marker = html.indexOf("ytInitialData")
            if (marker < 0) return Outcome.Fail("Format halaman YouTube berubah (ytInitialData tidak ditemukan). Isi YouTube API key di Setting untuk jalur resmi.")
            val brace = html.indexOf('{', marker)
            val jsonText = extractBalancedJson(html, brace)
                ?: return Outcome.Fail("Gagal membaca data pencarian YouTube. Coba lagi atau pakai jalur API key.")
            val root = JSONObject(jsonText)
            val videos = mutableListOf<TrendVideo>()
            walkJson(root) { obj ->
                val vr = obj.optJSONObject("videoRenderer") ?: return@walkJson
                parseVideoRenderer(vr)?.let { videos.add(it) }
            }
            val merged = videos.distinctBy { it.videoId }
                .sortedByDescending { it.viralScore }.take(50)
            if (merged.isEmpty()) Outcome.Fail("Tidak ada video terbaca dari halaman YouTube. Coba kata kunci lain, atau pakai jalur API key.")
            else Outcome.Ok(merged, "Sumber: halaman pencarian YouTube (tanpa API key, data terbatas: likes/komen tidak tersedia)")
        } catch (e: Exception) {
            Outcome.Fail("Scrape YouTube (web) gagal: ${e.message}")
        }
    }

    private fun parseVideoRenderer(vr: JSONObject): TrendVideo? {
        val id = vr.optString("videoId")
        if (id.isBlank()) return null
        val title = readRunsText(vr.optJSONObject("title"))
            .ifBlank { vr.optJSONObject("title")?.optString("simpleText").orEmpty() }
        if (title.isBlank()) return null
        val viewText = vr.optJSONObject("viewCountText")?.optString("simpleText")
            .orEmpty().ifBlank { readRunsText(vr.optJSONObject("viewCountText")) }
        val ageText = vr.optJSONObject("publishedTimeText")?.optString("simpleText").orEmpty()
        val channel = readRunsText(vr.optJSONObject("ownerText"))
            .ifBlank { readRunsText(vr.optJSONObject("longBylineText")) }
        val thumbs = vr.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
        val thumb = if (thumbs != null && thumbs.length() > 0)
            thumbs.optJSONObject(thumbs.length() - 1)?.optString("url").orEmpty() else youtubeThumbUrl(id)
        return TrendVideo(
            videoId = id, title = title, channel = channel,
            url = youtubeWatchUrl(id), thumbnailUrl = thumb,
            views = parseCountText(viewText),
            ageHours = if (ageText.isBlank()) 24.0 else parseRelativeAgeHours(ageText),
            platform = TrendPlatform.YOUTUBE
        )
    }

    private fun readRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val runs = obj.optJSONArray("runs") ?: return obj.optString("simpleText")
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

    // ---------------- TikTok ----------------

    fun scrapeTikTok(niche: String): Outcome {
        val videos = mutableListOf<TrendVideo>()
        var hashtagInfo = ""
        // Percobaan 1: halaman pencarian TikTok (data tertanam __UNIVERSAL_DATA__)
        try {
            val q = niche.ifBlank { "viral" }
            val url = "https://www.tiktok.com/search/video".toHttpUrl().newBuilder()
                .addQueryParameter("q", q).build().toString()
            val html = httpGet(url, MOBILE_UA)
            if (html != null) {
                val marker = html.indexOf("__UNIVERSAL_DATA__")
                if (marker >= 0) {
                    val brace = html.indexOf('{', marker)
                    val jsonText = extractBalancedJson(html, brace)
                    if (jsonText != null) {
                        walkJson(JSONObject(jsonText)) { obj ->
                            parseTikTokItem(obj)?.let { videos.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) { /* lanjut ke Creative Center */ }

        // Percobaan 2 (selalu dicoba): Creative Center — hashtag trending.
        try {
            val cc = httpGet(
                "https://ads.tiktok.com/business/creativecenter/inspiration/popular/hashtag/pc/en",
                DESKTOP_UA
            )
            if (cc != null) {
                val tags = Regex("""hashtag_?[Nn]ame\\?"?\s*:\s*\\?"([^"\\]{2,40})""")
                    .findAll(cc).map { it.groupValues[1] }.distinct().take(10).toList()
                if (tags.isNotEmpty()) hashtagInfo = tags.joinToString(" ") { "#$it" }
            }
        } catch (e: Exception) { /* abaikan, pesan jujur di bawah */ }

        val merged = videos.distinctBy { it.videoId }.take(50)
        return if (merged.isEmpty()) {
            Outcome.Fail(
                if (hashtagInfo.isNotBlank())
                    "$TIKTOK_BLOCKED_MSG Hashtag yang lagi naik terbaca dari Creative Center: $hashtagInfo"
                else TIKTOK_BLOCKED_MSG
            )
        } else {
            Outcome.Ok(merged, "Sumber: halaman publik TikTok (data bisa tidak lengkap)")
        }
    }

    private fun parseTikTokItem(obj: JSONObject): TrendVideo? {
        // Bentuk item TikTok bervariasi; hanya terima objek yang punya desc + stats.
        val desc = obj.optString("desc")
        if (desc.isBlank()) return null
        val stats = obj.optJSONObject("stats") ?: obj.optJSONObject("statistics") ?: return null
        val id = obj.optString("id").ifBlank { obj.optString("itemId") }
        if (id.isBlank()) return null
        val author = obj.optJSONObject("author")?.optString("uniqueId").orEmpty()
        val createMs = obj.optLong("createTime", 0L).let { if (it in 1..9_999_999_999L) it * 1000 else it }
        val ageH = if (createMs > 0) maxOf((System.currentTimeMillis() - createMs) / 3_600_000.0, 0.1) else 24.0
        return TrendVideo(
            videoId = id,
            title = desc,
            channel = if (author.isNotBlank()) "@$author" else "",
            url = "https://www.tiktok.com/@$author/video/$id",
            views = stats.optLong("playCount", stats.optLong("play_count", 0L)),
            likes = stats.optLong("diggCount", stats.optLong("digg_count", 0L)),
            comments = stats.optLong("commentCount", stats.optLong("comment_count", 0L)),
            publishedAtMs = createMs,
            ageHours = ageH,
            hashtags = Regex("""#[\p{L}0-9_]+""").findAll(desc).map { it.value }.take(8).toList(),
            platform = TrendPlatform.TIKTOK
        )
    }

    // ---------------- Util JSON ----------------

    /** Ambil substring JSON seimbang mulai dari posisi kurung kurawal. */
    fun extractBalancedJson(text: String, startBrace: Int): String? {
        if (startBrace < 0 || startBrace >= text.length || text[startBrace] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        var i = startBrace
        while (i < text.length) {
            val c = text[i]
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return text.substring(startBrace, i + 1)
                    }
                }
            }
            i++
        }
        return null
    }

    /** Telusuri JSONObject/JSONArray secara rekursif, panggil blok tiap objek. */
    fun walkJson(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walkJson(node.opt(keys.next()), visit)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walkJson(node.opt(i), visit)
            }
        }
    }
}
