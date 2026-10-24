package com.daftech.tiruprompt

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daftech.tiruprompt.engine.AnalyseMode
import com.daftech.tiruprompt.engine.AnalysisResult
import com.daftech.tiruprompt.engine.GeminiAnalyzer
import com.daftech.tiruprompt.engine.Storage
import com.daftech.tiruprompt.engine.TargetEngine
import com.daftech.tiruprompt.engine.TemplateEngine
import com.daftech.tiruprompt.engine.ThemeAnalyzer
import com.daftech.tiruprompt.engine.ThemeCard
import com.daftech.tiruprompt.engine.TrendPlatform
import com.daftech.tiruprompt.engine.TrendScraper
import com.daftech.tiruprompt.engine.TrendVideo
import com.daftech.tiruprompt.engine.extractYoutubeId
import com.daftech.tiruprompt.engine.isYoutubeUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab { ANALISA, TREND, RIWAYAT, SETTING }

/**
 * State terpusat TiruPrompt.
 * ATURAN KERAS (pelajaran SuaraKu): JANGAN pernah menamai fungsi set<Property>
 * bila ada properti Compose `var` bernama sama — tabrakan deklarasi JVM.
 * Semua mutator di sini bernama updateX / selectX / toggleX.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ---------- Navigasi ----------
    var currentTab by mutableStateOf(Tab.ANALISA)
        private set

    fun selectTab(tab: Tab) { currentTab = tab }

    // ---------- Setting (dimuat dari SharedPreferences) ----------
    var geminiKey by mutableStateOf(Storage.loadGeminiKey(app))
        private set
    var youtubeKey by mutableStateOf(Storage.loadYoutubeKey(app))
        private set
    var modelName by mutableStateOf(Storage.loadModelName(app))
        private set
    var region by mutableStateOf(Storage.loadRegion(app))
        private set
    var settingsNote by mutableStateOf("")
        private set

    fun updateGeminiKey(v: String) {
        geminiKey = v; Storage.saveGeminiKey(getApplication(), v); settingsNote = "Gemini key tersimpan di perangkat."
    }

    fun updateYoutubeKey(v: String) {
        youtubeKey = v; Storage.saveYoutubeKey(getApplication(), v); settingsNote = "YouTube API key tersimpan di perangkat."
    }

    fun updateModelName(v: String) {
        modelName = v; Storage.saveModelName(getApplication(), v); settingsNote = "Nama model tersimpan."
    }

    fun updateRegion(v: String) {
        region = v.uppercase(); Storage.saveRegion(getApplication(), v)
    }

    // ---------- Tab Analisa ----------
    var linkInput by mutableStateOf("")
        private set
    var mode by mutableStateOf(AnalyseMode.CLONE_STRUKTUR)
        private set
    var targetEngine by mutableStateOf(TargetEngine.UNIVERSAL)
        private set
    var replacementInput by mutableStateOf("")
        private set
    var durationInput by mutableStateOf("")
        private set
    var descriptionInput by mutableStateOf("")
        private set
    var analysing by mutableStateOf(false)
        private set
    var analysisError by mutableStateOf("")
        private set
    var lastResult by mutableStateOf<AnalysisResult?>(null)
        private set
    var analyseStatus by mutableStateOf("")
        private set

    fun updateLink(v: String) { linkInput = v }
    fun selectMode(v: AnalyseMode) { mode = v }
    fun selectTargetEngine(v: TargetEngine) { targetEngine = v }
    fun updateReplacement(v: String) { replacementInput = v }
    fun updateDuration(v: String) { durationInput = v.filter { it.isDigit() }.take(5) }
    fun updateDescription(v: String) { descriptionInput = v }

    /**
     * Tombol ANALISA:
     *  Jalur 1 (ASLI)  : link YouTube + Gemini key -> Gemini menonton video.
     *  Jalur 2 (JUJUR) : selain itu -> TemplateEngine dari durasi + deskripsi.
     */
    fun runAnalysis() {
        if (analysing) return
        analysisError = ""
        val link = linkInput.trim()
        if (link.isBlank()) { analysisError = "Tempel dulu link videonya."; return }
        if (mode == AnalyseMode.GANTI_SUBJEK && replacementInput.isBlank()) {
            analysisError = "Mode GANTI_SUBJEK butuh subjek/produk pengganti diisi."
            return
        }
        val ytId = extractYoutubeId(link)
        val useGemini = ytId != null && geminiKey.isNotBlank()
        if (!useGemini) {
            // Validasi jalur template sebelum jalan
            val dur = durationInput.toIntOrNull() ?: 0
            if (dur <= 0) {
                analysisError = if (ytId == null)
                    "Link non-YouTube hanya bisa jalur Template: isi durasi video (detik) dulu."
                else
                    "Gemini key kosong: jalur Template butuh durasi video (detik) diisi manual."
                return
            }
            if (descriptionInput.isBlank()) {
                analysisError = "Jalur Template butuh deskripsi video diisi (subjek + isi video)."
                return
            }
        }
        analysing = true
        viewModelScope.launch {
            val outcome: Result<AnalysisResult> = withContext(Dispatchers.IO) {
                runCatching {
                    if (useGemini) {
                        analyseStatus = "Gemini sedang menonton video…"
                        when (val o = GeminiAnalyzer.analyze(
                            geminiKey, modelName, link, ytId!!, mode, targetEngine, replacementInput.trim()
                        )) {
                            is GeminiAnalyzer.Outcome.Ok -> o.result
                            is GeminiAnalyzer.Outcome.Fail -> throw IllegalStateException(o.message)
                        }
                    } else {
                        TemplateEngine.build(
                            link, durationInput.toInt(), descriptionInput.trim(),
                            mode, targetEngine, replacementInput.trim()
                        )
                    }
                }
            }
            analysing = false
            analyseStatus = ""
            outcome.onSuccess { result ->
                result.createdAtMs = System.currentTimeMillis()
                lastResult = result
                withContext(Dispatchers.IO) { Storage.addToHistory(getApplication(), result) }
                refreshHistory()
            }.onFailure { e ->
                analysisError = e.message ?: "Analisa gagal."
            }
        }
    }

    fun clearAnalysisError() { analysisError = "" }

    /** Dipanggil kartu video trending: pindah tab + isi link (tidak auto-jalan). */
    fun prefillAnalysisFromTrend(video: TrendVideo) {
        linkInput = video.url
        if (isYoutubeUrl(video.url)) descriptionInput = ""
        currentTab = Tab.ANALISA
        analysisError = ""
    }

    // ---------- Tab Trend ----------
    var trendPlatform by mutableStateOf(TrendPlatform.YOUTUBE)
        private set
    var nicheInput by mutableStateOf("")
        private set
    var scraping by mutableStateOf(false)
        private set
    var trendError by mutableStateOf("")
        private set
    var trendNote by mutableStateOf("")
        private set
    var trendVideos by mutableStateOf<List<TrendVideo>>(emptyList())
        private set
    var trendThemes by mutableStateOf<List<ThemeCard>>(emptyList())
        private set

    fun selectTrendPlatform(p: TrendPlatform) {
        trendPlatform = p
        loadTrendFromCache()
    }

    fun updateNiche(v: String) { nicheInput = v }

    init {
        refreshHistory()
        loadTrendFromCache()
    }

    private fun loadTrendFromCache() {
        val ctx = getApplication<Application>()
        val cached = Storage.trendListFromJson(Storage.loadTrendCacheJson(ctx, trendPlatform))
        if (cached.isNotEmpty()) {
            trendVideos = cached
            recomputeThemes()
            if (trendNote.isBlank()) trendNote = "Hasil scrape terakhir (cache) — tekan SCRAPE untuk memperbarui."
        } else {
            trendVideos = emptyList()
            trendThemes = emptyList()
        }
    }

    private fun recomputeThemes() {
        val ctx = getApplication<Application>()
        val otherPlatform = if (trendPlatform == TrendPlatform.YOUTUBE) TrendPlatform.TIKTOK else TrendPlatform.YOUTUBE
        val otherVideos = Storage.trendListFromJson(Storage.loadTrendCacheJson(ctx, otherPlatform))
        val otherKeywords = ThemeAnalyzer.keywordsOf(otherVideos)
        trendThemes = ThemeAnalyzer.analyze(trendVideos, otherKeywords)
    }

    fun runScrape() {
        if (scraping) return
        trendError = ""
        scraping = true
        val platform = trendPlatform
        val niche = nicheInput.trim()
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                when (platform) {
                    TrendPlatform.YOUTUBE ->
                        if (youtubeKey.isNotBlank()) TrendScraper.scrapeYoutubeApi(youtubeKey, region, niche)
                        else TrendScraper.scrapeYoutubeWeb(niche)
                    TrendPlatform.TIKTOK -> TrendScraper.scrapeTikTok(niche)
                }
            }
            scraping = false
            when (outcome) {
                is TrendScraper.Outcome.Ok -> {
                    trendVideos = outcome.videos
                    trendNote = outcome.note
                    withContext(Dispatchers.IO) {
                        Storage.saveTrendCacheJson(getApplication(), platform, Storage.trendListToJson(outcome.videos))
                    }
                    recomputeThemes()
                }
                is TrendScraper.Outcome.Fail -> trendError = outcome.message
            }
        }
    }

    // ---------- Tab Riwayat ----------
    var historyItems by mutableStateOf<List<AnalysisResult>>(emptyList())
        private set

    fun refreshHistory() {
        viewModelScope.launch {
            historyItems = withContext(Dispatchers.IO) { Storage.loadHistory(getApplication()) }
        }
    }

    fun openHistoryItem(item: AnalysisResult) {
        lastResult = item
        currentTab = Tab.ANALISA
    }

    fun deleteHistoryItem(item: AnalysisResult) {
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val items = Storage.loadHistory(getApplication())
                items.removeAll { it.createdAtMs == item.createdAtMs && it.title == item.title }
                Storage.saveHistory(getApplication(), items)
                items
            }
            historyItems = updated
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Storage.saveHistory(getApplication(), emptyList()) }
            historyItems = emptyList()
        }
    }
}
