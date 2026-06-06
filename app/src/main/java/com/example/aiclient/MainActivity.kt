package com.example.aiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.ui.AIClientTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AiClientApplication).container
        setContent {
            AIClientTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                val uiState by vm.uiState.collectAsState()
                AppScreen(
                    uiState = uiState,
                    onCreateSession = vm::createSession,
                    onSelectSession = vm::updateActiveSession,
                    onEndpointChange = vm::updateEndpointUrl,
                    onMethodChange = vm::updateMethod,
                    onHeadersChange = vm::updateHeaders,
                    onBodyTemplateChange = vm::updateBodyTemplate,
                    onQuickInputChange = vm::updateQuickInput,
                    onMemoryChange = vm::updateGlobalMemory,
                    onSend = vm::sendRequest,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(
    uiState: UiState,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onEndpointChange: (String) -> Unit,
    onMethodChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onBodyTemplateChange: (String) -> Unit,
    onQuickInputChange: (String) -> Unit,
    onMemoryChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    val showAdvanced = rememberSaveable { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(80)
            chatListState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF08121F),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0F2238),
                            Color(0xFF08121F),
                        ),
                    ),
                ),
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "AI Client",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "Chat UI untuk API apa saja",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF9FB4C7),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { showAdvanced.value = !showAdvanced.value }) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF7EE0D1))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showAdvanced.value) "Sembunyikan" else "Advanced",
                                    color = Color(0xFF7EE0D1),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    ComposerBar(
                        quickInput = uiState.prefs.quickInput,
                        onQuickInputChange = onQuickInputChange,
                        onSend = onSend,
                        isLoading = uiState.isLoading,
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SessionStrip(
                        sessions = uiState.sessions,
                        currentSessionId = uiState.currentSessionId,
                        onCreateSession = onCreateSession,
                        onSelectSession = onSelectSession,
                    )

                    MessageTranscript(
                        messages = uiState.messages,
                        isLoading = uiState.isLoading,
                        modifier = Modifier.weight(1f),
                        listState = chatListState,
                    )

                    AnimatedVisibility(visible = showAdvanced.value) {
                        AdvancedSettingsCard(
                            endpointUrl = uiState.prefs.endpointUrl,
                            method = uiState.prefs.method,
                            headers = uiState.prefs.defaultHeaders,
                            bodyTemplate = uiState.prefs.bodyTemplate,
                            globalMemory = uiState.prefs.globalMemory,
                            onEndpointChange = onEndpointChange,
                            onMethodChange = onMethodChange,
                            onHeadersChange = onHeadersChange,
                            onBodyTemplateChange = onBodyTemplateChange,
                            onMemoryChange = onMemoryChange,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionStrip(
    sessions: List<SessionEntity>,
    currentSessionId: String,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E1A2B)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sesi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onCreateSession) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF7EE0D1))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Baru", color = Color(0xFF7EE0D1))
                }
            }

            if (sessions.isEmpty()) {
                Text(text = "Belum ada sesi tersimpan.", color = Color(0xFF9FB4C7))
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    sessions.forEach { session ->
                        val selected = session.id == currentSessionId
                        AssistChip(
                            onClick = { onSelectSession(session.id) },
                            label = {
                                Text(
                                    text = session.title,
                                    color = if (selected) Color(0xFF08121F) else Color.White,
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) Color(0xFF7EE0D1) else Color(0xFF18324F),
                                labelColor = if (selected) Color(0xFF08121F) else Color.White,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageTranscript(
    messages: List<MessageEntity>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    listState: LazyListState,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1624)),
        shape = RoundedCornerShape(28.dp),
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mulai percakapan",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tulis pesan di bawah untuk mengirim ke API.",
                        color = Color(0xFF9FB4C7),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatBubble(message = message)
                }
                if (isLoading) {
                    item {
                        TypingIndicatorBubble()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: MessageEntity,
) {
    val isUser = message.role == "request"
    val isError = message.role == "error"
    val containerColor = when {
        isError -> Color(0xFF4B2430)
        isUser -> Color(0xFF16304D)
        else -> Color(0xFF122437)
    }
    val textColor = if (isUser) Color.White else Color(0xFFEAF3FF)
    val senderLabel = when (message.role) {
        "request" -> "You"
        "response" -> "Assistant"
        else -> "System"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(180)) +
                androidx.compose.animation.slideInVertically(
                    animationSpec = tween(180),
                    initialOffsetY = { it / 3 },
                ),
        ) {
            Card(
                modifier = Modifier.align(if (isUser) Alignment.End else Alignment.Start),
                colors = CardDefaults.cardColors(containerColor = containerColor),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = senderLabel,
                        color = when {
                            isError -> Color(0xFFFFA59A)
                            isUser -> Color(0xFF7EE0D1)
                            else -> Color(0xFFF4B860)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = message.content,
                        color = textColor,
                    )
                    Text(
                        text = formatTimestamp(message.timestamp),
                        color = Color(0xFF9FB4C7),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypingIndicatorBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF122437)),
            shape = RoundedCornerShape(22.dp),
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Assistant",
                    color = Color(0xFFF4B860),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Mengetik...",
                    color = Color(0xFF9FB4C7),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ComposerBar(
    quickInput: String,
    onQuickInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF09121D)),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = quickInput,
                onValueChange = onQuickInputChange,
                label = { Text("Ketik pesan") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
            )
            Button(
                onClick = onSend,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Mengirim..." else "Kirim")
            }
        }
    }
}

@Composable
private fun AdvancedSettingsCard(
    endpointUrl: String,
    method: String,
    headers: String,
    bodyTemplate: String,
    globalMemory: String,
    onEndpointChange: (String) -> Unit,
    onMethodChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onBodyTemplateChange: (String) -> Unit,
    onMemoryChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1F33)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Pengaturan API",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = endpointUrl,
                onValueChange = onEndpointChange,
                label = { Text("Endpoint URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            MethodSelector(
                method = method,
                onMethodChange = onMethodChange,
            )
            OutlinedTextField(
                value = headers,
                onValueChange = onHeadersChange,
                label = { Text("Headers") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = bodyTemplate,
                onValueChange = onBodyTemplateChange,
                label = { Text("Body Template") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 6,
            )
            OutlinedTextField(
                value = globalMemory,
                onValueChange = onMemoryChange,
                label = { Text("Memory Global") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Text(
                text = "Placeholder: {{input_json}}, {{memory_json}}, {{history_json}}",
                color = Color(0xFF9FB4C7),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MethodSelector(
    method: String,
    onMethodChange: (String) -> Unit,
) {
    val methods = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        methods.forEach { item ->
            val selected = item.equals(method, ignoreCase = true)
            AssistChip(
                onClick = { onMethodChange(item) },
                label = {
                    Text(
                        text = item,
                        color = if (selected) Color(0xFF08121F) else Color.White,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (selected) Color(0xFF7EE0D1) else Color(0xFF18324F),
                    labelColor = if (selected) Color(0xFF08121F) else Color.White,
                ),
            )
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) "HH:mm" else "d MMM HH:mm"
    return SimpleDateFormat(format, Locale.getDefault()).format(timestamp)
}
