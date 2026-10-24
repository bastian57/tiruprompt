package com.daftech.tiruprompt.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ============================================================================
// Storage — persistensi lokal TiruPrompt.
//  - API key & preferensi: SharedPreferences (tidak pernah ditulis ke log).
//  - Riwayat analisa: file JSON di internal storage aplikasi.
//  - Cache scrape terakhir per platform: SharedPreferences (JSON).
// ============================================================================

object Storage {

    private const val PREFS = "tiruprompt_prefs"
    private const val HISTORY_FILE = "riwayat_analisa.json"
    private const val MAX_HISTORY = 60

    // ---------------- SharedPreferences ----------------

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadGeminiKey(ctx: Context): String = prefs(ctx).getString("gemini_key", "") ?: ""
    fun saveGeminiKey(ctx: Context, v: String) =
        prefs(ctx).edit().putString("gemini_key", v.trim()).apply()

    fun loadYoutubeKey(ctx: Context): String = prefs(ctx).getString("youtube_key", "") ?: ""
    fun saveYoutubeKey(ctx: Context, v: String) =
        prefs(ctx).edit().putString("youtube_key", v.trim()).apply()

    fun loadModelName(ctx: Context): String =
        prefs(ctx).getString("model_name", "gemini-2.5-flash") ?: "gemini-2.5-flash"

    fun saveModelName(ctx: Context, v: String) =
        prefs(ctx).edit().putString("model_name", v.trim().ifBlank { "gemini-2.5-flash" }).apply()

    fun loadRegion(ctx: Context): String = prefs(ctx).getString("region", "ID") ?: "ID"
    fun saveRegion(ctx: Context, v: String) =
        prefs(ctx).edit().putString("region", v.trim().uppercase().ifBlank { "ID" }).apply()

    fun loadTrendCacheJson(ctx: Context, platform: TrendPlatform): String =
        prefs(ctx).getString("trend_cache_${platform.name}", "") ?: ""

    fun saveTrendCacheJson(ctx: Context, platform: TrendPlatform, json: String) =
        prefs(ctx).edit().putString("trend_cache_${platform.name}", json).apply()

    // ---------------- Serialisasi ----------------

    fun analysisToJson(r: AnalysisResult): JSONObject {
        val scenes = JSONArray()
        for (s in r.scenes) {
            scenes.put(JSONObject()
                .put("index", s.index).put("startSec", s.startSec).put("endSec", s.endSec)
                .put("prompt", s.prompt).put("negative", s.negative).put("camera", s.camera)
                .put("lighting", s.lighting).put("audio", s.audio).put("dialog", s.dialog)
                .put("transition", s.transition).put("subshots", s.subshots))
        }
        return JSONObject()
            .put("title", r.title).put("link", r.link).put("youtubeId", r.youtubeId)
            .put("durationSec", r.durationSec).put("characterLock", r.characterLock)
            .put("styleSummary", r.styleSummary).put("mode", r.mode.name)
            .put("targetEngine", r.targetEngine.name).put("source", r.source)
            .put("createdAtMs", r.createdAtMs).put("scenes", scenes)
    }

    fun analysisFromJson(j: JSONObject): AnalysisResult {
        val scenes = mutableListOf<ScenePrompt>()
        val arr = j.optJSONArray("scenes") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            scenes.add(ScenePrompt(
                index = s.optInt("index", i), startSec = s.optInt("startSec"),
                endSec = s.optInt("endSec"), prompt = s.optString("prompt"),
                negative = s.optString("negative"), camera = s.optString("camera"),
                lighting = s.optString("lighting"), audio = s.optString("audio"),
                dialog = s.optString("dialog"), transition = s.optString("transition"),
                subshots = s.optString("subshots")
            ))
        }
        return AnalysisResult(
            title = j.optString("title"), link = j.optString("link"),
            youtubeId = j.optString("youtubeId"), durationSec = j.optInt("durationSec"),
            characterLock = j.optString("characterLock"), styleSummary = j.optString("styleSummary"),
            mode = runCatching { AnalyseMode.valueOf(j.optString("mode")) }.getOrDefault(AnalyseMode.CLONE_STRUKTUR),
            targetEngine = runCatching { TargetEngine.valueOf(j.optString("targetEngine")) }.getOrDefault(TargetEngine.UNIVERSAL),
            source = j.optString("source"), createdAtMs = j.optLong("createdAtMs"), scenes = scenes
        )
    }

    fun trendVideoToJson(v: TrendVideo): JSONObject = JSONObject()
        .put("videoId", v.videoId).put("title", v.title).put("channel", v.channel)
        .put("url", v.url).put("thumbnailUrl", v.thumbnailUrl).put("views", v.views)
        .put("likes", v.likes).put("comments", v.comments)
        .put("publishedAtMs", v.publishedAtMs).put("ageHours", v.ageHours)
        .put("hashtags", JSONArray(v.hashtags)).put("platform", v.platform.name)

    fun trendVideoFromJson(j: JSONObject): TrendVideo {
        val tags = mutableListOf<String>()
        val a = j.optJSONArray("hashtags") ?: JSONArray()
        for (i in 0 until a.length()) tags.add(a.optString(i))
        return TrendVideo(
            videoId = j.optString("videoId"), title = j.optString("title"),
            channel = j.optString("channel"), url = j.optString("url"),
            thumbnailUrl = j.optString("thumbnailUrl"), views = j.optLong("views"),
            likes = j.optLong("likes"), comments = j.optLong("comments"),
            publishedAtMs = j.optLong("publishedAtMs"), ageHours = j.optDouble("ageHours", 24.0),
            hashtags = tags,
            platform = runCatching { TrendPlatform.valueOf(j.optString("platform")) }
                .getOrDefault(TrendPlatform.YOUTUBE)
        )
    }

    fun trendListToJson(list: List<TrendVideo>): String {
        val arr = JSONArray()
        for (v in list) arr.put(trendVideoToJson(v))
        return arr.toString()
    }

    fun trendListFromJson(json: String): List<TrendVideo> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<TrendVideo>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(trendVideoFromJson(it)) }
            out
        } catch (e: Exception) { emptyList() }
    }

    // ---------------- Riwayat (file internal) ----------------

    private fun historyFile(ctx: Context) = File(ctx.filesDir, HISTORY_FILE)

    fun loadHistory(ctx: Context): MutableList<AnalysisResult> {
        val f = historyFile(ctx)
        if (!f.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(f.readText())
            val out = mutableListOf<AnalysisResult>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { out.add(analysisFromJson(it)) }
            out
        } catch (e: Exception) { mutableListOf() }
    }

    fun saveHistory(ctx: Context, items: List<AnalysisResult>) {
        val arr = JSONArray()
        for (r in items.take(MAX_HISTORY)) arr.put(analysisToJson(r))
        try { historyFile(ctx).writeText(arr.toString()) } catch (e: Exception) { /* penyimpanan penuh dsb. */ }
    }

    fun addToHistory(ctx: Context, r: AnalysisResult) {
        val items = loadHistory(ctx)
        items.add(0, r)
        saveHistory(ctx, items)
    }
}
