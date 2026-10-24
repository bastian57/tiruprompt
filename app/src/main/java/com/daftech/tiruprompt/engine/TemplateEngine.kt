package com.daftech.tiruprompt.engine

// ============================================================================
// TemplateEngine — jalur CADANGAN yang JUJUR. Ini BUKAN analisa video asli:
// prompt disusun dari deskripsi + durasi yang diisi pengguna, dipadukan
// daftar gerakan kamera & lighting yang berputar per scene. Hasilnya selalu
// dilabeli jelas di UI sebagai template. File murni Kotlin (tes JVM aman).
// ============================================================================

object TemplateEngine {

    const val SOURCE_LABEL =
        "Template (bukan analisa video asli — isi Gemini key + link YouTube untuk analisa penuh)"

    private val CAMERA_MOVES = listOf(
        "slow push-in" to "camera slowly pushes in toward the subject",
        "tracking shot" to "smooth tracking shot following the subject movement",
        "handheld close-up" to "intimate handheld close-up with subtle natural shake",
        "wide establishing shot" to "wide establishing shot revealing the full environment",
        "slow dolly sideways" to "slow lateral dolly revealing depth and parallax",
        "static tripod shot" to "locked-off tripod shot, subject moves inside the frame",
        "low angle hero shot" to "low angle hero shot making the subject look powerful",
        "orbital arc shot" to "slow orbital arc around the subject, background shifting"
    )

    private val LIGHTINGS = listOf(
        "warm golden-hour sunlight, soft shadows",
        "clean softbox studio lighting, even skin tones",
        "moody neon accent lighting with deep shadows",
        "bright natural daylight through large windows",
        "cinematic teal-and-orange color grading",
        "soft diffused overcast light, low contrast",
        "dramatic side lighting with strong contrast",
        "cozy warm practical lights in the background, bokeh"
    )

    private val TRANSITIONS = listOf(
        "hard cut to next scene",
        "quick zoom cut to next scene",
        "match cut on the subject movement",
        "whip pan transition to next scene",
        "smooth dissolve to next scene"
    )

    /**
     * Susun hasil template. [description] = deskripsi video dari pengguna,
     * [replacement] hanya dipakai pada mode GANTI_SUBJEK.
     */
    fun build(
        link: String,
        durationSec: Int,
        description: String,
        mode: AnalyseMode,
        target: TargetEngine,
        replacement: String
    ): AnalysisResult {
        val count = sceneCountForDuration(durationSec)
        val subject = when (mode) {
            AnalyseMode.GANTI_SUBJEK -> replacement.ifBlank { description }
            else -> description
        }
        val result = AnalysisResult(
            title = "Hasil Template — " + description.take(60),
            link = link,
            youtubeId = extractYoutubeId(link) ?: "",
            durationSec = durationSec,
            characterLock = subject,
            styleSummary = "Disusun dari deskripsi pengguna (mode ${mode.name}, mesin ${target.name}). " +
                "Bukan hasil menonton video — aktifkan Gemini untuk analisa visual asli.",
            mode = mode,
            targetEngine = target,
            source = SOURCE_LABEL
        )
        for (i in 0 until count) {
            val (camShort, camLong) = CAMERA_MOVES[i % CAMERA_MOVES.size]
            val light = LIGHTINGS[i % LIGHTINGS.size]
            val isFirst = i == 0
            val isLast = i == count - 1
            result.scenes.add(
                ScenePrompt(
                    prompt = buildPrompt(subject, camLong, light, mode, target, isFirst, isLast, replacement),
                    negative = negativeFor(target),
                    camera = camShort,
                    lighting = light,
                    audio = if (isFirst) "energetic opening music hit, then steady background track"
                    else "background music continues, natural ambient sound",
                    dialog = "",
                    transition = if (isLast) "fade out to end card" else TRANSITIONS[i % TRANSITIONS.size],
                    subshots = "single continuous shot (template)"
                )
            )
        }
        normalizeSceneTimestamps(result.scenes, durationSec)
        return result
    }

    private fun buildPrompt(
        subject: String,
        camLong: String,
        light: String,
        mode: AnalyseMode,
        target: TargetEngine,
        isFirst: Boolean,
        isLast: Boolean,
        replacement: String
    ): String {
        val hook = if (isFirst) "HOOK opening: bold attention-grabbing first frame, " +
            "immediate action that creates a curiosity gap in the first 2 seconds. " else ""
        val closing = if (isLast) "Closing beat: satisfying final pose / end frame that invites replay or a call to action. " else ""
        val modeNote = when (mode) {
            AnalyseMode.CLONE_STRUKTUR -> ""
            AnalyseMode.GANTI_SUBJEK ->
                if (replacement.isNotBlank()) "Main subject replaced with: $replacement, keeping the original structure, camera work and pacing. " else ""
            AnalyseMode.AMBIL_STYLE ->
                "Keep only the cinematography, lighting and pacing style of the reference; the scene content follows the description. "
        }
        val core = "$subject, $camLong, $light"
        return when (target) {
            TargetEngine.WAN ->
                "$hook$closing$modeNote" + core.replace(", ", ", ") +
                    ", cinematic, detailed, smooth motion, high quality"
            TargetEngine.KLING ->
                "$hook$closing$modeNote$core. Camera: $camLong. Professional cinematography, fluid realistic motion."
            TargetEngine.VEO ->
                "$hook$closing$modeNote$core. Photorealistic, natural motion, no text overlay, no watermark, no subtitles."
            TargetEngine.MUSE_VIDEO, TargetEngine.UNIVERSAL ->
                "$hook$closing$modeNote$core, cinematic color grading, photorealistic, smooth realistic motion."
        }
    }

    fun negativeFor(target: TargetEngine): String = when (target) {
        TargetEngine.VEO -> "text overlay, subtitles, watermark, distorted hands, morphing face, low quality"
        TargetEngine.KLING -> "blurry, distorted hands, flicker, morphing, low quality, watermark"
        TargetEngine.WAN -> "blurry, low quality, distorted, morphing, watermark, static"
        TargetEngine.MUSE_VIDEO, TargetEngine.UNIVERSAL ->
            "blurry, distorted hands, changing outfit, text artifacts, watermark, low quality"
    }
}
