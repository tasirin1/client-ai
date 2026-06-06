package com.example.aiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberScrollState
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
import java.util.Locale

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
                                    text = "Client API generik dengan memori lintas sesi",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color(0xFF9FB4C7),
                                )
                            }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        SessionStrip(
                            sessions = uiState.sessions,
                            currentSessionId = uiState.currentSessionId,
                            onCreateSession = onCreateSession,
                            onSelectSession = onSelectSession,
                        )
                    }
                    item {
                        RequestCard(
                            endpointUrl = uiState.prefs.endpointUrl,
                            method = uiState.prefs.method,
                            headers = uiState.prefs.defaultHeaders,
                            bodyTemplate = uiState.prefs.bodyTemplate,
                            quickInput = uiState.prefs.quickInput,
                            globalMemory = uiState.prefs.globalMemory,
                            onEndpointChange = onEndpointChange,
                            onMethodChange = onMethodChange,
                            onHeadersChange = onHeadersChange,
                            onBodyTemplateChange = onBodyTemplateChange,
                            onQuickInputChange = onQuickInputChange,
                            onMemoryChange = onMemoryChange,
                            onSend = onSend,
                            isLoading = uiState.isLoading,
                        )
                    }
                    item {
                        ResponseCard(
                            code = uiState.responseCode,
                            message = uiState.responseMessage,
                            body = uiState.responseBody,
                            error = uiState.errorMessage,
                        )
                    }
                    item {
                        HistoryCard(messages = uiState.messages)
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF10263E)),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Sesi",
                    style = MaterialTheme.typography.titleMedium,
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
                            colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
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
private fun RequestCard(
    endpointUrl: String,
    method: String,
    headers: String,
    bodyTemplate: String,
    quickInput: String,
    globalMemory: String,
    onEndpointChange: (String) -> Unit,
    onMethodChange: (String) -> Unit,
    onHeadersChange: (String) -> Unit,
    onBodyTemplateChange: (String) -> Unit,
    onQuickInputChange: (String) -> Unit,
    onMemoryChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1F33)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Request Builder", color = Color.White, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = endpointUrl,
                onValueChange = onEndpointChange,
                label = { Text("Endpoint URL (https://...)") },
                modifier = Modifier.fillMaxWidth(),
            )
            MethodSelector(method = method, onMethodChange = onMethodChange)
            Button(
                onClick = onSend,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLoading) "Mengirim..." else "Send")
            }
            OutlinedTextField(
                value = quickInput,
                onValueChange = onQuickInputChange,
                label = { Text("Input / Prompt") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            OutlinedTextField(
                value = headers,
                onValueChange = onHeadersChange,
                label = { Text("Headers") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            OutlinedTextField(
                value = bodyTemplate,
                onValueChange = onBodyTemplateChange,
                label = { Text("Body Template") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 8,
            )
            OutlinedTextField(
                value = globalMemory,
                onValueChange = onMemoryChange,
                label = { Text("Global Memory") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Text(
                text = "Placeholder: {{input}} / {{input_json}}, {{memory}} / {{memory_json}}, {{history}} / {{history_json}}",
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

@Composable
private fun ResponseCard(
    code: Int?,
    message: String,
    body: String,
    error: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1F33)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Response", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (code != null) {
                Text(
                    text = "HTTP $code ${message.trim()}",
                    color = Color(0xFF7EE0D1),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (error.isNotBlank()) {
                Text(text = error, color = Color(0xFFFFA59A))
            }
            if (body.isBlank()) {
                Text(text = "Belum ada response.", color = Color(0xFF9FB4C7))
            } else {
                Text(text = body, color = Color.White)
            }
        }
    }
}

@Composable
private fun HistoryCard(messages: List<MessageEntity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1F33)),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Riwayat Sesi", color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (messages.isEmpty()) {
                Text(text = "Belum ada pesan yang disimpan.", color = Color(0xFF9FB4C7))
            } else {
                messages.takeLast(10).forEach { msg ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = msg.role.uppercase(Locale.getDefault()),
                            color = Color(0xFF7EE0D1),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(text = msg.content, color = Color.White)
                        Divider(color = Color(0x223D5B7A))
                    }
                }
            }
        }
    }
}
