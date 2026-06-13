package com.example.aiprediksi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiprediksi.data.AnalysisResult
import com.example.aiprediksi.data.AppPrefs
import com.example.aiprediksi.data.AssetDatabase
import com.example.aiprediksi.data.AssetInfo
import com.example.aiprediksi.data.AssetType
import com.example.aiprediksi.data.BackupManager
import com.example.aiprediksi.data.ChatRepository
import com.example.aiprediksi.data.ChartInterval
import com.example.aiprediksi.data.MarketDataRepository
import com.example.aiprediksi.data.MessageEntity
import com.example.aiprediksi.data.OHLCV
import com.example.aiprediksi.data.OHLCVUpdate
import com.example.aiprediksi.data.PredictionDirection
import com.example.aiprediksi.data.ProviderConfig
import com.example.aiprediksi.data.RiskLevel
import com.example.aiprediksi.data.SessionEntity
import com.example.aiprediksi.data.SettingsStore
import com.example.aiprediksi.data.applyProviderConfig
import com.example.aiprediksi.data.getApiType
import com.example.aiprediksi.data.getDefaultBaseUrl
import com.example.aiprediksi.data.getDefaultModel
import com.example.aiprediksi.data.getFallbackChain
import com.example.aiprediksi.data.getModelsForProvider
import com.example.aiprediksi.data.getProviderConfig
import com.example.aiprediksi.data.getAllProviderNames
import com.example.aiprediksi.data.setProviderConfig
import com.example.aiprediksi.network.ApiResult
import com.example.aiprediksi.network.GenericApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

enum class ConnectionStatus { IDLE, TESTING, CONNECTED, FAILED }

data class UiState(
    val prefs: AppPrefs = AppPrefs(),
    val selectedAsset: AssetInfo = AssetDatabase.cryptoAssets.first(),
    val interval: ChartInterval = ChartInterval.H1,
    val candles: List<OHLCV> = emptyList(),
    val isLoadingData: Boolean = false,
    val dataError: String = "",
    val changePercent: Double = 0.0,
    val rsi: Double = 50.0,
    val supports: List<Double> = emptyList(),
    val resistances: List<Double> = emptyList(),
    val hoveredCandle: OHLCV? = null,
    // Chart zoom
    val visibleCandleCount: Int = 80,
    val isLive: Boolean = false,
    // AI
    val isAnalyzing: Boolean = false,
    val analysisResult: AnalysisResult? = null,
    val streamingAnalysis: String = "",
    val errorMessage: String = "",
    val errorLog: String = "",
    // Settings
    val showSettings: Boolean = false,
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
)

class AppViewModel(
    private val settingsStore: SettingsStore,
    private val marketDataRepo: MarketDataRepository,
    private val apiClient: GenericApiClient,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val selectedAsset = MutableStateFlow(AssetDatabase.cryptoAssets.first())
    private val interval = MutableStateFlow(ChartInterval.H1)
    private val candles = MutableStateFlow<List<OHLCV>>(emptyList())
    private val isLoadingData = MutableStateFlow(false)
    private val dataError = MutableStateFlow("")
    private val changePercent = MutableStateFlow(0.0)
    private val rsi = MutableStateFlow(50.0)
    private val supports = MutableStateFlow<List<Double>>(emptyList())
    private val resistances = MutableStateFlow<List<Double>>(emptyList())
    private val hoveredCandle = MutableStateFlow<OHLCV?>(null)
    private val visibleCandleCount = MutableStateFlow(80)
    private val isLive = MutableStateFlow(true)
    private val isAnalyzing = MutableStateFlow(false)
    private val analysisResult = MutableStateFlow<AnalysisResult?>(null)
    private val streamingAnalysis = MutableStateFlow("")
    private val errorMessage = MutableStateFlow("")
    private val errorLog = MutableStateFlow("")
    private val showSettings = MutableStateFlow(false)
    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)

    private val prefsFlow = settingsStore.prefsFlow

    val uiState: StateFlow<UiState> = combine(
        prefsFlow, selectedAsset, interval, candles,
        isLoadingData, dataError, changePercent, rsi,
        supports, resistances, hoveredCandle, isAnalyzing,
        analysisResult, streamingAnalysis, errorMessage, errorLog,
        showSettings, connectionStatus,
    ) {
        val arr = it
        UiState(
            prefs = arr[0] as AppPrefs,
            selectedAsset = arr[1] as AssetInfo,
            interval = arr[2] as ChartInterval,
            candles = arr[3] as List<OHLCV>,
            isLoadingData = arr[4] as Boolean,
            dataError = arr[5] as String,
            changePercent = arr[6] as Double,
            rsi = arr[7] as Double,
            supports = arr[8] as List<Double>,
            resistances = arr[9] as List<Double>,
            hoveredCandle = arr[10] as OHLCV?,
            isAnalyzing = arr[11] as Boolean,
            analysisResult = arr[12] as AnalysisResult?,
            streamingAnalysis = arr[13] as String,
            errorMessage = arr[14] as String,
            errorLog = arr[15] as String,
            showSettings = arr[16] as Boolean,
            connectionStatus = arr[17] as ConnectionStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState())

    init {
        // Load default asset on start
        selectAsset(AssetDatabase.cryptoAssets.first())
        // Start real-time if live mode
        if (isLive.value) {
            startAutoRefresh()
            startKlineStream()
        }
    }

    // ======================== ASSET & INTERVAL ========================

    fun selectAsset(asset: AssetInfo) {
        selectedAsset.value = asset
        analysisResult.value = null
        streamingAnalysis.value = ""
        errorMessage.value = ""
        fetchMarketData()
        restartKlineStream()
    }

    fun selectInterval(chartInterval: ChartInterval) {
        interval.value = chartInterval
        analysisResult.value = null
        fetchMarketData()
        restartKlineStream()
    }

    fun setHoveredCandle(c: OHLCV?) { hoveredCandle.value = c }

    fun setVisibleCandleCount(count: Int) { visibleCandleCount.value = count.coerceIn(5, 500) }

    fun toggleLive() {
        isLive.value = !isLive.value
        if (isLive.value) {
            startAutoRefresh()
            startKlineStream()
        } else {
            refreshJob?.cancel()
            wsJob?.cancel()
        }
    }

    fun toggleSettings() { showSettings.value = !showSettings.value }

    fun updateSetting(transform: (AppPrefs) -> AppPrefs) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    // ======================== AUTO-REFRESH ========================

    private var refreshJob: kotlinx.coroutines.Job? = null

    private fun startAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (true) {
                if (!isLive.value) break
                delay(getRefreshInterval())
                fetchMarketData()
            }
        }
    }

    private fun getRefreshInterval(): Long {
        if (!isLive.value) return Long.MAX_VALUE
        return when (interval.value) {
            ChartInterval.M1 -> 15_000L
            ChartInterval.M5 -> 30_000L
            ChartInterval.M15 -> 60_000L
            ChartInterval.M30 -> 120_000L
            ChartInterval.H1 -> 180_000L
            ChartInterval.H4 -> 300_000L
            ChartInterval.H12 -> 600_000L
            ChartInterval.D1 -> 600_000L
            ChartInterval.W1 -> 1_800_000L
        }
    }

    // ======================== WEBSOCKET STREAM ========================

    private var wsJob: kotlinx.coroutines.Job? = null

    fun restartKlineStream() {
        if (isLive.value) {
            wsJob?.cancel()
            startKlineStream()
        }
    }

    private fun startKlineStream() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            val asset = selectedAsset.value
            val symbol = marketDataRepo.getBinanceSymbol(asset)
            val intv = interval.value.binanceValue

            while (isLive.value) {
                try {
                    marketDataRepo.klineStream(symbol, intv).collect { update ->
                        if (!isLive.value) return@collect
                        val current = candles.value.toMutableList()
                        if (current.isNotEmpty()) {
                            val last = current.last()
                            if (last.openTime == update.openTime) {
                                current[current.size - 1] = OHLCV(
                                    openTime = update.openTime,
                                    open = update.open,
                                    high = maxOf(last.high, update.high),
                                    low = minOf(last.low, update.low),
                                    close = update.close,
                                    volume = update.volume,
                                    closeTime = update.closeTime,
                                )
                                if (update.isClosed) {
                                    fetchMarketData()
                                }
                            } else if (update.openTime > last.openTime) {
                                current.add(
                                    OHLCV(
                                        openTime = update.openTime,
                                        open = update.open,
                                        high = update.high,
                                        low = update.low,
                                        close = update.close,
                                        volume = update.volume,
                                        closeTime = update.closeTime,
                                    )
                                )
                            }
                            candles.value = current
                        }
                    }
                } catch (e: Exception) {
                    if (isLive.value) kotlinx.coroutines.delay(5000)
                }
            }
        }
    }

    // ======================== MARKET DATA ========================

    fun fetchMarketData() {
        val asset = selectedAsset.value
        val intv = interval.value
        viewModelScope.launch {
            isLoadingData.value = true
            dataError.value = ""

            val symbol = marketDataRepo.getBinanceSymbol(asset)
            val result = marketDataRepo.fetchKlines(symbol, intv.binanceValue, 100)

            result.onSuccess { klines ->
                candles.value = klines
                changePercent.value = marketDataRepo.calculateChange(klines)
                rsi.value = marketDataRepo.calculateRSI(klines)
                val (sup, res) = marketDataRepo.findSupportResistance(klines)
                supports.value = sup
                resistances.value = res
                dataError.value = ""
                // Connect status
                connectionStatus.value = ConnectionStatus.CONNECTED
            }.onFailure { e ->
                dataError.value = "Gagal ambil data: ${e.message}"
                connectionStatus.value = ConnectionStatus.FAILED
            }
            isLoadingData.value = false
        }
    }

    // ======================== AI PREDIKSI ========================

    fun analyzeChart() {
        val asset = selectedAsset.value
        val cndls = candles.value
        if (cndls.isEmpty()) {
            errorMessage.value = "Tidak ada data chart. Pilih aset dulu."
            return
        }

        viewModelScope.launch {
            isAnalyzing.value = true
            streamingAnalysis.value = ""
            analysisResult.value = null
            errorMessage.value = ""
            appendLog("Memulai analisis untuk ${asset.symbol}...")

            // Build market context
            val marketContext = buildMarketContext(asset, cndls)
            val systemPrompt = buildAnalysisPrompt(asset, marketContext)

            // Try primary provider
            val prefs = settingsStore.prefsFlow.first()
            val provider = prefs.apiProvider
            val config = getProviderConfig(prefs, provider)
            val model = config.model.ifBlank { prefs.model }
            val apiKey = config.apiKey.ifBlank { prefs.apiKey }
            val baseUrl = config.baseUrl.ifBlank { prefs.baseUrl }

            var success = false
            if (apiKey.isNotBlank()) {
                success = tryAnalyzeWithProvider(
                    provider = provider,
                    model = model,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    temperature = config.temperature.ifNaN(prefs.temperature),
                    systemPrompt = systemPrompt,
                )
            }

            if (!success) {
                // Fallback chain
                appendLog("Provider utama gagal, coba fallback...")
                val fallbackProviders = getAllProviderNames()
                    .filter { it != provider && it != "Custom" }
                    .mapNotNull { p ->
                        val cfg = getProviderConfig(prefs, p)
                        if (cfg.apiKey.isNotBlank()) Triple(p, cfg.model.ifBlank { getDefaultModel(p) }, cfg.apiKey) else null
                    }

                for ((fbProvider, fbModel, fbKey) in fallbackProviders) {
                    appendLog("Mencoba fallback: $fbProvider / $fbModel")
                    success = tryAnalyzeWithProvider(
                        provider = fbProvider,
                        model = fbModel,
                        baseUrl = getDefaultBaseUrl(fbProvider),
                        apiKey = fbKey,
                        temperature = 0.3f,
                        systemPrompt = systemPrompt,
                    )
                    if (success) {
                        appendLog("✅ Fallback berhasil: $fbProvider")
                        break
                    }
                }
            }

            if (!success) {
                errorMessage.value = "Semua provider gagal. Periksa API key."
                appendLog("❌ Semua gagal")
            }

            isAnalyzing.value = false
        }
    }

    private suspend fun tryAnalyzeWithProvider(
        provider: String,
        model: String,
        baseUrl: String,
        apiKey: String,
        temperature: Float,
        systemPrompt: String,
    ): Boolean {
        return try {
            val headers = "Content-Type: application/json\nAuthorization: Bearer $apiKey\nAccept: application/json"
            val messages = JSONArray().apply {
                put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
            }
            val body = JSONObject().apply {
                put("model", model)
                put("messages", messages)
                put("temperature", temperature.toDouble())
                put("max_tokens", 2048)
                put("stream", true)
            }.toString()

            var fullText = ""
            var lastError = ""

            apiClient.executeStreaming(
                url = baseUrl,
                method = "POST",
                headersText = headers,
                body = body,
                onToken = { token ->
                    fullText += token
                    streamingAnalysis.value = fullText
                },
                onDone = {
                    if (fullText.isNotBlank()) {
                        val result = parseAnalysisResult(fullText, selectedAsset.value.symbol)
                        analysisResult.value = result
                        appendLog("✅ Analisis selesai dari $provider")
                    }
                },
                onError = { err -> lastError = err },
            )

            if (fullText.isNotBlank()) true
            else {
                appendLog("❌ $provider: $lastError")
                false
            }
        } catch (e: Exception) {
            appendLog("❌ $provider error: ${e.message}")
            false
        }
    }

    // ======================== BUILD CONTEXT ========================

    private fun buildMarketContext(asset: AssetInfo, cndls: List<OHLCV>): String {
        if (cndls.isEmpty()) return "Tidak ada data."
        val last = cndls.last()
        val prev = if (cndls.size > 1) cndls[cndls.size - 2] else last
        val change = if (prev.close != 0.0) ((last.close - prev.close) / prev.close * 100) else 0.0
        val high24 = cndls.maxOf { it.high }
        val low24 = cndls.minOf { it.low }
        val avgVol = cndls.map { it.volume }.average()
        val rsiVal = marketDataRepo.calculateRSI(cndls)
        val sma20 = marketDataRepo.calculateSMA(cndls, 20)
        val sma50 = marketDataRepo.calculateSMA(cndls, 50)
        val (supps, ress) = marketDataRepo.findSupportResistance(cndls)

        return buildString {
            appendLine("Aset: ${asset.name} (${asset.symbol})")
            appendLine("Tipe: ${asset.type.label}")
            appendLine("Timeframe: ${interval.value.label}")
            appendLine("Jumlah candle: ${cndls.size}")
            appendLine()
            appendLine("Data Harga Terkini:")
            appendLine("- Harga Terakhir: ${fmt(last.close)}")
            appendLine("- Open: ${fmt(last.open)}")
            appendLine("- High: ${fmt(last.high)}")
            appendLine("- Low: ${fmt(last.low)}")
            appendLine("- Perubahan: ${"%.2f".format(change)}%")
            appendLine("- Volume: ${fmtVol(last.volume)}")
            appendLine()
            appendLine("Statistik Periode:")
            appendLine("- Tertinggi: ${fmt(high24)}")
            appendLine("- Terendah: ${fmt(low24)}")
            appendLine("- Rata-rata Volume: ${fmtVol(avgVol)}")
            appendLine()
            appendLine("Indikator Teknikal:")
            appendLine("- RSI(14): ${"%.1f".format(rsiVal)} ${rsiInterpretation(rsiVal)}")
            if (sma20.isNotEmpty()) appendLine("- SMA 20: ${fmt(sma20.last())}")
            if (sma50.isNotEmpty()) appendLine("- SMA 50: ${fmt(sma50.last())}")
            if (sma20.isNotEmpty() && sma50.isNotEmpty()) {
                val goldenCross = sma20.last() > sma50.last()
                appendLine(if (goldenCross) "- Golden Cross (SMA20 > SMA50) 🟢" else "- Death Cross (SMA20 < SMA50) 🔴")
            }
            appendLine()
            appendLine("Level Support & Resistance:")
            supps.forEachIndexed { i, s -> appendLine("  Support ${i + 1}: ${fmt(s)}") }
            ress.forEachIndexed { i, r -> appendLine("  Resistance ${i + 1}: ${fmt(r)}") }
        }
    }

    private fun buildAnalysisPrompt(asset: AssetInfo, marketContext: String): String {
        return buildString {
            appendLine("Kamu adalah analis pasar profesional dan akurat.")
            appendLine()
            appendLine("Analisis data market berikut dan berikan prediksi:")
            appendLine()
            appendLine(marketContext)
            appendLine()
            appendLine("""
Berikan analisis dalam format berikut (BAHASA INDONESIA):

📊 ANALISIS [NAMA ASET]
========================

🟢/🔴/⚪ ARAH: [Bullish/Bearish/Netral]
🎯 TARGET: [harga target]
🛑 STOP LOSS: [harga stop loss]
📈 KEYAKINAN: [persentase]%

ANALISIS TEKNIKAL:
- [poin analisis teknikal]

ANALISIS FUNDAMENTAL:
- [poin analisis fundamental]

LEVEL PENTING:
- Support: [level]
- Resistance: [level]

REKOMENDASI:
- [saran spesifik]

RISIKO: [Rendah/Sedang/Tinggi]

⚠️ Disclaimer: Ini bukan saran keuangan.
            """.trimIndent())
        }
    }

    private fun parseAnalysisResult(text: String, symbol: String): AnalysisResult {
        val direction = when {
            text.contains("🟢") || text.contains("Bullish", ignoreCase = true) -> PredictionDirection.BULLISH
            text.contains("🔴") || text.contains("Bearish", ignoreCase = true) -> PredictionDirection.BEARISH
            else -> PredictionDirection.NEUTRAL
        }
        val confidence = extractPercent(text, "KEYAKINAN") ?: 50f
        val target = extractPrice(text, "TARGET")
        val stopLoss = extractPrice(text, "STOP LOSS")
        val risk = when {
            text.contains("Tinggi", ignoreCase = true) -> RiskLevel.HIGH
            text.contains("Rendah", ignoreCase = true) -> RiskLevel.LOW
            else -> RiskLevel.MEDIUM
        }
        val supportLevels = extractLevels(text, "Support")
        val resistanceLevels = extractLevels(text, "Resistance")

        return AnalysisResult(
            symbol = symbol,
            direction = direction,
            confidence = confidence,
            targetPrice = target,
            stopLoss = stopLoss,
            reasoning = text,
            riskLevel = risk,
            supportLevels = supportLevels,
            resistanceLevels = resistanceLevels,
        )
    }

    // ======================== PROVIDER CONFIG ========================

    fun selectProvider(provider: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val config = getProviderConfig(prefs, provider)
            settingsStore.update {
                it.copy(
                    apiProvider = provider,
                    apiKey = config.apiKey,
                    model = config.model.ifBlank { getDefaultModel(provider) },
                    baseUrl = config.baseUrl.ifBlank { getDefaultBaseUrl(provider) },
                    temperature = config.temperature,
                    maxTokens = config.maxTokens,
                )
            }
        }
    }

    fun updateProviderConfig(provider: String, config: ProviderConfig) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val newConfigs = setProviderConfig(prefs, provider, config)
            settingsStore.update {
                it.copy(providerConfigs = newConfigs).let { p ->
                    if (p.apiProvider == provider) applyProviderConfig(p, config) else p
                }
            }
        }
    }

    // ======================== HELPERS ========================

    private fun appendLog(msg: String) {
        val current = errorLog.value
        errorLog.value = if (current.length > 3000) current.takeLast(2500) + "\n$msg" else "$current\n$msg"
    }

    private fun Float.ifNaN(default: Float): Float = if (isNaN()) default else this

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        settingsStore = container.settingsStore,
                        marketDataRepo = container.marketDataRepo,
                        apiClient = container.apiClient,
                        backupManager = container.backupManager,
                    ) as T
                }
            }
        }
    }
}

// ======================== EXTENSION FUNCTIONS ========================

private fun rsiInterpretation(rsi: Double): String = when {
    rsi >= 70 -> "(Overbought/Jenuh Beli 🔴)"
    rsi <= 30 -> "(Oversold/Jenuh Jual 🟢)"
    else -> "(Normal)"
}

private fun extractPercent(text: String, label: String): Float? {
    val regex = Regex("""$label[:\s]*(\d{1,3})""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.groupValues?.getOrNull(1)?.toFloatOrNull()
}

private fun extractPrice(text: String, label: String): Double? {
    val regex = Regex("""$label[:\s]*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    return regex.find(text)?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
}

private fun extractLevels(text: String, label: String): List<Double> {
    val regex = Regex("""$label[ \d]*[:\s]*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE)
    return regex.findAll(text).mapNotNull {
        it.groupValues.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
    }.toList()
}

private fun fmt(p: Double): String = when {
    p >= 1000 -> "%.2f".format(p)
    p >= 1 -> "%.4f".format(p)
    else -> "%.6f".format(p)
}

private fun fmtVol(v: Double): String = when {
    v >= 1_000_000 -> "%.2fM".format(v / 1_000_000)
    v >= 1_000 -> "%.2fK".format(v / 1_000)
    else -> "%.2f".format(v)
}
