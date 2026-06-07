package com.example.aiclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.data.ChatRepository
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.ProviderConfig
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.data.SettingsStore
import com.example.aiclient.data.getDefaultBaseUrl
import com.example.aiclient.data.getDefaultModel
import com.example.aiclient.data.getProviderConfig
import com.example.aiclient.data.setProviderConfig
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
)

private data class CoreUiState(
    val prefs: AppPrefs,
    val sessions: List<SessionPreview>,
    val messages: List<MessageEntity>,
    val currentSessionId: String,
    val connectionStatus: ConnectionStatus,
    val connectionError: String,
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

class AppViewModel(
    private val settingsStore: SettingsStore,
    private val chatRepository: ChatRepository,
    private val apiClient: GenericApiClient,
) : ViewModel() {
    private val loading = MutableStateFlow(false)
    private val responseCode = MutableStateFlow<Int?>(null)
    private val responseMessage = MutableStateFlow("")
    private val responseBody = MutableStateFlow("")
    private val errorMessage = MutableStateFlow("")
    private val searchQuery = MutableStateFlow("")

    private val connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    private val connectionError = MutableStateFlow("")

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
        }
    ) { session, connPair ->
        CoreUiState(
            prefs = session.prefs,
            sessions = session.sessions,
            messages = session.messages,
            currentSessionId = session.id,
            connectionStatus = connPair.first,
            connectionError = connPair.second,
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
            existing ?: chatRepository.createSession("Chat Baru")
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
    fun testConnection() {
        viewModelScope.launch {
            connectionStatus.value = ConnectionStatus.TESTING
            connectionError.value = ""

            val prefs = uiState.value.prefs
            if (prefs.apiKey.isBlank()) {
                connectionStatus.value = ConnectionStatus.FAILED
                connectionError.value = "API Key belum diisi"
                return@launch
            }
            if (prefs.baseUrl.isBlank()) {
                connectionStatus.value = ConnectionStatus.FAILED
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
                    connectionError.value = ""
                } else {
                    val preview = result.responseBody.take(200).replace("\n", " ").trim()
                    connectionStatus.value = ConnectionStatus.FAILED
                    connectionError.value = "HTTP ${result.statusCode} ${result.statusMessage}\n$preview"
                }
            }.onFailure { throwable ->
                val msg = throwable.message ?: throwable::class.java.simpleName
                connectionStatus.value = ConnectionStatus.FAILED
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
    fun sendRequest(input: String = "") {
        viewModelScope.launch {
            loading.value = true
            try {
                errorMessage.value = ""
                responseCode.value = null
                responseMessage.value = ""
                responseBody.value = ""

                val prefs = uiState.value.prefs
                val session = ensureCurrentSession(prefs)
                val history = chatRepository.getMessagesOnce(session.id)

                val inputForRename = input.take(28).trim()
                val (requestUrl, headers, body) = buildRequest(prefs, history, input)

                chatRepository.addMessage(session.id, "request", input)

                runCatching {
                    apiClient.execute(
                        url = requestUrl,
                        method = "POST",
                        headersText = headers,
                        body = body,
                    )
                }.onSuccess { result ->
                    handleSuccess(session.id, result, inputForRename)
                }.onFailure { throwable ->
                    handleError(session.id, input.ifBlank { body }, throwable)
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
    private fun buildRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        if (prefs.apiKey.isBlank()) {
            val (h, b) = buildCustomRequest(prefs, history, input)
            return Triple(prefs.baseUrl.ifBlank { prefs.endpointUrl }, h, b)
        }
        return when (prefs.apiProvider) {
            "Google" -> buildGoogleRequest(prefs, history, input)
            "Anthropic" -> buildAnthropicRequest(prefs, history, input)
            else -> buildOpenAiRequest(prefs, history, input)
        }
    }

    private fun buildOpenAiRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("Authorization: Bearer ${prefs.apiKey}")
        }

        val messages = mutableListOf<String>()

        if (prefs.globalMemory.isNotBlank()) {
            messages.add("""{"role": "system", "content": ${prefs.globalMemory.toJsonString()}}""")
        }

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
            messages.add("""{"role": "user", "content": ${input.toJsonString()}}""")
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

    private fun buildGoogleRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val url = prefs.baseUrl.trimEnd('/') + "/${prefs.model}:generateContent?key=${prefs.apiKey}"
        val headers = "Content-Type: application/json"

        val contents = mutableListOf<String>()

        // System instruction as top-level field, not in contents
        // Google uses system_instruction for system prompts

        // History
        for (msg in history) {
            val role = when (msg.role) {
                "request" -> "user"
                "response" -> "model"
                "error" -> "model"
                else -> msg.role
            }
            contents.add("""{"role": "${role}", "parts": [{"text": ${msg.content.toJsonString()}}]}""")
        }

        // Current input
        if (input.isNotBlank()) {
            contents.add("""{"role": "user", "parts": [{"text": ${input.toJsonString()}}]}""")
        }

        val contentsJson = contents.joinToString(",\n")

        val body = buildString {
            appendLine("{")
            if (prefs.globalMemory.isNotBlank()) {
                appendLine("  \"system_instruction\": {\"parts\": [{\"text\": ${prefs.globalMemory.toJsonString()}}]},")
            }
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

    private fun buildAnthropicRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("x-api-key: ${prefs.apiKey}\n")
            append("anthropic-version: 2023-06-01")
        }

        val messages = mutableListOf<String>()

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
            messages.add("""{"role": "user", "content": ${input.toJsonString()}}""")
        }

        val messagesJson = messages.joinToString(",\n")

        val body = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${prefs.model}\",")
            appendLine("  \"max_tokens\": ${prefs.maxTokens},")
            if (prefs.globalMemory.isNotBlank()) {
                appendLine("  \"system\": ${prefs.globalMemory.toJsonString()},")
            }
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
        val prefs = uiState.value.prefs
        val extractedText = extractResponseText(prefs.apiProvider, result.responseBody)
        responseBody.value = extractedText
        chatRepository.addMessage(sessionId, "response", extractedText)
        val currentSession = chatRepository.getSessionOnce(sessionId)
        val shouldAutoRename = currentSession?.title?.startsWith("Chat") != false || currentSession?.title?.startsWith("Sesi") != false
        if (shouldAutoRename) {
            val newTitle = inputForRename.ifBlank { "Chat ${sessionId.take(4)}" }
            chatRepository.renameSession(sessionId, newTitle)
        }
    }

    private suspend fun handleError(sessionId: String, renderedBody: String, throwable: Throwable) {
        val message = throwable.message ?: throwable::class.java.simpleName
        errorMessage.value = message
        responseBody.value = renderedBody
        chatRepository.addMessage(sessionId, "error", message)
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

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(
                        settingsStore = container.settingsStore,
                        chatRepository = container.chatRepository,
                        apiClient = container.apiClient,

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
