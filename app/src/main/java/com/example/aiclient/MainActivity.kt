package com.example.aiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.ui.AIClientTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AiClientApplication).container
        setContent {
            AIClientTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                val uiState by vm.uiState.collectAsState()
                MainScreen(
                    uiState = uiState,
                    onCreateSession = vm::createSession,
                    onSelectSession = vm::updateActiveSession,
                    onDeleteSession = vm::deleteSession,
                    onQuickInputChange = vm::updateQuickInput,
                    onSend = vm::sendRequest,
                    onUpdateApiKey = vm::updateApiKey,
                    onUpdateProvider = vm::updateProvider,
                    onUpdateModel = vm::updateModel,
                    onUpdateBaseUrl = vm::updateBaseUrl,
                    onUpdateTemperature = vm::updateTemperature,
                    onUpdateMaxTokens = vm::updateMaxTokens,
                    onUpdateGlobalMemory = vm::updateGlobalMemory,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    uiState: UiState,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onQuickInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateProvider: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateMaxTokens: (Int) -> Unit,
    onUpdateGlobalMemory: (String) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showSettings = rememberSaveable { mutableStateOf(false) }
    val chatListState = rememberLazyListState()

    // Auto-scroll when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(80)
            chatListState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = Color(0xFF1A1A1A),
            ) {
                SessionSidebar(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.currentSessionId,
                    onCreateSession = {
                        onCreateSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { id ->
                        onSelectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = onDeleteSession,
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val currentSession = uiState.sessions.find { it.id == uiState.currentSessionId }
                        Text(
                            text = currentSession?.title ?: "AI Client",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFFE0E0E0))
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings.value = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFFE0E0E0))
                        }
                        IconButton(onClick = onCreateSession) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color(0xFFE0E0E0))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1A1A1A),
                        titleContentColor = Color(0xFFE0E0E0),
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212))
                    .padding(padding),
            ) {
                // Chat messages area
                ChatArea(
                    messages = uiState.messages,
                    isLoading = uiState.isLoading,
                    listState = chatListState,
                    modifier = Modifier.weight(1f),
                )

                // Composer bar
                ComposerBar(
                    quickInput = uiState.prefs.quickInput,
                    onQuickInputChange = onQuickInputChange,
                    onSend = onSend,
                    isLoading = uiState.isLoading,
                )
            }
        }
    }

    // Settings dialog
    if (showSettings.value) {
        SettingsDialog(
            prefs = uiState.prefs,
            onUpdateApiKey = onUpdateApiKey,
            onUpdateProvider = onUpdateProvider,
            onUpdateModel = onUpdateModel,
            onUpdateBaseUrl = onUpdateBaseUrl,
            onUpdateTemperature = onUpdateTemperature,
            onUpdateMaxTokens = onUpdateMaxTokens,
            onUpdateGlobalMemory = onUpdateGlobalMemory,
            onDismiss = { showSettings.value = false },
        )
    }
}

@Composable
private fun SessionSidebar(
    sessions: List<SessionEntity>,
    activeSessionId: String,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF1A1A1A))
            .padding(12.dp),
    ) {
        Button(
            onClick = onCreateSession,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10A37F),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(8.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat Baru", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Riwayat Chat",
            color = Color(0xFF888888),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(sessions) { session ->
                val isActive = session.id == activeSessionId
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSession(session.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) Color(0xFF2C2C2C) else Color(0xFF1A1A1A),
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "💬", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = session.title,
                            color = if (isActive) Color.White else Color(0xFFCCCCCC),
                            fontSize = 14.sp,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (sessions.size > 1) {
                            IconButton(
                                onClick = { onDeleteSession(session.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Hapus",
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatArea(
    messages: List<MessageEntity>,
    isLoading: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages) { message ->
            ChatBubble(message = message)
        }
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF10A37F),
                            strokeWidth = 2.dp,
                        )
                        Text("Memproses...", color = Color(0xFF888888), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: MessageEntity) {
    val isUser = message.role == "request"
    val isError = message.role == "error"

    val backgroundColor = when {
        isError -> Color(0xFF3D1A1A)
        isUser -> Color(0xFF1E3A3A)
        else -> Color(0xFF1E1E1E)
    }
    val textColor = when {
        isError -> Color(0xFFEF9A9A)
        else -> Color(0xFFE0E0E0)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)),
        ) {
            Card(
                modifier = Modifier.widthIn(max = 320.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 4.dp,
                    topEnd = if (isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.content,
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatMessageTime(message.timestamp),
                        color = Color(0xFF666666),
                        fontSize = 11.sp,
                    )
                }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = quickInput,
                onValueChange = onQuickInputChange,
                placeholder = { Text("Ketik pesan...", color = Color(0xFF666666)) },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFFE0E0E0)),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10A37F),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFF10A37F),
                    focusedContainerColor = Color(0xFF121212),
                    unfocusedContainerColor = Color(0xFF121212),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (!isLoading && quickInput.isNotBlank()) onSend() }),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSend,
                enabled = !isLoading && quickInput.isNotBlank(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10A37F),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF333333),
                ),
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Kirim",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    prefs: AppPrefs,
    onUpdateApiKey: (String) -> Unit,
    onUpdateProvider: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateMaxTokens: (Int) -> Unit,
    onUpdateGlobalMemory: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val providers = listOf("OpenAI", "Anthropic", "Google", "Custom")
    val modelsByProvider = mapOf(
        "OpenAI" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"),
        "Anthropic" to listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307"),
        "Google" to listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-pro"),
        "Custom" to listOf("custom"),
    )
    val baseUrls = mapOf(
        "OpenAI" to "https://api.openai.com/v1/chat/completions",
        "Anthropic" to "https://api.anthropic.com/v1/messages",
        "Google" to "https://generativelanguage.googleapis.com/v1beta/models",
        "Custom" to prefs.baseUrl,
    )

    val selectedProvider = remember { mutableStateOf(prefs.apiProvider) }
    val selectedModel = remember { mutableStateOf(prefs.model) }
    val apiKey = remember { mutableStateOf(prefs.apiKey) }
    val baseUrl = remember { mutableStateOf(prefs.baseUrl) }
    val temperature = remember { mutableFloatStateOf(prefs.temperature) }
    val maxTokens = remember { mutableStateOf(prefs.maxTokens.toString()) }
    val systemPrompt = remember { mutableStateOf(prefs.globalMemory) }
    val showApiKey = remember { mutableStateOf(false) }
    val availableModels = modelsByProvider[selectedProvider.value] ?: modelsByProvider["OpenAI"]!!

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE0E0E0),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "Pengaturan API",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Provider
                Text("Provider", color = Color(0xFF888888), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider.value == provider
                        OutlinedButton(
                            onClick = {
                                selectedProvider.value = provider
                                onUpdateProvider(provider)
                                baseUrl.value = baseUrls[provider] ?: ""
                                onUpdateBaseUrl(baseUrl.value)
                                val models = modelsByProvider[provider] ?: modelsByProvider["OpenAI"]!!
                                selectedModel.value = models.first()
                                onUpdateModel(models.first())
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isSelected) Color(0xFF10A37F) else Color(0xFF888888),
                            ),
                        ) {
                            Text(provider, fontSize = 13.sp)
                        }
                    }
                }

                // API Key
                Text("API Key", color = Color(0xFF888888), fontSize = 13.sp)
                OutlinedTextField(
                    value = apiKey.value,
                    onValueChange = {
                        apiKey.value = it
                        onUpdateApiKey(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = Color(0xFF555555)) },
                    visualTransformation = if (showApiKey.value) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey.value = !showApiKey.value }) {
                            Text(
                                if (showApiKey.value) "Sembunyi" else "Lihat",
                                color = Color(0xFF10A37F),
                                fontSize = 12.sp,
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10A37F),
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color(0xFF10A37F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Model
                Text("Model", color = Color(0xFF888888), fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableModels.forEach { model ->
                        val isSelected = selectedModel.value == model
                        OutlinedButton(
                            onClick = {
                                selectedModel.value = model
                                onUpdateModel(model)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isSelected) Color(0xFF10A37F) else Color(0xFF888888),
                            ),
                        ) {
                            Text(model, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                // Base URL
                Text("Base URL", color = Color(0xFF888888), fontSize = 13.sp)
                OutlinedTextField(
                    value = baseUrl.value,
                    onValueChange = {
                        baseUrl.value = it
                        onUpdateBaseUrl(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10A37F),
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color(0xFF10A37F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Temperature
                Text(
                    "Temperature: ${"%.1f".format(temperature.floatValue)}",
                    color = Color(0xFF888888),
                    fontSize = 13.sp,
                )
                Slider(
                    value = temperature.floatValue,
                    onValueChange = {
                        temperature.floatValue = it
                        onUpdateTemperature(it)
                    },
                    valueRange = 0f..2f,
                    steps = 19,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF10A37F),
                    inactiveTrackColor = Color(0xFF333333),
                    thumbColor = Color(0xFF10A37F),
                    ),
                )

                // Max Tokens
                Text("Max Tokens", color = Color(0xFF888888), fontSize = 13.sp)
                OutlinedTextField(
                    value = maxTokens.value,
                    onValueChange = {
                        maxTokens.value = it
                        it.toIntOrNull()?.let { v -> onUpdateMaxTokens(v) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10A37F),
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color(0xFF10A37F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // System Prompt
                Text("System Prompt", color = Color(0xFF888888), fontSize = 13.sp)
                OutlinedTextField(
                    value = systemPrompt.value,
                    onValueChange = {
                        systemPrompt.value = it
                        onUpdateGlobalMemory(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF10A37F),
                        unfocusedBorderColor = Color(0xFF333333),
                        cursorColor = Color(0xFF10A37F),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF121212),
                        unfocusedContainerColor = Color(0xFF121212),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Spacer so button at bottom has some room
                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("Simpan & Tutup", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

private fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) "HH:mm" else "d MMM HH:mm"
    return SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))
}
