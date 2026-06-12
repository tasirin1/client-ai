package com.example.aiprediksi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiprediksi.data.AppPrefs
import com.example.aiprediksi.data.AssetDatabase
import com.example.aiprediksi.data.AssetType
import com.example.aiprediksi.data.BackupManager
import com.example.aiprediksi.data.ChatRepository
import com.example.aiprediksi.data.MessageEntity
import com.example.aiprediksi.data.ProviderConfig
import com.example.aiprediksi.data.SessionEntity
import com.example.aiprediksi.data.SettingsStore
import com.example.aiprediksi.data.getApiType
import com.example.aiprediksi.data.getDefaultBaseUrl
import com.example.aiprediksi.data.getDefaultModel
import com.example.aiprediksi.data.getProviderConfig
import com.example.aiprediksi.data.getModelsForProvider
import com.example.aiprediksi.data.getFallbackChain
import com.example.aiprediksi.data.setProviderConfig
import com.example.aiprediksi.data.getAllProviderNames
import com.example.aiprediksi.network.ApiResult
import com.example.aiprediksi.network.GenericApiClient
import com.example.aiprediksi.data.applyProviderConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ConnectionStatus { IDLE, TESTING, CONNECTED, FAILED }

data class SessionPreview(
    val session: SessionEntity,
    val lastMessage: String? = null,
    val lastMessageTime: Long? = null,
)

data class UiState(
    val prefs: AppPrefs = AppPrefs(),
    val sessions: List<SessionPreview> = emptyList(),
    val groupedSessions: Map<String, List<SessionPreview>> = emptyMap(),
    val messages: List<MessageEntity> = emptyList(),
    val currentSessionId: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String = "",
    val sessionSearchQuery: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val connectionError: String = "",
    val errorLog: String = "",
    val streamingText: String = "",
    val selectedAssetType: AssetType = AssetType.CRYPTO,
    val favoriteAssets: List<String> = emptyList(),
    val currentTimeframe: String = "24h",
    val showAssetSelector: Boolean = false,
    val showSettings: Boolean = false,
    val showNewSessionDialog: Boolean = false,
)

class AppViewModel(
    private val settingsStore: SettingsStore,
    private val chatRepository: ChatRepository,
    private val apiClient: GenericApiClient,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val loading = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    private val connectionError = MutableStateFlow("")
    private val errorLog = MutableStateFlow("")
    private val streamingText = MutableStateFlow("")
    private val showAssetSelector = MutableStateFlow(false)
    private val showSettings = MutableStateFlow(false)
    private val showNewSessionDialog = MutableStateFlow(false)

    private val prefsFlow = settingsStore.prefsFlow
    private val sessionsFlow = chatRepository.observeSessions()
    private val lastMessagesFlow = chatRepository.observeLastMessagesForAllSessions()

    private val sessionPreviewsFlow = combine(sessionsFlow, lastMessagesFlow) { sessions, lastMessages ->
        val msgMap = lastMessages.associateBy { it.sessionId }
        sessions.map { session ->
            val lastMsg = msgMap[session.id]
            SessionPreview(
                session = session,
                lastMessage = lastMsg?.let { truncatePreview(it.content) },
                lastMessageTime = lastMsg?.createdAt,
            )
        }
    }

    private val filteredSessionsFlow = combine(sessionPreviewsFlow, searchQuery) { previews, query ->
        if (query.isBlank()) previews
        else previews.filter { it.session.title.contains(query, ignoreCase = true) }
    }

    private val currentSessionIdFlow = prefsFlow.map { it.activeSessionId }
    private val messagesFlow = prefsFlow.flatMapLatest { prefs ->
        val sessionId = prefs.activeSessionId
        if (sessionId.isBlank()) kotlinx.coroutines.flow.flowOf(emptyList())
        else chatRepository.observeMessages(sessionId)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        prefsFlow,
        filteredSessionsFlow,
        messagesFlow
    ) { prefs, sessions, messages ->
        val showAs = showAssetSelector.value
        val showSet = showSettings.value
        val showNew = showNewSessionDialog.value
        val load = loading.value
        val err = errorMessage.value
        val connStat = connectionStatus.value
        val connErr = connectionError.value
        val errLog = errorLog.value
        val streamTxt = streamingText.value
        val currentId = prefs.activeSessionId
        UiState(
            prefs = prefs,
            sessions = sessions,
            groupedSessions = groupSessionsByDate(sessions),
            messages = messages,
            currentSessionId = currentId,
            isLoading = load,
            errorMessage = err,
            connectionStatus = connStat,
            connectionError = connErr,
            errorLog = errLog,
            streamingText = streamTxt,
            selectedAssetType = try { AssetType.valueOf(prefs.selectedAssetType) } catch (_: Exception) { AssetType.CRYPTO },
            favoriteAssets = parseFavorites(prefs.favoriteAssets),
            currentTimeframe = prefs.defaultTimeframe,
            showAssetSelector = showAs,
            showSettings = showSet,
            showNewSessionDialog = showNew,
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, UiState())

    // ======================== SESSION MANAGEMENT ========================

    fun selectSession(sessionId: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(activeSessionId = sessionId) }
        }
    }

    fun createNewSession() {
        viewModelScope.launch {
            val session = chatRepository.createSession("Prediksi baru")
            settingsStore.update { it.copy(activeSessionId = session.id) }
            showNewSessionDialog.value = false
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            chatRepository.deleteSession(sessionId)
            val prefs = settingsStore.prefsFlow.first()
            if (prefs.activeSessionId == sessionId) {
                settingsStore.update { it.copy(activeSessionId = "") }
            }
        }
    }

    fun updateSearchQuery(query: String) { searchQuery.value = query }

    // ======================== SETTINGS ========================

    fun updateSetting(transform: (AppPrefs) -> AppPrefs) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    fun toggleSettings() { showSettings.value = !showSettings.value }
    fun toggleAssetSelector() { showAssetSelector.value = !showAssetSelector.value }
    fun toggleNewSessionDialog() { showNewSessionDialog.value = !showNewSessionDialog.value }

    fun setAssetType(type: AssetType) {
        viewModelScope.launch {
            settingsStore.update { it.copy(selectedAssetType = type.name) }
        }
    }

    fun setTimeframe(timeframe: String) {
        viewModelScope.launch {
            settingsStore.update { it.copy(defaultTimeframe = timeframe) }
        }
    }

    fun toggleFavoriteAsset(symbol: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val current = parseFavorites(prefs.favoriteAssets)
            val updated = if (symbol in current) current - symbol else current + symbol
            settingsStore.update { it.copy(favoriteAssets = JSONArray(updated).toString()) }
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

    // ======================== AUTO-FALLBACK & PREDIKSI ========================

    /**
     * Kirim prompt prediksi dengan auto-fallback chain.
     * Jika provider utama gagal (token habis / error), coba provider lain.
     */
    fun sendPrediction(input: String, assetType: AssetType = AssetType.CRYPTO) {
        if (input.isBlank()) return
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val sessionId = prefs.activeSessionId
            if (sessionId.isBlank()) {
                val session = chatRepository.createSession(input.take(28))
                settingsStore.update { it.copy(activeSessionId = session.id) }
            }

            // Simpan pesan user
            val sid = settingsStore.prefsFlow.first().activeSessionId
            chatRepository.addMessage(sid, "user", input, assetType = assetType.name)

            // Auto-rename jika masih default
            val session = chatRepository.getSessionOnce(sid)
            if (session?.title == "Prediksi baru") {
                chatRepository.renameSession(sid, input.take(28).trim().ifBlank { "Prediksi" })
            }

            loading.value = true
            streamingText.value = ""
            errorMessage.value = ""

            val provider = prefs.apiProvider
            val config = getProviderConfig(prefs, provider)
            val model = config.model.ifBlank { prefs.model }

            // Prompt prediksi dengan konteks
            val systemPrompt = buildPredictionPrompt(assetType, input)

            // Coba provider utama dulu
            val success = trySendWithProvider(
                sessionId = sid,
                input = input,
                provider = provider,
                model = model,
                baseUrl = config.baseUrl.ifBlank { prefs.baseUrl },
                apiKey = config.apiKey.ifBlank { prefs.apiKey },
                temperature = config.temperature.ifNaN(prefs.temperature),
                maxTokens = config.maxTokens.ifZero(prefs.maxTokens),
                systemPrompt = systemPrompt,
                assetType = assetType,
            )

            if (!success) {
                // Auto-fallback: coba provider lain
                val fallbackSuccess = tryFallbackChain(sid, input, systemPrompt, assetType)
                if (!fallbackSuccess) {
                    errorMessage.value = "Semua provider gagal. Periksa koneksi dan API key."
                }
            }

            loading.value = false
            streamingText.value = ""
        }
    }

    private suspend fun trySendWithProvider(
        sessionId: String,
        input: String,
        provider: String,
        model: String,
        baseUrl: String,
        apiKey: String,
        temperature: Float,
        maxTokens: Int,
        systemPrompt: String,
        assetType: AssetType,
    ): Boolean {
        return try {
            if (apiKey.isBlank()) return false

            val history = chatRepository.getMessagesOnce(sessionId)
            val (requestUrl, headers, body) = buildOpenAIRequest(
                baseUrl = baseUrl,
                apiKey = apiKey,
                model = model,
                systemPrompt = systemPrompt,
                messages = history,
                temperature = temperature,
                maxTokens = maxTokens,
            )

            appendErrorLog("Mencoba $provider / $model")

            var accumulatedText = ""
            var lastError = ""

            apiClient.executeStreaming(
                url = requestUrl,
                method = "POST",
                headersText = headers,
                body = body,
                onToken = { token ->
                    accumulatedText += token
                    streamingText.value = accumulatedText
                },
                onDone = {
                    if (accumulatedText.isNotBlank()) {
                        viewModelScope.launch {
                            chatRepository.addMessage(
                                sessionId, "assistant", accumulatedText,
                                assetType = assetType.name,
                            )
                            appendErrorLog("✅ Berhasil: $provider / $model")
                        }
                    }
                },
                onError = { err -> lastError = err },
            )

            if (accumulatedText.isNotBlank()) true
            else {
                appendErrorLog("❌ $provider / $model gagal: $lastError")
                false
            }
        } catch (e: Exception) {
            appendErrorLog("❌ $provider / $model error: ${e.message}")
            false
        }
    }

    /**
     * Fallback chain: coba provider lain saat provider utama gagal.
     */
    private suspend fun tryFallbackChain(
        sessionId: String,
        input: String,
        systemPrompt: String,
        assetType: AssetType,
    ): Boolean {
        val prefs = settingsStore.prefsFlow.first()

        // Dapatkan daftar provider dari konfigurasi yang punya API key
        val fallbackProviders = getAllProviderNames()
            .filter { it != prefs.apiProvider && it != "Custom" }
            .mapNotNull { providerName ->
                val config = getProviderConfig(prefs, providerName)
                if (config.apiKey.isNotBlank()) {
                    val models = getModelsForProvider(providerName)
                    val modelToUse = if (models.isNotEmpty()) models.first() else ""
                    Triple(providerName, modelToUse, config)
                } else null
            }

        for ((providerName, model, config) in fallbackProviders) {
            val success = trySendWithProvider(
                sessionId = sessionId,
                input = input,
                provider = providerName,
                model = model.ifBlank { config.model.ifBlank { getDefaultModel(providerName) } },
                baseUrl = config.baseUrl.ifBlank { getDefaultBaseUrl(providerName) },
                apiKey = config.apiKey,
                temperature = config.temperature.ifNaN(prefs.temperature),
                maxTokens = config.maxTokens.ifZero(prefs.maxTokens),
                systemPrompt = systemPrompt,
                assetType = assetType,
            )
            if (success) return true
        }
        return false
    }

    // ======================== BUILD REQUEST ========================

    private fun buildOpenAIRequest(
        baseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        messages: List<MessageEntity>,
        temperature: Float,
        maxTokens: Int,
    ): Triple<String, String, String> {
        val url = baseUrl.trim()
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("Authorization: Bearer $apiKey\n")
            append("Accept: application/json")
        }

        val historyMessages = JSONArray()
        historyMessages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemPrompt)
        })

        // Ambil 20 pesan terakhir untuk konteks
        val recentMessages = messages.takeLast(20)
        for (msg in recentMessages) {
            historyMessages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", historyMessages)
            put("temperature", temperature.toDouble())
            put("max_tokens", maxTokens)
            put("stream", true)
        }.toString()

        return Triple(url, headers, body)
    }

    private fun buildPredictionPrompt(assetType: AssetType, input: String): String {
        val assetLabel = assetType.label
        val timeframe = "24 jam"
        return buildString {
            appendLine("Kamu adalah analis $assetLabel profesional dan akurat.")
            appendLine()
            appendLine("Tugas:")
            appendLine("- Analisis $assetLabel: $input")
            appendLine("- Berikan prediksi arah harga (bullish/bearish/netral)")
            appendLine("- Sertakan level support dan resistance")
            appendLine("- Target waktu: $timeframe ke depan")
            appendLine("- Berikan analisis teknikal dan fundamental singkat")
            appendLine("- Sertakan level stop-loss yang disarankan")
            appendLine("- Tingkat keyakinan (0-100%)")
            appendLine()
            appendLine("Format jawaban:")
            appendLine("📊 **Analisis [Nama Aset]**")
            appendLine("🟢/🔴/⚪ **Arah: [Bullish/Bearish/Netral]**")
            appendLine("🎯 **Target:** [harga target]")
            appendLine("🛑 **Stop Loss:** [harga stop loss]")
            appendLine("📈 **Keyakinan:** [persentase]%")
            appendLine()
            appendLine("**Analisis Teknikal:**")
            appendLine("- Poin-poin analisis...")
            appendLine()
            appendLine("**Analisis Fundamental:**")
            appendLine("- Poin-poin analisis...")
            appendLine()
            appendLine("**Rekomendasi:**")
            appendLine("- Saran trading...")
            appendLine()
            appendLine("⚠️ *Disclaimer: Ini bukan saran keuangan. Trading memiliki risiko tinggi.*")
        }
    }

    // ======================== PROVIDER CONFIG HELPERS ========================

    fun getModelsForCurrentProvider(provider: String): List<String> {
        val defaultModels = getModelsForProvider(provider)
        return defaultModels
    }

    fun addCustomModel(model: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val provider = prefs.apiProvider
            val config = getProviderConfig(prefs, provider)
            val updated = config.copy(customModels = (config.customModels + model).distinct())
            val newConfigs = setProviderConfig(prefs, provider, updated)
            settingsStore.update { it.copy(providerConfigs = newConfigs) }
        }
    }

    fun removeCustomModel(model: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val provider = prefs.apiProvider
            val config = getProviderConfig(prefs, provider)
            val updated = config.copy(customModels = config.customModels - model)
            val newConfigs = setProviderConfig(prefs, provider, updated)
            settingsStore.update { it.copy(providerConfigs = newConfigs) }
        }
    }

    // ======================== BACKUP ========================

    fun createBackupJson(): String {
        var result = "{}"
        viewModelScope.launch {
            val backup = backupManager.createBackup()
            result = backupManager.serialize(backup)
        }
        return result
    }

    suspend fun restoreFromJson(json: String): Boolean {
        val backup = backupManager.deserialize(json) ?: return false
        return backupManager.restore(backup)
    }

    // ======================== HELPERS ========================

    private fun appendErrorLog(msg: String) {
        val current = errorLog.value
        errorLog.value = if (current.length > 2000) {
            current.takeLast(1500) + "\n$msg"
        } else {
            "$current\n$msg"
        }
    }

    private fun parseFavorites(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        settingsStore = container.settingsStore,
                        chatRepository = container.chatRepository,
                        apiClient = container.apiClient,
                        backupManager = container.backupManager,
                    ) as T
                }
            }
        }
    }
}

private fun truncatePreview(text: String): String {
    return text.replace("\n", " ").replace("\r", " ").trim().take(120)
        .let { if (it.length >= 120) "$it..." else it }
}

fun groupSessionsByDate(sessions: List<SessionPreview>): Map<String, List<SessionPreview>> {
    if (sessions.isEmpty()) return emptyMap()
    val now = java.util.Calendar.getInstance()
    val todayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = todayStart.clone() as java.util.Calendar
    yesterdayStart.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val thisWeekStart = todayStart.clone() as java.util.Calendar
    thisWeekStart.set(java.util.Calendar.DAY_OF_WEEK, thisWeekStart.getFirstDayOfWeek())
    val thisMonthStart = todayStart.clone() as java.util.Calendar
    thisMonthStart.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val groups = linkedMapOf<String, MutableList<SessionPreview>>()
    groups["Hari Ini"] = mutableListOf()
    groups["Kemarin"] = mutableListOf()
    groups["7 Hari Terakhir"] = mutableListOf()
    groups["Bulan Ini"] = mutableListOf()
    groups["Sebelumnya"] = mutableListOf()
    for (preview in sessions) {
        val time = preview.session.updatedAt
        val group = when {
            time >= todayStart.timeInMillis -> "Hari Ini"
            time >= yesterdayStart.timeInMillis -> "Kemarin"
            time >= thisWeekStart.timeInMillis -> "7 Hari Terakhir"
            time >= thisMonthStart.timeInMillis -> "Bulan Ini"
            else -> "Sebelumnya"
        }
        groups[group]!!.add(preview)
    }
    return groups.filter { it.value.isNotEmpty() }
}

private fun Float.ifNaN(default: Float): Float = if (isNaN()) default else this
private fun Int.ifZero(default: Int): Int = if (this == 0) default else this
