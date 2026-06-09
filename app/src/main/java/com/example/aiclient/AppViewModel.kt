package com.example.aiclient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.data.BackupData
import com.example.aiclient.data.BackupManager
import com.example.aiclient.data.ChatRepository
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.ProviderConfig
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.data.SettingsStore
import com.example.aiclient.data.getDefaultBaseUrl
import com.example.aiclient.data.getDefaultModel
import com.example.aiclient.data.getProviderConfig
import com.example.aiclient.data.setProviderConfig
import com.example.aiclient.data.getAllProviderNames
import com.example.aiclient.data.getModelsForProvider
import com.example.aiclient.data.getFallbackChain
import com.example.aiclient.network.ApiResult
import com.example.aiclient.network.GenericApiClient
import com.example.aiclient.network.toJsonString
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
enum class ConnectionStatus {
    IDLE, TESTING, CONNECTED, FAILED
}
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
    val responseCode: Int? = null,
    val responseMessage: String = "",
    val responseBody: String = "",
    val errorMessage: String = "",
    val sessionSearchQuery: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val connectionError: String = "",
    val errorLog: String = "",
)
private data class CoreUiState(
    val prefs: AppPrefs,
    val sessions: List<SessionPreview>,
    val messages: List<MessageEntity>,
    val currentSessionId: String,
    val connectionStatus: ConnectionStatus,
    val connectionError: String,
    val errorLog: String,
)
private data class NetworkUiState(
    val isLoading: Boolean,
    val responseCode: Int?,
    val responseMessage: String,
    val responseBody: String,
    val errorMessage: String,
)
private data class SessionState(
    val prefs: AppPrefs,
    val sessions: List<SessionPreview>,
    val messages: List<MessageEntity>,
    val id: String,
)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AppViewModel(
    private val settingsStore: SettingsStore,
    private val chatRepository: ChatRepository,
    private val apiClient: GenericApiClient,
    private val backupManager: BackupManager,
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val responseCode = MutableStateFlow<Int?>(null)
    private val responseMessage = MutableStateFlow("")
    private val responseBody = MutableStateFlow("")
    private val errorMessage = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")
    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    private val connectionError = MutableStateFlow("")
    private val errorLog = MutableStateFlow("")
    private val prefsFlow = settingsStore.prefsFlow
    private val sessionsFlow = chatRepository.observeSessions()
    private val lastMessagesFlow = chatRepository.observeLastMessagesForAllSessions()
    private val sessionPreviewsFlow = combine(
        sessionsFlow,
        lastMessagesFlow,
    ) { sessions, lastMessages ->
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
    private val filteredSessionsFlow = combine(
        sessionPreviewsFlow,
        searchQuery,
    ) { previews, query ->
        if (query.isBlank()) previews
        else previews.filter { it.session.title.contains(query, ignoreCase = true) }
    }
    private val currentSessionIdFlow = prefsFlow.map { it.activeSessionId }
    private val messagesFlow = prefsFlow.flatMapLatest { prefs ->
        val sessionId = prefs.activeSessionId
        if (sessionId.isBlank()) {
            kotlinx.coroutines.flow.flowOf(emptyList<MessageEntity>())
        } else {
            chatRepository.observeMessages(sessionId)
        }
    }
    private val coreUiStateFlow = combine(
        combine(prefsFlow, filteredSessionsFlow, messagesFlow, currentSessionIdFlow) { prefs, sessions, messages, id ->
            SessionState(prefs, sessions, messages, id)
        },
        combine(connectionStatus, connectionError) { connStatus, connError ->
            connStatus to connError
        },
        errorLog,
    ) { session, connPair, el ->
        CoreUiState(
            prefs = session.prefs,
            sessions = session.sessions,
            messages = session.messages,
            currentSessionId = session.id,
            connectionStatus = connPair.first,
            connectionError = connPair.second,
            errorLog = el,
        )
    }
    private val networkUiStateFlow = combine(
        loading,
        responseCode,
        responseMessage,
        responseBody,
        errorMessage,
    ) { isLoading, code, message, body, error ->
        NetworkUiState(
            isLoading = isLoading,
            responseCode = code,
            responseMessage = message,
            responseBody = body,
            errorMessage = error,
        )
    }
    val uiState: StateFlow<UiState> = combine(
        coreUiStateFlow,
        networkUiStateFlow,
    ) { core, network ->
        UiState(
            prefs = core.prefs,
            sessions = core.sessions,
            groupedSessions = groupSessionsByDate(core.sessions),
            messages = core.messages,
            currentSessionId = core.currentSessionId,
            isLoading = network.isLoading,
            responseCode = network.responseCode,
            responseMessage = network.responseMessage,
            responseBody = network.responseBody,
            errorMessage = network.errorMessage,
            sessionSearchQuery = searchQuery.value,
            connectionStatus = core.connectionStatus,
            connectionError = core.connectionError,
            errorLog = core.errorLog,
        )
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        UiState(),
    )
    init {
        viewModelScope.launch {
            bootstrapSession()
        }
    }
    private suspend fun bootstrapSession() {
        val prefs = settingsStore.prefsFlow.first()
        val activeSession = prefs.activeSessionId
            .takeIf { it.isNotBlank() }
            ?.let { chatRepository.getSessionOnce(it) }
        val session = activeSession ?: run {
            val existing = chatRepository.observeSessions().first().firstOrNull()
            existing ?: chatRepository.createSession("Chat Baru").also {
                sendAutoGreeting(it.id)
            }
        }
        settingsStore.update { it.copy(activeSessionId = session.id) }
    }
    // --- AI Chat settings (per-provider) ---
    fun updateApiKey(value: String) = persistPrefsWithProvider { it.copy(apiKey = value) }
    fun updateModel(value: String) = persistPrefsWithProvider { it.copy(model = value) }
    fun updateBaseUrl(value: String) = persistPrefsWithProvider { it.copy(baseUrl = value) }
    fun updateTemperature(value: Float) = persistPrefsWithProvider { it.copy(temperature = value) }
    fun updateMaxTokens(value: Int) = persistPrefsWithProvider { it.copy(maxTokens = value) }
    fun updateGlobalMemory(value: String) = persistPrefs { it.copy(globalMemory = value) }
    fun updateProvider(value: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            // Save current provider's config
            val currentConfig = ProviderConfig(
                apiKey = prefs.apiKey,
                model = prefs.model,
                baseUrl = prefs.baseUrl,
                temperature = prefs.temperature,
                maxTokens = prefs.maxTokens,
            )
            val configsSaved = setProviderConfig(prefs, prefs.apiProvider, currentConfig)
            // Load new provider's config
            val updatedPrefs = prefs.copy(providerConfigs = configsSaved)
            val newConfig = getProviderConfig(updatedPrefs, value)
            settingsStore.update {
                updatedPrefs.copy(
                    apiProvider = value,
                    apiKey = newConfig.apiKey,
                    model = newConfig.model.ifEmpty { getDefaultModel(value) },
                    baseUrl = newConfig.baseUrl.ifEmpty { getDefaultBaseUrl(value) },
                    temperature = newConfig.temperature,
                    maxTokens = newConfig.maxTokens,
                )
            }
        }
    }
    // --- Legacy settings ---
    fun updateEndpointUrl(value: String) = persistPrefs { it.copy(endpointUrl = value) }
    fun updateMethod(value: String) = persistPrefs { it.copy(method = value) }
    fun updateHeaders(value: String) = persistPrefs { it.copy(defaultHeaders = value) }
    fun updateBodyTemplate(value: String) = persistPrefs { it.copy(bodyTemplate = value) }
    fun updateActiveSession(sessionId: String) = persistPrefs { it.copy(activeSessionId = sessionId) }
    fun updateSessionSearch(value: String) { searchQuery.value = value }
    // --- Test Connection ---
    fun clearErrorLog() {
        errorLog.value = ""
        appendErrorLog("Log dibersihkan")
    }
    fun testConnection() {
        viewModelScope.launch {
            connectionStatus.value = ConnectionStatus.TESTING
            connectionError.value = ""
            appendErrorLog("Test koneksi dimulai...")
            val prefs = uiState.value.prefs
            if (prefs.apiKey.isBlank()) {
                connectionStatus.value = ConnectionStatus.FAILED
                appendErrorLog("Test koneksi gagal: API Key belum diisi")
                connectionError.value = "API Key belum diisi"
                return@launch
            }
            if (prefs.baseUrl.isBlank()) {
                connectionStatus.value = ConnectionStatus.FAILED
                appendErrorLog("Test koneksi gagal: Base URL belum diisi")
                connectionError.value = "Base URL belum diisi"
                return@launch
            }
            val provider = prefs.apiProvider
            val (url, headers, body) = when {
                provider.equals("Google", ignoreCase = true) -> {
                    val googleUrl = prefs.baseUrl.trimEnd('/') + "/${prefs.model}:generateContent?key=${prefs.apiKey}"
                    val googleHeaders = "Content-Type: application/json"
                    val googleBody = """{"contents": [{"parts": [{"text": "Hello"}]}]}"""
                    Triple(googleUrl, googleHeaders, googleBody)
                }
                provider.equals("Anthropic", ignoreCase = true) -> {
                    val anthropicHeaders = buildString {
                        append("Content-Type: application/json\n")
                        append("anthropic-version: 2023-06-01\n")
                        append("x-api-key: ${prefs.apiKey}")
                    }
                    val anthropicBody = buildString {
                        appendLine("{")
                        appendLine("  \"model\": \"${prefs.model}\",")
                        appendLine("  \"max_tokens\": 5,")
                        appendLine("  \"messages\": [")
                        appendLine("    {\"role\": \"user\", \"content\": \"Hello\"}")
                        appendLine("  ]")
                        append("}")
                    }
                    Triple(prefs.baseUrl, anthropicHeaders, anthropicBody)
                }
                else -> {
                    // OpenAI / Deepseek / compatible
                    val stdHeaders = buildString {
                        append("Content-Type: application/json\n")
                        append("Authorization: Bearer ${prefs.apiKey}")
                    }
                    val stdBody = buildString {
                        appendLine("{")
                        appendLine("  \"model\": \"${prefs.model}\",")
                        appendLine("  \"messages\": [")
                        appendLine("    {\"role\": \"user\", \"content\": \"Hello\"}")
                        appendLine("  ],")
                        appendLine("  \"max_tokens\": 5")
                        append("}")
                    }
                    Triple(prefs.baseUrl, stdHeaders, stdBody)
                }
            }
            runCatching {
                apiClient.execute(
                    url = url,
                    method = "POST",
                    headersText = headers,
                    body = body,
                )
            }.onSuccess { result ->
                if (result.statusCode in 200..299) {
                    connectionStatus.value = ConnectionStatus.CONNECTED
                    appendErrorLog("Test koneksi berhasil ke ${prefs.apiProvider}")
                    connectionError.value = ""
                } else {
                    val preview = result.responseBody.take(200).replace("\n", " ").trim()
                    connectionStatus.value = ConnectionStatus.FAILED
                    appendErrorLog("Test koneksi gagal: HTTP ${result.statusCode}")
                    connectionError.value = "HTTP ${result.statusCode} ${result.statusMessage}\n$preview"
                }
            }.onFailure { throwable ->
                val msg = throwable.message ?: throwable::class.java.simpleName
                connectionStatus.value = ConnectionStatus.FAILED
                appendErrorLog("Test koneksi gagal: $msg")
                connectionError.value = msg
            }
        }
    }
    fun createSession() {
        viewModelScope.launch {
            val session = chatRepository.createSession("Chat Baru")
            updateActiveSession(session.id)
        }
    }
    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            val currentId = uiState.value.currentSessionId
            chatRepository.deleteSession(sessionId)
            if (sessionId == currentId) {
                val sessions = chatRepository.observeSessions().first()
                val nextSession = sessions.firstOrNull()
                if (nextSession != null) {
                    updateActiveSession(nextSession.id)
                } else {
                    createSession()
                }
            }
        }
    }
    // --- Send Request ---
    fun sendRequest(input: String = "", imageBase64: String = "") {
        viewModelScope.launch {
            loading.value = true
            try {
                errorMessage.value = ""
                responseCode.value = null
                responseMessage.value = ""
                responseBody.value = ""
                val prefs = uiState.value.prefs
                if (prefs.apiKey.isBlank() && prefs.apiProvider != "Custom") {
                    errorMessage.value = "API Key belum diatur. Silakan isi di menu Pengaturan."
                    loading.value = false
                    return@launch
                }
                val session = ensureCurrentSession(prefs)
                // Load messages from ALL sessions for cross-session memory
                val allHistory = chatRepository.getAllMessagesOnce()
                val inputForRename = (if (input.isNotBlank()) input else "Gambar").take(28).trim()
                val (requestUrl, headers, body) = buildRequest(prefs, allHistory, input, imageBase64)
                if (input.isNotBlank() || imageBase64.isNotBlank()) {
                    chatRepository.addMessage(session.id, "request", input, imageBase64)
                }
                runCatching {
                    apiClient.execute(
                        url = requestUrl,
                        method = "POST",
                        headersText = headers,
                        body = body,
                    )
                }.onSuccess { result ->
                    if (result.statusCode >= 400) {
                        val fbInput = if (input.isNotBlank()) input else imageBase64.take(100)
                        val fallback = tryFallback(session.id, fbInput, result.responseBody, imageBase64)
                        if (!fallback) {
                            handleError(session.id, fbInput.ifBlank { body }, Exception("HTTP ${result.statusCode}: ${result.responseBody.take(200)}"))
                        }
                    } else {
                        handleSuccess(session.id, result, inputForRename)
                    }
                }.onFailure { throwable ->
                    val fbInput = if (input.isNotBlank()) input else imageBase64.take(100)
                    val fallback = tryFallback(session.id, fbInput, throwable.message ?: "", imageBase64)
                    if (!fallback) handleError(session.id, fbInput.ifBlank { body }, throwable)
                }
            } finally {
                loading.value = false
            }
        }
    }
    fun editMessageAndRegenerate(messageId: String, newContent: String, imageBase64: String = "") {
        viewModelScope.launch {
            loading.value = true
            try {
                val prefs = uiState.value.prefs
                if (prefs.apiKey.isBlank() && prefs.apiProvider != "Custom") {
                    errorMessage.value = "API Key belum diatur."
                    loading.value = false
                    return@launch
                }
                val sessionId = uiState.value.currentSessionId
                if (sessionId.isBlank()) { loading.value = false; return@launch }
                // Get all messages and find the edit point
                val allMessages = chatRepository.getMessagesOnce(sessionId)
                val msgIndex = allMessages.indexOfFirst { it.id == messageId }
                if (msgIndex < 0) { loading.value = false; return@launch }
                // Keep messages BEFORE the edit point
                val keptMessages = allMessages.take(msgIndex)
                // Rebuild session: delete all, re-insert kept, add edited message
                chatRepository.restoreAll(
                    listOf(chatRepository.getSessionOnce(sessionId) ?: return@launch),
                    keptMessages
                )
                // Add the edited message as new request
                chatRepository.addMessage(sessionId, "request", newContent)
                // Send request with all history + new message
                errorMessage.value = ""
                val allHistory = chatRepository.getAllMessagesOnce()
                val (requestUrl, headers, body) = buildRequest(prefs, allHistory, newContent, imageBase64)
                runCatching {
                    apiClient.execute(url = requestUrl, method = "POST", headersText = headers, body = body)
                }.onSuccess { result ->
                    handleSuccess(sessionId, result, newContent.take(28).trim())
                }.onFailure { throwable ->
                    handleError(sessionId, newContent, throwable)
                }
            } finally {
                loading.value = false
            }
        }
    }
    private fun buildCustomRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val historyText = history.joinToString("\n\n") { "${it.role}: ${it.content}" }
        val renderedBody = apiClient.renderTemplate(
            template = prefs.bodyTemplate,
            input = input,
            memory = prefs.globalMemory,
            history = historyText,
            model = prefs.model,
            temperature = prefs.temperature,
            maxTokens = prefs.maxTokens,
            apiKey = prefs.apiKey,
        )
        return Triple(prefs.baseUrl.ifBlank { prefs.endpointUrl }, prefs.defaultHeaders, renderedBody)
    }
    private fun buildRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String, imageBase64: String = ""): Triple<String, String, String> {
        if (prefs.apiKey.isBlank()) {
            val (h, b) = buildCustomRequest(prefs, history, input)
            return Triple(prefs.baseUrl.ifBlank { prefs.endpointUrl }, h, b)
        }
        // Auto-switch ke vision model jika ada gambar
        val effectivePrefs = if (imageBase64.isNotBlank()) {
            val visionModel = getVisionModel(prefs.apiProvider)
            if (visionModel != null && prefs.model != visionModel) {
                prefs.copy(model = visionModel)
            } else prefs
        } else prefs
        return when (effectivePrefs.apiProvider) {
            "Google" -> buildGoogleRequest(effectivePrefs, history, input, imageBase64)
            "Anthropic" -> buildAnthropicRequest(effectivePrefs, history, input, imageBase64)
            else -> buildOpenAiRequest(effectivePrefs, history, input, imageBase64)
        }
    }
    private fun buildOpenAiRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String, imageBase64: String = ""): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("Authorization: Bearer ${prefs.apiKey}")
        }
        val messages = mutableListOf<String>()
        val now = java.util.Calendar.getInstance()
        val timeCtx = getCurrentTimeContext(now)
        val sysContent = if (prefs.globalMemory.isNotBlank()) {
            "$timeCtx\n${prefs.globalMemory}"
        } else timeCtx
        messages.add("""{"role": "system", "content": ${sysContent.toJsonString()}}""")
        var currentSessionId = ""
        for (msg in history) {
            if (msg.sessionId != currentSessionId) {
                currentSessionId = msg.sessionId
            }
            val role = when (msg.role) {
                "request" -> "user"
                "response" -> "assistant"
                "error" -> "assistant"
                else -> msg.role
            }
            if (msg.imageBase64.isNotBlank()) {
                messages.add("""{"role": "${role}", "content": [{"type": "text", "text": ${msg.content.toJsonString()}}, {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,${msg.imageBase64}"}}]}""")
            } else {
                messages.add("""{"role": "${role}", "content": ${msg.content.toJsonString()}}""")
            }
        }
        if (input.isNotBlank()) {
            val timeStr = getTimeString(now)
            val userContent = "[Waktu: $timeStr]\n\n$input"
            if (imageBase64.isNotBlank()) {
                messages.add("""{"role": "user", "content": [{"type": "text", "text": ${userContent.toJsonString()}}, {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,${imageBase64}"}}]}""")
            } else {
                messages.add("""{"role": "user", "content": ${userContent.toJsonString()}}""")
            }
        }
        val messagesJson = messages.joinToString(",\n")
        val body = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${prefs.model}\",")
            appendLine("  \"messages\": [")
            appendLine("    $messagesJson")
            appendLine("  ],")
            appendLine("  \"temperature\": ${prefs.temperature},")
            appendLine("  \"max_tokens\": ${prefs.maxTokens}")
            append("}")
        }
        return Triple(prefs.baseUrl, headers, body)
    }
    private fun buildGoogleRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String, imageBase64: String = ""): Triple<String, String, String> {
        val url = prefs.baseUrl.trimEnd('/') + "/${prefs.model}:generateContent?key=${prefs.apiKey}"
        val headers = "Content-Type: application/json"
        val contents = mutableListOf<String>()
        val now = java.util.Calendar.getInstance()
        val timeCtx = getCurrentTimeContext(now)
        // History
        for (msg in history) {
            val role = when (msg.role) {
                "request" -> "user"
                "response" -> "model"
                "error" -> "model"
                else -> msg.role
            }
            if (msg.imageBase64.isNotBlank()) {
                contents.add("""{"role": "${role}", "parts": [{"text": ${msg.content.toJsonString()}}, {"inline_data": {"mime_type": "image/jpeg", "data": "${msg.imageBase64}"}}]}""")
            } else {
                contents.add("""{"role": "${role}", "parts": [{"text": ${msg.content.toJsonString()}}]}""")
            }
        }
        // Current input
        if (input.isNotBlank()) {
            val timeStr = getTimeString(now)
            val userContent = "[Waktu: $timeStr]\n\n$input"
            if (imageBase64.isNotBlank()) {
                contents.add("""{"role": "user", "parts": [{"text": ${userContent.toJsonString()}}, {"inline_data": {"mime_type": "image/jpeg", "data": "${imageBase64}"}}]}""")
            } else {
                contents.add("""{"role": "user", "parts": [{"text": ${userContent.toJsonString()}}]}""")
            }
        }
        val contentsJson = contents.joinToString(",\n")
        val sysContentG = if (prefs.globalMemory.isNotBlank()) {
            "$timeCtx\n${prefs.globalMemory}"
        } else timeCtx
        val body = buildString {
            appendLine("{")
            appendLine("  \"system_instruction\": {\"parts\": [{\"text\": ${sysContentG.toJsonString()}}]},")
            appendLine("  \"contents\": [")
            appendLine("    $contentsJson")
            appendLine("  ],")
            appendLine("  \"generationConfig\": {")
            appendLine("    \"temperature\": ${prefs.temperature},")
            appendLine("    \"maxOutputTokens\": ${prefs.maxTokens}")
            appendLine("  }")
            append("}")
        }
        return Triple(url, headers, body)
    }
    private fun buildAnthropicRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String, imageBase64: String = ""): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("x-api-key: ${prefs.apiKey}\n")
            append("anthropic-version: 2023-06-01")
        }
        val messages = mutableListOf<String>()
        val now = java.util.Calendar.getInstance()
        val timeCtx = getCurrentTimeContext(now)
        for (msg in history) {
            val role = when (msg.role) {
                "request" -> "user"
                "response" -> "assistant"
                "error" -> "assistant"
                else -> msg.role
            }
            messages.add("""{"role": "${role}", "content": ${msg.content.toJsonString()}}""")
        }
        if (input.isNotBlank()) {
            val timeStr = getTimeString(now)
            val userContent = "[Waktu: $timeStr]\n\n$input"
            messages.add("""{"role": "user", "content": ${userContent.toJsonString()}}""")
        }
        val messagesJson = messages.joinToString(",\n")
        val sysContentA = if (prefs.globalMemory.isNotBlank()) {
            "$timeCtx\n${prefs.globalMemory}"
        } else timeCtx
        val body = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${prefs.model}\",")
            appendLine("  \"max_tokens\": ${prefs.maxTokens},")
            appendLine("  \"system\": ${sysContentA.toJsonString()},")
            appendLine("  \"messages\": [")
            appendLine("    $messagesJson")
            appendLine("  ]")
            append("}")
        }
        return Triple(prefs.baseUrl, headers, body)
    }
    /**
     * Extract text content from API response JSON based on provider format.
     */
    private fun extractResponseText(provider: String, responseBody: String): String {
        if (responseBody.isBlank()) return ""
        return try {
            val json = JSONObject(responseBody)
            when (provider) {
                "Google" -> {
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            parts.getJSONObject(0).optString("text", responseBody)
                        } else responseBody
                    } else responseBody
                }
                "Anthropic" -> {
                    val contentArr = json.optJSONArray("content")
                    if (contentArr != null && contentArr.length() > 0) {
                        contentArr.getJSONObject(0).optString("text", responseBody)
                    } else responseBody
                }
                else -> {
                    // OpenAI / Deepseek / compatible
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        message?.optString("content", responseBody) ?: responseBody
                    } else {
                        // Fallback: try to find any text field
                        json.optString("text", responseBody)
                    }
                }
            }
        } catch (e: Exception) {
            // If parsing fails, return raw body
            responseBody
        }
    }
    private suspend fun ensureCurrentSession(prefs: AppPrefs): SessionEntity {
        val activeId = prefs.activeSessionId
        val existing = activeId.takeIf { it.isNotBlank() }?.let { chatRepository.getSessionOnce(it) }
        return existing ?: run {
            val session = chatRepository.createSession("Chat Baru")
            settingsStore.update { it.copy(activeSessionId = session.id) }
            session
        }
    }
    private suspend fun handleSuccess(sessionId: String, result: ApiResult, inputForRename: String) {
        responseCode.value = result.statusCode
        responseMessage.value = result.statusMessage
        // Check for API errors (non-2xx status)
        if (result.statusCode >= 400) {
            val errorPreview = try {
                val json = JSONObject(result.responseBody)
                val err = json.optJSONObject("error")
                if (err != null) {
                    err.optString("message", json.optString("message", result.responseBody))
                } else {
                    json.optString("message", result.responseBody)
                }
            } catch (_: Exception) { result.responseBody }
            val cleanError = errorPreview.take(300).replace("\n", " ")
            val msg = "HTTP ${result.statusCode}: ${cleanError}"
            errorMessage.value = msg
            responseBody.value = result.responseBody
            chatRepository.addMessage(sessionId, "error", msg)
            return
        }
        val prefs = uiState.value.prefs
        val extractedText = extractResponseText(prefs.apiProvider, result.responseBody)
        val displayText = extractedText
        responseBody.value = displayText
        chatRepository.addMessage(sessionId, "response", displayText)
        val currentSession = chatRepository.getSessionOnce(sessionId)
        val shouldAutoRename = currentSession?.title?.startsWith("Chat") != false || currentSession?.title?.startsWith("Sesi") != false
        if (shouldAutoRename) {
            val newTitle = inputForRename.ifBlank { "Chat ${sessionId.take(4)}" }
            chatRepository.renameSession(sessionId, newTitle)
        }
    }
    private fun appendErrorLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val entry = "[$timestamp] $message"
        val current = errorLog.value
        val updated = if (current.length > 10000) {
            current.takeLast(9000) + "\n...\n" + entry
        } else {
            current + "\n" + entry
        }
        errorLog.value = updated
    }
    private suspend fun handleError(sessionId: String, renderedBody: String, throwable: Throwable) {
        val message = throwable.message ?: throwable::class.java.simpleName
        val cleanMsg = message.take(300).replace("\n", " ")
        appendErrorLog("Error: $cleanMsg")
        errorMessage.value = cleanMsg
        responseBody.value = renderedBody
        chatRepository.addMessage(sessionId, "error", cleanMsg)
    }
    private fun sendAutoGreeting(sessionId: String) {
        viewModelScope.launch {
            try {
                val prefs = settingsStore.prefsFlow.first()
                if (prefs.apiKey.isBlank()) {
                    val welcome = "Selamat datang! Saya adalah asisten AI Anda. Silakan atur API Key di menu Pengaturan untuk mulai menggunakan AI."
                    chatRepository.addMessage(sessionId, "response", welcome)
                    return@launch
                }
                val greetingPrompt = "Sapa pengguna dengan ramah. Perkenalkan dirimu sebagai asisten AI yang membantu."
                val (requestUrl, headers, body) = buildRequest(prefs, emptyList(), greetingPrompt)
                runCatching {
                    apiClient.execute(url = requestUrl, method = "POST", headersText = headers, body = body)
                }.onSuccess { result ->
                    if (result.statusCode in 200..299) {
                        val text = extractResponseText(prefs.apiProvider, result.responseBody)
                        chatRepository.addMessage(sessionId, "response", text)
                    }
                }.onFailure { }
            } catch (_: Exception) { }
        }
    }
    private fun persistPrefs(transform: (AppPrefs) -> AppPrefs) {
        viewModelScope.launch {
            settingsStore.update(transform)
        }
    }
    private fun persistPrefsWithProvider(transform: (AppPrefs) -> AppPrefs) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val updated = transform(prefs)
            // Save to per-provider config as well
            val config = ProviderConfig(
                apiKey = updated.apiKey,
                model = updated.model,
                baseUrl = updated.baseUrl,
                temperature = updated.temperature,
                maxTokens = updated.maxTokens,
            )
            val newProviderConfigs = setProviderConfig(updated, updated.apiProvider, config)
            settingsStore.update { updated.copy(providerConfigs = newProviderConfigs) }
        }
    }
    // --- Backup / Restore ---
    fun createBackupData(): BackupData? {
        var result: BackupData? = null
        viewModelScope.launch {
            result = backupManager.createBackup()
        }
        return result
    }
    suspend fun createBackupJson(): String {
        val backup = backupManager.createBackup()
        return backupManager.serialize(backup)
    }
    suspend fun restoreFromJson(jsonString: String): Boolean {
        val backup = backupManager.deserialize(jsonString) ?: return false
        return backupManager.restore(backup)
    }
    private fun getCurrentTimeContext(now: java.util.Calendar = java.util.Calendar.getInstance()): String {
        val days = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val months = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val dayName = days[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val monthName = months[now.get(java.util.Calendar.MONTH)]
        val date = now.get(java.util.Calendar.DAY_OF_MONTH)
        val year = now.get(java.util.Calendar.YEAR)
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        val tz = java.text.SimpleDateFormat("z", java.util.Locale.getDefault()).format(now.time)
        return """Hari ini: $dayName, $date $monthName $year. Waktu: $hour:$minute $tz.
Kamu adalah asisten AI yang membantu dan ramah."""
    }
    private fun getTimeString(now: java.util.Calendar): String {
        val hour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = now.get(java.util.Calendar.MINUTE)
        val tz = java.text.SimpleDateFormat("z", java.util.Locale.getDefault()).format(now.time)
        val days = arrayOf("Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu")
        val months = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
        val dayName = days[now.get(java.util.Calendar.DAY_OF_WEEK) - 1]
        val monthName = months[now.get(java.util.Calendar.MONTH)]
        val date = now.get(java.util.Calendar.DAY_OF_MONTH)
        val year = now.get(java.util.Calendar.YEAR)
        return "$dayName, $date $monthName $year $hour:$minute $tz"
    }
    // Fallback providers/models ordered by priority
    private val fallbackChain: List<Pair<String, List<String>>> by lazy {
        getFallbackChain()
    }
    // Vision-capable models per provider (auto-switch saat kirim gambar)
    private val visionModels: Map<String, String> = mapOf(
        "OpenAI" to "gpt-4o",
        "Anthropic" to "claude-3-5-sonnet-20241022",
        "Google" to "gemini-2.5-flash",
        "Deepseek" to "deepseek-chat",
        "Groq" to "llama-3.2-11b-vision-preview",
        "OpenRouter" to "openai/gpt-4o",
    )
    private fun getVisionModel(provider: String): String? = visionModels[provider]

    private suspend fun tryFallback(sessionId: String, originalInput: String, errorHint: String, imageBase64: String = ""): Boolean {
        val prefs = settingsStore.prefsFlow.first()
        val currentProvider = prefs.apiProvider
        val currentModel = prefs.model

        // Kumpulkan semua provider yang punya API key
        val providersWithKeys = getAllProviderNames().filter { p ->
            if (p == "Custom") return@filter false
            getProviderConfig(prefs, p).apiKey.isNotBlank()
        }
        if (providersWithKeys.isEmpty()) return false

        // Urutkan: provider yang sedang aktif didahulukan
        val orderedProviders = listOf(currentProvider) + (providersWithKeys - currentProvider)

        // Kumpulkan semua kandidat (provider, model) untuk dicoba
        data class Candidate(val provider: String, val model: String)
        val candidates = mutableListOf<Candidate>()

        for (provider in orderedProviders) {
            val models = getModelsForProvider(provider)
            if (models.isEmpty()) continue
            val config = getProviderConfig(prefs, provider)

            // Saat ada gambar, prioritaskan vision model
            if (imageBase64.isNotBlank()) {
                val visionModel = getVisionModel(provider)
                if (visionModel != null && models.contains(visionModel)) {
                    candidates.add(Candidate(provider, visionModel))
                }
            }

            // Tambahkan semua model dari provider ini (kecuali yang sudah ditambahkan)
            for (model in models) {
                if (!candidates.any { it.provider == provider && it.model == model }) {
                    candidates.add(Candidate(provider, model))
                }
            }
        }

        // Mulai dari model setelah currentModel di currentProvider
        val startIndex = candidates.indexOfFirst { it.provider == currentProvider && it.model == currentModel }
        val startFrom = if (startIndex >= 0) startIndex + 1 else 0

        // Coba satu per satu
        for (i in startFrom until candidates.size) {
            val (provider, model) = candidates[i]
            if (provider == currentProvider && model == currentModel) continue // skip model yg sama
            val ok = tryRetryWithModel(sessionId, originalInput, provider, model, imageBase64)
            if (ok) return true
        }

        return false
    }
    private suspend fun tryRetryWithModel(sessionId: String, input: String, provider: String, model: String, imageBase64: String = ""): Boolean {
        try {
            val prefs = settingsStore.prefsFlow.first()
            val config = getProviderConfig(prefs, provider)
            if (config.apiKey.isBlank()) return false
            // Temporarily switch to fallback config
            val fallbackPrefs = prefs.copy(
                apiProvider = provider,
                apiKey = config.apiKey,
                model = model,
                baseUrl = config.baseUrl.ifBlank { getDefaultBaseUrl(provider) },
                temperature = config.temperature,
                maxTokens = config.maxTokens,
            )
            val history = chatRepository.getMessagesOnce(sessionId)
            val (requestUrl, headers, body) = buildRequest(fallbackPrefs, history, input, imageBase64)
            val result = apiClient.execute(url = requestUrl, method = "POST", headersText = headers, body = body)
            if (result.statusCode in 200..299) {
                appendErrorLog("Fallback berhasil: $provider / $model")
                val text = extractResponseText(provider, result.responseBody)
                responseBody.value = text
                chatRepository.addMessage(sessionId, "response", text)
                // Auto-rename with fallback prefix
                val session = chatRepository.getSessionOnce(sessionId)
                if (session?.title?.startsWith("Chat") != false || session?.title?.startsWith("Sesi") != false) {
                    chatRepository.renameSession(sessionId, input.take(28).trim().ifBlank { "Chat (${provider.first()})" })
                }
                return true
            }
        } catch (_: Exception) { }
        return false
    }
    fun addCustomModel(model: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val provider = prefs.apiProvider
            val newConfigs = com.example.aiclient.data.addCustomModel(prefs, provider, model)
            settingsStore.update { it.copy(providerConfigs = newConfigs) }
        }
    }
    fun removeCustomModel(model: String) {
        viewModelScope.launch {
            val prefs = settingsStore.prefsFlow.first()
            val provider = prefs.apiProvider
            val newConfigs = com.example.aiclient.data.removeCustomModel(prefs, provider, model)
            settingsStore.update { it.copy(providerConfigs = newConfigs) }
        }
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
    return text.replace("\n", " ")
        .replace("\r", " ")
        .trim()
        .take(120)
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
