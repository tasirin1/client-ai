package com.example.aiclient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.data.ChatRepository
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.data.SettingsStore
import com.example.aiclient.network.ApiResult
import com.example.aiclient.network.GenericApiClient
import com.example.aiclient.network.toJsonString
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
    val currentInput: String = "",
    val sessionSearchQuery: String = "",
    val connectionStatus: ConnectionStatus = ConnectionStatus.IDLE,
    val connectionError: String = "",
)

private data class CoreUiState(
    val prefs: AppPrefs,
    val sessions: List<SessionPreview>,
    val messages: List<MessageEntity>,
    val currentSessionId: String,
    val currentInput: String,
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
    private val currentInput = MutableStateFlow("")
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
        prefsFlow,
        filteredSessionsFlow,
        messagesFlow,
        currentSessionIdFlow,
        currentInput,
        connectionStatus,
        connectionError,
    ) { prefs, sessions, messages, currentSessionId, input, connStatus, connError ->
        CoreUiState(
            prefs = prefs,
            sessions = sessions,
            messages = messages,
            currentSessionId = currentSessionId,
            currentInput = input,
            connectionStatus = connStatus,
            connectionError = connError,
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
            currentInput = core.currentInput,
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

    // --- AI Chat settings ---
    fun updateApiKey(value: String) = persistPrefs { it.copy(apiKey = value) }
    fun updateProvider(value: String) = persistPrefs { it.copy(apiProvider = value) }
    fun updateModel(value: String) = persistPrefs { it.copy(model = value) }
    fun updateBaseUrl(value: String) = persistPrefs { it.copy(baseUrl = value) }
    fun updateTemperature(value: Float) = persistPrefs { it.copy(temperature = value) }
    fun updateMaxTokens(value: Int) = persistPrefs { it.copy(maxTokens = value) }
    fun updateGlobalMemory(value: String) = persistPrefs { it.copy(globalMemory = value) }

    // --- Input (local state, no DataStore write) ---
    fun updateQuickInput(value: String) { currentInput.value = value }

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

            val headers = buildString {
                append("Content-Type: application/json\n")
                append("Authorization: Bearer ${prefs.apiKey}")
            }

            val body = buildString {
                appendLine("{")
                appendLine("  \"model\": \"${prefs.model}\",")
                appendLine("  \"messages\": [")
                appendLine("    {\"role\": \"user\", \"content\": \"Hello\"}")
                appendLine("  ],")
                appendLine("  \"max_tokens\": 5")
                append("}")
            }

            runCatching {
                apiClient.execute(
                    url = prefs.baseUrl,
                    method = "POST",
                    headersText = headers,
                    body = body,
                )
            }.onSuccess { result ->
                if (result.statusCode in 200..299) {
                    connectionStatus.value = ConnectionStatus.CONNECTED
                } else {
                    connectionStatus.value = ConnectionStatus.FAILED
                    connectionError.value = "HTTP ${result.statusCode}: ${result.statusMessage}"
                }
            }.onFailure { throwable ->
                connectionStatus.value = ConnectionStatus.FAILED
                connectionError.value = throwable.message ?: throwable::class.java.simpleName
            }
        }
    }

    // --- Session management ---
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
    fun sendRequest() {
        viewModelScope.launch {
            loading.value = true
            try {
                errorMessage.value = ""
                responseCode.value = null
                responseMessage.value = ""
                responseBody.value = ""

                val input = currentInput.value
                val prefs = uiState.value.prefs
                val session = ensureCurrentSession(prefs)
                val history = chatRepository.getMessagesOnce(session.id)

                val inputForRename = input.take(28).trim()
                val (headers, body) = buildRequest(prefs, history, input)

                chatRepository.addMessage(session.id, "request", body)

                runCatching {
                    apiClient.execute(
                        url = prefs.baseUrl.ifBlank { prefs.endpointUrl },
                        method = "POST",
                        headersText = headers,
                        body = body,
                    )
                }.onSuccess { result ->
                    handleSuccess(session.id, result, inputForRename)
                }.onFailure { throwable ->
                    handleError(session.id, body, throwable)
                }
            } finally {
                currentInput.value = ""
                loading.value = false
            }
        }
    }

    private fun buildRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Pair<String, String> {
        if (prefs.apiKey.isNotBlank()) {
            return buildAiRequest(prefs, history, input)
        }
        return buildCustomRequest(prefs, history, input)
    }

    private fun buildAiRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Pair<String, String> {
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

        return Pair(headers, body)
    }

    private fun buildCustomRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Pair<String, String> {
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
        return Pair(prefs.defaultHeaders, renderedBody)
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
        responseBody.value = result.responseBody
        chatRepository.addMessage(sessionId, "response", result.responseBody)
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
