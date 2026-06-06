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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val prefs: AppPrefs = AppPrefs(),
    val sessions: List<SessionEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
    val currentSessionId: String = "",
    val isLoading: Boolean = false,
    val responseCode: Int? = null,
    val responseMessage: String = "",
    val responseBody: String = "",
    val errorMessage: String = "",
)

private data class CoreUiState(
    val prefs: AppPrefs,
    val sessions: List<SessionEntity>,
    val messages: List<MessageEntity>,
    val currentSessionId: String,
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

    private val prefsFlow = settingsStore.prefsFlow
    private val sessionsFlow = chatRepository.observeSessions()

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
        sessionsFlow,
        messagesFlow,
        currentSessionIdFlow,
    ) { prefs, sessions, messages, currentSessionId ->
        CoreUiState(
            prefs = prefs,
            sessions = sessions,
            messages = messages,
            currentSessionId = currentSessionId,
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
            messages = core.messages,
            currentSessionId = core.currentSessionId,
            isLoading = network.isLoading,
            responseCode = network.responseCode,
            responseMessage = network.responseMessage,
            responseBody = network.responseBody,
            errorMessage = network.errorMessage,
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
            existing ?: chatRepository.createSession("Sesi 1")
        }
        settingsStore.update { it.copy(activeSessionId = session.id) }
    }

    fun updateEndpointUrl(value: String) = persistPrefs { it.copy(endpointUrl = value) }
    fun updateMethod(value: String) = persistPrefs { it.copy(method = value) }
    fun updateHeaders(value: String) = persistPrefs { it.copy(defaultHeaders = value) }
    fun updateBodyTemplate(value: String) = persistPrefs { it.copy(bodyTemplate = value) }
    fun updateQuickInput(value: String) = persistPrefs { it.copy(quickInput = value) }
    fun updateGlobalMemory(value: String) = persistPrefs { it.copy(globalMemory = value) }
    fun updateActiveSession(sessionId: String) = persistPrefs { it.copy(activeSessionId = sessionId) }

    fun createSession() {
        viewModelScope.launch {
            val session = chatRepository.createSession("Sesi ${uiState.value.sessions.size + 1}")
            updateActiveSession(session.id)
        }
    }

    fun sendRequest() {
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
                    .joinToString("\n\n") { "${it.role}: ${it.content}" }
                val renderedBody = apiClient.renderTemplate(
                    template = prefs.bodyTemplate,
                    input = prefs.quickInput,
                    memory = prefs.globalMemory,
                    history = history,
                )

                chatRepository.addMessage(session.id, "request", renderedBody)

                runCatching {
                    apiClient.execute(
                        url = prefs.endpointUrl,
                        method = prefs.method,
                        headersText = prefs.defaultHeaders,
                        body = renderedBody,
                    )
                }.onSuccess { result ->
                    handleSuccess(session.id, result)
                }.onFailure { throwable ->
                    handleError(session.id, renderedBody, throwable)
                }
            } finally {
                loading.value = false
            }
        }
    }

    private suspend fun ensureCurrentSession(prefs: AppPrefs): SessionEntity {
        val activeId = prefs.activeSessionId
        val existing = activeId.takeIf { it.isNotBlank() }?.let { chatRepository.getSessionOnce(it) }
        return existing ?: run {
            val session = chatRepository.createSession("Sesi ${uiState.value.sessions.size + 1}")
            settingsStore.update { it.copy(activeSessionId = session.id) }
            session
        }
    }

    private suspend fun handleSuccess(sessionId: String, result: ApiResult) {
        responseCode.value = result.statusCode
        responseMessage.value = result.statusMessage
        responseBody.value = result.responseBody
        chatRepository.addMessage(sessionId, "response", result.responseBody)
        val currentSession = chatRepository.getSessionOnce(sessionId)
        val shouldAutoRename = currentSession?.title?.startsWith("Sesi ") != false
        if (shouldAutoRename) {
            val newTitle = uiState.value.prefs.quickInput
                .take(24)
                .trim()
                .ifBlank { "Sesi ${sessionId.take(4)}" }
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
