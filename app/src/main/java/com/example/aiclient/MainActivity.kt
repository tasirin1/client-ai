package com.example.aiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.ConnectionStatus
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.ui.AIClientTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
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
                val backupScope = rememberCoroutineScope()
                val backupCtx = androidx.compose.ui.platform.LocalContext.current
                val backupLauncher = rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    if (uri != null) doBackupWithUri(vm, uri, backupCtx, backupScope)
                }
                val restoreLauncher = rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) doRestoreFromUri(vm, uri, backupCtx, backupScope)
                }
                MainScreen(
                    uiState = uiState,
                    onCreateSession = vm::createSession,
                    onSelectSession = vm::updateActiveSession,
                    onDeleteSession = vm::deleteSession,
                    onSend = { text -> vm.sendRequest(text) },
                    onEditMessage = { msgId, text -> vm.editMessageAndRegenerate(msgId, text) },
                    onSessionSearch = vm::updateSessionSearch,
                    onUpdateApiKey = vm::updateApiKey,
                    onUpdateProvider = vm::updateProvider,
                    onUpdateModel = vm::updateModel,
                    onUpdateBaseUrl = vm::updateBaseUrl,
                    onUpdateTemperature = vm::updateTemperature,
                    onUpdateMaxTokens = vm::updateMaxTokens,
                    onUpdateGlobalMemory = vm::updateGlobalMemory,
                    onTestConnection = vm::testConnection,
                    onBackup = {
                        val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
                        backupLauncher.launch("ai-client-backup-$dateStr.json")
                    },

                    onRestore = { restoreLauncher.launch(arrayOf("application/json")) },
                    connectionStatus = uiState.connectionStatus,
                    connectionError = uiState.connectionError,

                )
            }
        }
    }
}

val modelsByProvider = mapOf(
    "OpenAI" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1", "o1-mini", "o3-mini"),
    "Anthropic" to listOf("claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307"),
    "Google" to listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.0-pro"),
    "Deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
    "Groq" to listOf("gemma2-9b-it", "mixtral-8x7b-32768", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama-guard-3-8b", "llama3-70b-8192", "llama3-8b-8192", "deepseek-r1-distill-llama-70b"),
    "OpenRouter" to listOf("openai/gpt-4o", "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-r1", "mistralai/mistral-small-24b-instruct"),
    "Custom" to emptyList(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    uiState: UiState,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSend: (String) -> Unit,
    onEditMessage: ((String, String) -> Unit) = { _, _ -> },
    onSessionSearch: (String) -> Unit,
    onUpdateApiKey: (String) -> Unit,
    onUpdateProvider: (String) -> Unit,
    onUpdateModel: (String) -> Unit,
    onUpdateBaseUrl: (String) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onUpdateMaxTokens: (Int) -> Unit,
    onUpdateGlobalMemory: (String) -> Unit,
    onTestConnection: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    connectionStatus: ConnectionStatus,
    connectionError: String,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showSettings = rememberSaveable { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Auto-scroll when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(80)
            chatListState.animateScrollToItem(uiState.messages.lastIndex)
        }
    }
    // Auto-hide keyboard when AI starts responding
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            keyboardController?.hide()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = Color(0xFF161616),
            ) {
                SessionSidebar(
                    groupedSessions = uiState.groupedSessions,
                    activeSessionId = uiState.currentSessionId,
                    searchQuery = uiState.sessionSearchQuery,
                    onSearchChange = onSessionSearch,
                    onCreateSession = {
                        onCreateSession()
                        scope.launch { drawerState.close() }
                    },
                    onSelectSession = { id ->
                        onSelectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = onDeleteSession,
                    onOpenSettings = { showSettings.value = true },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            val currentSession = uiState.sessions.find { it.session.id == uiState.currentSessionId }
                            Text(
                                text = currentSession?.session?.title ?: "AI Client",
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = uiState.prefs.model,
                                color = Color(0xFF10A37F),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFFE0E0E0))
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateSession) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color(0xFFE0E0E0))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF161616),
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
                ChatArea(
                    messages = uiState.messages,
                    isLoading = uiState.isLoading,
                    listState = chatListState,
                    modifier = Modifier.weight(1f),
                    onEditMessage = onEditMessage,
                )

                ComposerBar(
                    quickInput = inputText,
                    onQuickInputChange = { inputText = it },
                    onSend = { text ->
                        onSend(text)
                        inputText = ""
                    },
                    isLoading = uiState.isLoading,
                )
            }
        }
    }

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
            onTestConnection = onTestConnection,
            onBackup = onBackup,
            onRestore = onRestore,
            connectionStatus = connectionStatus,
            connectionError = connectionError,
            onDismiss = { showSettings.value = false },
        )
    }
}

@Composable
private fun SessionSidebar(
    groupedSessions: Map<String, List<SessionPreview>>,
    activeSessionId: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .background(Color(0xFF161616)),
    ) {
        // New Chat button
        Button(
            onClick = onCreateSession,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF10A37F),
                contentColor = Color.White,
            ),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Chat Baru", fontWeight = FontWeight.SemiBold)
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            placeholder = { Text("Cari chat...", color = Color(0xFF666666), fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFE0E0E0)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF10A37F),
                unfocusedBorderColor = Color(0xFF333333),
                cursorColor = Color(0xFF10A37F),
                focusedContainerColor = Color(0xFF1E1E1E),
                unfocusedContainerColor = Color(0xFF1E1E1E),
            ),
            shape = RoundedCornerShape(10.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            groupedSessions.forEach { (groupName, sessions) ->
                item {
                    Text(
                        text = groupName,
                        color = Color(0xFF888888),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                items(sessions) { preview ->
                    val isActive = preview.session.id == activeSessionId
                    SessionCard(
                        preview = preview,
                        isActive = isActive,
                        onSelect = { onSelectSession(preview.session.id) },
                        onDelete = { onDeleteSession(preview.session.id) },
                        showDelete = groupedSessions.values.flatten().size > 1,
                    )
                }
            }

            // Empty state
            if (groupedSessions.values.all { it.isEmpty() }) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Tidak ada chat",
                            color = Color(0xFF666666),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // Settings button at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clickable { onOpenSettings() }
                .background(Color(0xFF1E1E1E), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Pengaturan",
                    tint = Color(0xFF888888),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Pengaturan",
                    color = Color(0xFFE0E0E0),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SessionCard(
    preview: SessionPreview,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable { onSelect },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF2A2A2A) else Color(0xFF161616),
        ),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = "💬",
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preview.session.title,
                    color = if (isActive) Color.White else Color(0xFFCCCCCC),
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                                            softWrap = true,
                )
                if (preview.lastMessage != null) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = preview.lastMessage,
                        color = Color(0xFF777777),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                                            softWrap = true,
                        lineHeight = 15.sp,
                    )
                }
                if (preview.lastMessageTime != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDateLabel(preview.lastMessageTime),
                        color = Color(0xFF555555),
                        fontSize = 10.sp,
                    )
                }
            }
            if (showDelete) {
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Hapus",
                        tint = Color(0xFF555555),
                        modifier = Modifier.size(14.dp),
                    )
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
    onEditMessage: ((String, String) -> Unit)? = null,
) {
    if (messages.isEmpty() && !isLoading) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF121212)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "👋", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mulai chat baru",
                color = Color(0xFF888888),
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atur API Key di pengaturan lalu kirim pesan",
                color = Color(0xFF666666),
                fontSize = 13.sp,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF121212))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                ChatBubble(message = message, onEdit = onEditMessage)
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
}

@Composable
private fun ChatBubble(message: MessageEntity, onEdit: ((String, String) -> Unit)? = null) {
    val isUser = message.role == "request"
    val isError = message.role == "error"
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val showCopied = remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf(message.content) }

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
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Role label
        Text(
            text = if (isUser) "Kamu" else if (isError) "Error" else "Asisten",
            color = if (isUser) Color(0xFF10A37F) else if (isError) Color(0xFFEF9A9A) else Color(0xFF888888),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(300)),
        ) {
            Card(
                modifier = Modifier.widthIn(max = 320.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 8.dp,
                    topEnd = if (isUser) 8.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (editing && isUser) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
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
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    editing = false
                                    onEdit?.invoke(message.id, editText)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Simpan", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { editing = false; editText = message.content },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Batal", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text(
                            text = message.content,
                            color = textColor,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(message.timestamp),
                            color = Color(0xFF666666),
                            fontSize = 11.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!isUser && !isError) {
                                // Copy button for AI responses
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                        showCopied.value = true
                                        scope.launch {
                                            delay(1500)
                                            showCopied.value = false
                                        }
                                    },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "Salin",
                                        tint = if (showCopied.value) Color(0xFF10A37F) else Color(0xFF666666),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            if (isUser && !isError && onEdit != null) {
                                // Edit button for user messages
                                IconButton(
                                    onClick = { editing = true },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
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
}

@Composable
private fun ComposerBar(
    quickInput: String,
    onQuickInputChange: (String) -> Unit,
    onSend: (String) -> Unit,
    isLoading: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {


            OutlinedTextField(
                value = quickInput,
                onValueChange = onQuickInputChange,
                placeholder = {
                    Text(
"Ketik pesan...",
                        color = Color(0xFF666666),
                    )
                },
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFE0E0E0),
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF10A37F),
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = Color(0xFF10A37F),
                    focusedContainerColor = Color(0xFF121212),
                    unfocusedContainerColor = Color(0xFF121212),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isLoading && quickInput.isNotBlank()) {
                        keyboardController?.hide()
                        onSend(quickInput)
                    }
                }),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    onSend(quickInput)
                },
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
    onTestConnection: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    connectionStatus: ConnectionStatus,
    connectionError: String,
    onDismiss: () -> Unit,
) {
    val providers = listOf("OpenAI", "Anthropic", "Google", "Deepseek", "Groq", "OpenRouter", "Custom")
    val modelsByProvider = mapOf(
        "OpenAI" to listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1", "o1-mini", "o3-mini"),
        "Anthropic" to listOf("claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307"),
        "Google" to listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.0-pro"),
        "Deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
        "Groq" to listOf("gemma2-9b-it", "mixtral-8x7b-32768", "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama-guard-3-8b", "llama3-70b-8192", "llama3-8b-8192", "deepseek-r1-distill-llama-70b"),
        "OpenRouter" to listOf("openai/gpt-4o", "openai/gpt-4o-mini", "anthropic/claude-3.5-sonnet", "google/gemini-2.0-flash", "meta-llama/llama-3.3-70b-instruct", "deepseek/deepseek-r1", "mistralai/mistral-small-24b-instruct"),
        "Custom" to emptyList(),
    )
    val baseUrls = mapOf(
        "OpenAI" to "https://api.openai.com/v1/chat/completions",
        "Anthropic" to "https://api.anthropic.com/v1/messages",
        "Google" to "https://generativelanguage.googleapis.com/v1beta/models",
        "Deepseek" to "https://api.deepseek.com/v1/chat/completions",
        "Groq" to "https://api.groq.com/openai/v1/chat/completions",
        "OpenRouter" to "https://openrouter.ai/api/v1/chat/completions",
        "Custom" to prefs.baseUrl,
    )

    val selectedProvider = remember(prefs.apiProvider) { mutableStateOf(prefs.apiProvider) }
    val selectedModel = remember(prefs.apiProvider, prefs.model) { mutableStateOf(prefs.model) }
    val customModel = remember { mutableStateOf("") }
    val apiKey = remember(prefs.apiProvider, prefs.apiKey) { mutableStateOf(prefs.apiKey) }
    val baseUrl = remember(prefs.apiProvider, prefs.baseUrl) { mutableStateOf(prefs.baseUrl) }
    val temperature = remember(prefs.apiProvider) { mutableFloatStateOf(prefs.temperature) }
    val maxTokens = remember(prefs.apiProvider) { mutableStateOf(prefs.maxTokens.toString()) }
    val systemPrompt = remember { mutableStateOf(prefs.globalMemory) }
    val showApiKey = remember { mutableStateOf(false) }
    val showCustomModelInput = remember(selectedProvider.value, selectedModel.value) { mutableStateOf(selectedProvider.value == "Custom" || !modelsByProvider[selectedProvider.value]?.contains(selectedModel.value)!!) }
    val availableModels = modelsByProvider[selectedProvider.value] ?: emptyList()

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E1E1E),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE0E0E0),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = "Pengaturan",
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Provider buttons
                Text("Provider", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    providers.forEach { provider ->
                        val isSelected = selectedProvider.value == provider
                        OutlinedButton(
                            onClick = {
                                selectedProvider.value = provider
                                onUpdateProvider(provider)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isSelected) Color(0xFF10A37F) else Color(0xFF888888),
                            ),
                        ) {
                            Text(provider, fontSize = 12.sp)
                        }
                    }
                }

                // API Key
                Text("API Key", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                fontSize = 11.sp,
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

                // Model selection
                Text("Model", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (showCustomModelInput.value) {
                    OutlinedTextField(
                        value = if (selectedProvider.value == "Custom") customModel.value else selectedModel.value,
                        onValueChange = {
                            if (selectedProvider.value == "Custom") {
                                customModel.value = it
                            }
                            selectedModel.value = it
                            onUpdateModel(it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Nama model...", color = Color(0xFF555555)) },
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
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
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
                                    Text(model, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        TextButton(onClick = { showCustomModelInput.value = true }) {
                            Text("+ Model kustom", color = Color(0xFF10A37F), fontSize = 12.sp)
                        }
                    }
                }

                // Base URL
                Text("Base URL", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
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
                Text("Max Tokens", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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


                // Test Connection
                Text("Test Koneksi", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when (connectionStatus) {
                            ConnectionStatus.IDLE -> {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF666666),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Belum dicek",
                                    color = Color(0xFF666666),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ConnectionStatus.TESTING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF10A37F),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Mengetes...",
                                    color = Color(0xFF888888),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ConnectionStatus.CONNECTED -> {
                                Text("\u2705", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Terhubung",
                                        color = Color(0xFF10A37F),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                            ConnectionStatus.FAILED -> {
                                Text("\u274C", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Gagal",
                                        color = Color(0xFFEF9A9A),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (connectionError.isNotBlank()) {
                                        Text(
                                            connectionError,
                                            color = Color(0xFF888888),
                                            fontSize = 11.sp,
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                            softWrap = true,
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = onTestConnection,
                            enabled = connectionStatus != ConnectionStatus.TESTING,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFF10A37F),
                            ),
                        ) {
                            Text("Test", fontSize = 12.sp)
                        }
                    }
                }
                // System Prompt
                Text("System Prompt", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = systemPrompt.value,
                    onValueChange = {
                        systemPrompt.value = it
                        onUpdateGlobalMemory(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
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

                Spacer(modifier = Modifier.height(4.dp))

                // Backup / Restore buttons
                Text("Data", color = Color(0xFF888888), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBackup,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF10A37F)),
                    ) {
                        Text(" Backup", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF6B6B)),
                    ) {
                        Text("Restore", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10A37F)),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Simpan & Tutup", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}

// --- Backup / Restore helpers ---
private fun doBackupWithUri(vm: AppViewModel, uri: android.net.Uri, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
            val json = vm.createBackupJson()
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            android.widget.Toast.makeText(ctx, "Backup berhasil disimpan", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Backup gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

private fun doRestoreFromUri(vm: AppViewModel, uri: android.net.Uri, ctx: android.content.Context, scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw Exception("Cannot read file")
            val success = vm.restoreFromJson(json)
            if (success) {
                android.widget.Toast.makeText(ctx, "Restore berhasil! Session akan dimuat ulang", android.widget.Toast.LENGTH_LONG).show()
            } else {
                android.widget.Toast.makeText(ctx, "Restore gagal: format file tidak valid", android.widget.Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "Restore gagal: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) "HH:mm" else "d MMM HH:mm"
    return SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))
}

private fun formatDateLabel(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)

    return if (sameDay) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
    } else {
        SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
