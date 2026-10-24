package com.daftech.tiruprompt.engine

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ============================================================================
// GeminiAnalyzer — jalur analisa ASLI. Video YouTube dikirim ke Gemini
// sebagai fileData (fileUri = URL tontonan YouTube), Gemini "menonton"
// videonya dan mengembalikan JSON per scene 10 detik. Tidak ada kunci yang
// pernah ditulis ke log.
// ============================================================================

object GeminiAnalyzer {

    const val SOURCE_LABEL = "Gemini Video"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    sealed class Outcome {
        data class Ok(val result: AnalysisResult) : Outcome()
        data class Fail(val message: String) : Outcome()
    }

    fun instruction(mode: AnalyseMode, target: TargetEngine, replacement: String): String {
        val modeRule = when (mode) {
            AnalyseMode.CLONE_STRUKTUR ->
                "Mode CLONE_STRUKTUR: recreate the video faithfully scene by scene, same subjects, actions, order and pacing."
            AnalyseMode.GANTI_SUBJEK ->
                "Mode GANTI_SUBJEK: replace the main subject/product in EVERY scene prompt with \"$replacement\" while keeping the original structure, camera work, setting logic and pacing."
            AnalyseMode.AMBIL_STYLE ->
                "Mode AMBIL_STYLE: describe the content generically (no specific person/product identity), focus on cinematography, lighting, color, camera movement and pacing only."
        }
        val engineRule = when (target) {
            TargetEngine.VEO ->
                "Target engine Veo: each prompt is a natural-language paragraph, photorealistic wording, and negative must include no text overlay / no subtitles / no watermark style entries."
            TargetEngine.KLING ->
                "Target engine Kling: each prompt is concise, subject and action first, followed by explicit camera directives."
            TargetEngine.WAN ->
                "Target engine Wan: each prompt uses comma-separated keyword style, short descriptive phrases separated by commas."
            TargetEngine.MUSE_VIDEO ->
                "Target engine Muse Video: each prompt is a clean single paragraph, vivid but not overly long."
            TargetEngine.UNIVERSAL ->
                "Target engine universal: each prompt is a clean single paragraph usable in any video generator."
        }
        return """
            You are a video reverse-engineering expert. Watch this entire video and break it down into consecutive 10-second segments (segment 1 = 0-10s, segment 2 = 10-20s, and so on, covering the WHOLE video, ceil(duration/10) segments).
            $modeRule
            $engineRule
            Segment 1 must focus on the HOOK: exactly what grabs attention in the first seconds.
            Every prompt must be in English and highly detailed: subject appearance + action + setting + lighting + camera movement + visual style.
            Also produce one characterLock: a single consistent description of the main subject (face, hair, outfit) or product (shape, color, branding position) that stays identical across all segments, so a generator can keep continuity.
            Respond with ONLY a JSON object, no markdown, no commentary, with exactly this shape:
            {"title": string, "durationSec": int, "characterLock": string, "styleSummary": string, "scenes": [{"prompt": string, "negative": string, "camera": string, "lighting": string, "audio": string, "dialog": string, "transition": string, "subshots": string}]}
            Rules for fields: dialog = exact spoken words in that segment or "" if none. subshots = describe internal cuts inside the 10 seconds (e.g. "0-3s close-up, cut to wide 3-10s") or "single continuous shot". transition = how this segment cuts to the next. audio = music/sound description.
        """.trimIndent()
    }

    fun analyze(
        apiKey: String,
        model: String,
        link: String,
        youtubeId: String,
        mode: AnalyseMode,
        target: TargetEngine,
        replacement: String
    ): Outcome {
        val watchUrl = youtubeWatchUrl(youtubeId)
        val body = JSONObject()
        val parts = JSONArray()
            .put(JSONObject().put("fileData", JSONObject()
                .put("mimeType", "video/*")
                .put("fileUri", watchUrl)))
            .put(JSONObject().put("text", instruction(mode, target, replacement)))
        val contents = JSONArray().put(JSONObject().put("parts", parts))
        body.put("contents", contents)
        body.put("generationConfig", JSONObject()
            .put("responseMimeType", "application/json")
            .put("temperature", 0.4)
            .put("maxOutputTokens", 8192))

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val req = Request.Builder()
            .url(url)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return Outcome.Fail("Gemini menolak (HTTP ${resp.code}). Cek API key / kuota di Setting. Detail: ${text.take(220)}")
                }
                val root = JSONObject(text)
                val candidates = root.optJSONArray("candidates")
                val first = candidates?.optJSONObject(0)
                    ?: return Outcome.Fail("Gemini tidak mengembalikan kandidat jawaban. Coba ulangi.")
                val outText = first.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text").orEmpty()
                if (outText.isBlank()) return Outcome.Fail("Jawaban Gemini kosong. Coba ulangi.")
                parseResult(outText, link, youtubeId, mode, target)
            }
        } catch (e: Exception) {
            Outcome.Fail("Gagal menghubungi Gemini: ${e.message ?: "jaringan bermasalah"}")
        }
    }

    /** Parse defensif: buang pagar kode ``` bila ada, lalu baca JSON. */
    fun parseResult(
        rawText: String,
        link: String,
        youtubeId: String,
        mode: AnalyseMode,
        target: TargetEngine
    ): Outcome {
        val cleaned = rawText.trim()
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) return Outcome.Fail("Format jawaban Gemini tidak dikenali (bukan JSON). Coba ulangi.")
        return try {
            val json = JSONObject(cleaned.substring(start, end + 1))
            val duration = json.optInt("durationSec", 0)
            val scenesArr = json.optJSONArray("scenes") ?: JSONArray()
            val scenes = mutableListOf<ScenePrompt>()
            for (i in 0 until scenesArr.length()) {
                val s = scenesArr.optJSONObject(i) ?: continue
                scenes.add(
                    ScenePrompt(
                        prompt = s.optString("prompt"),
                        negative = s.optString("negative"),
                        camera = s.optString("camera"),
                        lighting = s.optString("lighting"),
                        audio = s.optString("audio"),
                        dialog = s.optString("dialog"),
                        transition = s.optString("transition"),
                        subshots = s.optString("subshots")
                    )
                )
            }
            if (scenes.isEmpty()) return Outcome.Fail("Gemini tidak menghasilkan scene. Coba ulangi.")
            // Normalisasi cap waktu selalu dilakukan di kode, bukan dipercaya dari model.
            normalizeSceneTimestamps(scenes, duration)
            Outcome.Ok(
                AnalysisResult(
                    title = json.optString("title").ifBlank { "Video YouTube $youtubeId" },
                    link = link,
                    youtubeId = youtubeId,
                    durationSec = duration,
                    characterLock = json.optString("characterLock"),
                    styleSummary = json.optString("styleSummary"),
                    mode = mode,
                    targetEngine = target,
                    source = SOURCE_LABEL,
                    scenes = scenes
                )
            )
        } catch (e: Exception) {
            Outcome.Fail("Gagal membaca JSON Gemini: ${e.message}")
        }
    }
}
