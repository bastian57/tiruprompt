package com.daftech.tiruprompt.engine

// ============================================================================
// ThemeAnalyzer — pengelompokan tema video trending yang DETERMINISTIK dan
// murni lokal (tanpa AI, tanpa Android). Kelompok dibentuk dari kata
// signifikan judul + hashtag yang muncul minimal 2 kali.
//
// Ambang status tema (didokumentasikan, bukan angka sulap diam-diam):
//  - "LAGI NAIK"  : rata-rata velocity >= 5.000 views/jam DAN jumlah video < 5
//                   (sedikit kreator tapi kecepatannya tinggi = baru meledak)
//  - "RAMAI/JENUH": jumlah video >= 6 (banyak yang ikut = pasar tema padat)
//  - selain itu   : "STABIL"
// ============================================================================

object ThemeAnalyzer {

    private val STOPWORDS = setOf(
        // Indonesia
        "yang", "dan", "di", "ke", "dari", "ini", "itu", "untuk", "dengan", "cara",
        "aku", "saya", "kamu", "dia", "gue", "bang", "kak", "viral", "fyp", "video",
        "banget", "bikin", "jadi", "ada", "tidak", "nggak", "enggak", "kok", "sih",
        "aja", "saja", "bakal", "harus", "pakai", "pake", "saat", "hari", "paling",
        "terbaru", "full", "part", "the", "bukan", "dalam", "pada", "juga", "atau",
        // Inggris
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with",
        "my", "your", "you", "how", "what", "why", "when", "this", "that", "is",
        "are", "was", "were", "it", "its", "at", "by", "from", "me", "i", "we",
        "they", "he", "she", "his", "her", "our", "new", "get", "got", "one",
        "official", "music", "ft", "feat"
    )

    private val QUESTION_WORDS = listOf(
        "kenapa", "mengapa", "bagaimana", "gimana", "apa", "apakah", "siapa",
        "how", "why", "what", "who", "which", "can", "could", "would"
    )
    private val SHOCK_WORDS = listOf(
        "ternyata", "rahasia", "jangan", "stop", "berhenti", "awas", "bahaya",
        "gila", "parah", "shocking", "secret", "never", "warning", "must"
    )

    /** Kata signifikan dari judul: huruf/angka saja, len>=3, bukan stopword. */
    fun significantWords(title: String): List<String> =
        title.lowercase()
            .replace(Regex("""[^a-z0-9\s#]"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim().removePrefix("#") }
            .filter { it.length >= 3 && it !in STOPWORDS && !it.all { c -> c.isDigit() } }

    /**
     * Deteksi pola hook dari judul-judul teratas tema.
     * Mengembalikan label Bahasa Indonesia.
     */
    fun detectHook(titles: List<String>): String {
        if (titles.isEmpty()) return "Visual kuat di detik pertama"
        val joined = titles.joinToString(" ").lowercase()
        val firstWords = titles.map { t ->
            t.lowercase().replace(Regex("""^[^a-z0-9]+"""), "").split(" ").firstOrNull() ?: ""
        }
        return when {
            titles.any { it.contains("?") } || firstWords.any { it in QUESTION_WORDS } ->
                "Pertanyaan penasaran (curiosity gap)"
            titles.any { Regex("""^\s*\d+""").containsMatchIn(it) } ->
                "Angka + daftar (listicle)"
            firstWords.any { it == "pov" } || joined.contains("pov:") || joined.contains("pov ") ->
                "POV / sudut pandang"
            joined.contains("before") && joined.contains("after") ||
                (joined.contains("sebelum") && joined.contains("sesudah")) ||
                joined.contains("before/after") || joined.contains("transformasi") ->
                "Before / After (transformasi)"
            SHOCK_WORDS.any { joined.contains(it) } ->
                "Kata kejut / larangan (shock hook)"
            joined.contains("reaction") || joined.contains("reaksi") || joined.contains("nonton") ->
                "Reaksi spontan"
            else -> "Visual kuat di detik pertama"
        }
    }

    /** Tebakan format dari kata kunci judul/hashtag. */
    fun guessFormat(videos: List<TrendVideo>): String {
        val joined = videos.joinToString(" ") { it.title + " " + it.hashtags.joinToString(" ") }.lowercase()
        return when {
            joined.contains("tutorial") || joined.contains("cara ") || joined.contains("how to") ||
                joined.contains("tips") -> "Tutorial / Edukasi"
            joined.contains("reaction") || joined.contains("reaksi") || joined.contains("react") -> "Reaction"
            joined.contains("pov") || joined.contains("skit") || joined.contains("sketsa") ||
                joined.contains("drama") -> "Skit / POV"
            joined.contains("review") || joined.contains("unboxing") || joined.contains("haul") -> "Review / Unboxing"
            joined.contains("vlog") || joined.contains("sehari") || joined.contains("day in") -> "Vlog"
            joined.contains("cover") || joined.contains("dance") || joined.contains("tari") -> "Musik / Dance"
            else -> "Talking / Visual"
        }
    }

    /**
     * Bangun kartu tema dari daftar video satu platform.
     * [otherPlatformKeywords] = kata tema dari cache platform lain, untuk
     * lencana LINTAS PLATFORM.
     */
    fun analyze(
        videos: List<TrendVideo>,
        otherPlatformKeywords: Set<String> = emptySet()
    ): List<ThemeCard> {
        if (videos.isEmpty()) return emptyList()
        val buckets = mutableMapOf<String, MutableList<TrendVideo>>()
        for (v in videos) {
            val keys = (significantWords(v.title) + v.hashtags.map { it.lowercase().removePrefix("#") })
                .filter { it.length >= 3 && it !in STOPWORDS }
                .distinct()
            for (k in keys) buckets.getOrPut(k) { mutableListOf() }.add(v)
        }
        val cards = mutableListOf<ThemeCard>()
        for ((keyword, bucket) in buckets) {
            if (bucket.size < 2) continue
            val totalViews = bucket.sumOf { it.views }
            val avgViews = if (bucket.isNotEmpty()) totalViews / bucket.size else 0L
            val avgVel = if (bucket.isNotEmpty()) bucket.sumOf { it.velocity } / bucket.size else 0.0
            val top = bucket.sortedByDescending { it.viralScore }.take(3)
            val status = when {
                avgVel >= 5000.0 && bucket.size < 5 -> "LAGI NAIK"
                bucket.size >= 6 -> "RAMAI/JENUH"
                else -> "STABIL"
            }
            cards.add(
                ThemeCard(
                    name = keyword,
                    videoCount = bucket.size,
                    avgViews = avgViews,
                    totalViews = totalViews,
                    avgVelocity = avgVel,
                    topVideoTitles = top.map { it.title },
                    hookPattern = detectHook(top.map { it.title }),
                    formatGuess = guessFormat(bucket),
                    status = status,
                    crossPlatform = keyword in otherPlatformKeywords
                )
            )
        }
        return cards.sortedWith(
            compareByDescending<ThemeCard> { it.crossPlatform }
                .thenByDescending { it.avgVelocity }
                .thenByDescending { it.videoCount }
        ).take(12)
    }

    /** Kata kunci tema dari daftar video (untuk dibandingkan lintas platform). */
    fun keywordsOf(videos: List<TrendVideo>): Set<String> {
        val counts = mutableMapOf<String, Int>()
        for (v in videos) {
            (significantWords(v.title) + v.hashtags.map { it.lowercase().removePrefix("#") })
                .filter { it.length >= 3 && it !in STOPWORDS }
                .distinct()
                .forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.filterValues { it >= 2 }.keys
    }
}
