package com.example.aiclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.material.icons.filled.AttachFile
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiclient.data.AppPrefs
import com.example.aiclient.ConnectionStatus
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.data.SessionEntity
import com.example.aiclient.data.getAllProviderNames
import com.example.aiclient.data.getModelsForProvider
import com.example.aiclient.data.getDefaultBaseUrl
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
                    onSend = { text, img -> vm.sendRequest(text, img) },
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
                    onAddCustomModel = vm::addCustomModel,
                    onRemoveCustomModel = vm::removeCustomModel,
                    connectionStatus = uiState.connectionStatus,
                    connectionError = uiState.connectionError,
                    errorLog = uiState.errorLog,
                    onClearErrorLog = vm::clearErrorLog,
                    streamingText = uiState.streamingText,
                )
            }
        }
    }
}

val modelsByProvider: Map<String, List<String>> by lazy {
    getAllProviderNames().associateWith { name ->
        if (name == "Custom") emptyList()
        else getModelsForProvider(name)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    uiState: UiState,
    onCreateSession: () -> Unit,
    onSelectSession: (String) -> Unit,
    onDeleteSession: (String) -> Unit,
    onSend: (String, String) -> Unit,
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
    onAddCustomModel: (String) -> Unit = {},
    onRemoveCustomModel: (String) -> Unit = {},
    connectionStatus: ConnectionStatus,
    connectionError: String,
    errorLog: String,
    onClearErrorLog: () -> Unit,
    streamingText: String = "",
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val showSettings = rememberSaveable { mutableStateOf(false) }
    val chatListState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    // Auto-scroll when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            delay(80)
            chatListState.animateScrollToItem(uiState.messages.lastIndex, scrollOffset = -20)
        }
    }
    // Auto-hide keyboard when AI starts responding or when response arrives
    LaunchedEffect(uiState.isLoading, uiState.messages.size) {
        if (uiState.isLoading || uiState.messages.isNotEmpty()) {
            keyboardController?.hide()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = Color(0xFF0D120D),
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

                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color(0xFFCCEECC))
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateSession) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = Color(0xFFCCEECC))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0D120D),
                        titleContentColor = Color(0xFFCCEECC),
                    ),
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0D0A))
                    .padding(padding),
            ) {
                ChatArea(
                    messages = uiState.messages,
                    isLoading = uiState.isLoading,
                    listState = chatListState,
                    streamingText = uiState.streamingText,
                    modifier = Modifier.weight(1f),
                    onEditMessage = onEditMessage,
                )

                ComposerBar(
                    onSend = { text, img ->
                        onSend(text, img)
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
            onAddCustomModel = onAddCustomModel,
            onRemoveCustomModel = onRemoveCustomModel,
            connectionStatus = connectionStatus,
            connectionError = connectionError,
            errorLog = errorLog,
            onClearErrorLog = onClearErrorLog,
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
            .background(Color(0xFF0D120D)),
    ) {
        // New Chat button
        Button(
            onClick = onCreateSession,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00FF88),
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
            placeholder = { Text("Cari chat...", color = Color(0xFF448844), fontSize = 14.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF448844), modifier = Modifier.size(18.dp))
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchChange("") }, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Hapus", tint = Color(0xFF448844), modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(color = Color(0xFFCCEECC)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00FF88),
                unfocusedBorderColor = Color(0xFF1A331A),
                cursorColor = Color(0xFF00FF88),
                focusedContainerColor = Color(0xFF141E14),
                unfocusedContainerColor = Color(0xFF141E14),
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
                        color = Color(0xFF66AA66),
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
                            color = Color(0xFF448844),
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
                .background(Color(0xFF141E14), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Pengaturan",
                    tint = Color(0xFF66AA66),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Pengaturan",
                    color = Color(0xFFCCEECC),
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
            containerColor = if (isActive) Color(0xFF1A2A1A) else Color(0xFF0D120D),
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
                    color = if (isActive) Color.White else Color(0xFFBBEebb),
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
                        color = Color(0xFF559955),
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
                        color = Color(0xFF337733),
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
                        tint = Color(0xFF337733),
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
    streamingText: String = "",
    modifier: Modifier = Modifier,
    onEditMessage: ((String, String) -> Unit)? = null,
) {
    if (messages.isEmpty() && !isLoading) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0D0A)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "👋", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Mulai chat baru",
                color = Color(0xFF66AA66),
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Atur API Key di pengaturan lalu kirim pesan",
                color = Color(0xFF448844),
                fontSize = 13.sp,
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0D0A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { message ->
                ChatBubble(message = message, onEdit = onEditMessage)
            }
            if (isLoading) {
                item {
                    TypingIndicator()
                }
            }
            // Streaming text bubble
            if (streamingText.isNotBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = "Asisten",
                            color = Color(0xFF66AA66),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        Card(
                            modifier = Modifier.widthIn(max = 320.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141E14)),
                            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                MarkdownText(
                                    text = streamingText,
                                    color = Color(0xFFCCEECC),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Transmisi...",
                                    color = Color(0xFF00FF88),
                                    fontSize = 10.sp,
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
private fun MarkdownText(text: String, color: Color = Color(0xFFCCEECC), fontSize: androidx.compose.ui.unit.TextUnit = 15.sp) {
    val annotatedText = remember(text) {
        buildAnnotatedString {
            val lines = text.split("\n")
            var i = 0
            while (i < lines.size) {
                val line = lines[i]
                if (i > 0 && !line.startsWith("```")) append("\n")
                
                // Code block
                if (line.startsWith("```")) {
                    val lang = line.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    val codeText = codeLines.joinToString("\n")
                    if (codeText.isNotBlank()) {
                        withStyle(SpanStyle(
                            color = Color(0xFF00FF88),
                            background = Color(0xFF0A0D0A),
                            fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        )) {
                            append(codeText)
                        }
                    }
                    i++ // skip closing ```
                    continue
                }
                
                // Horizontal rule
                if (line.trim().matches(Regex("^(-{3,}|[*]{3,}|_{3,})$"))) {
                    append("─".repeat(20))
                    i++
                    continue
                }
                
                // Blockquote
                if (line.startsWith("> ")) {
                    withStyle(SpanStyle(color = Color(0xFF66AA66), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append("│ ")
                        processInline(line.substring(2))
                    }
                    i++
                    continue
                }
                
                // Bullet list
                if (line.startsWith("- ") || line.startsWith("* ")) {
                    append("  • ")
                    processInline(line.substring(2))
                    i++
                    continue
                }
                
                // Numbered list
                val numMatch = Regex("^(\\d+)\\. ").find(line)
                if (numMatch != null) {
                    append("  ${numMatch.groupValues[1]}. ")
                    processInline(line.substring(numMatch.value.length))
                    i++
                    continue
                }
                
                // Heading ##
                val headingMatch = Regex("^(#{1,3})\\s+(.*)").find(line)
                if (headingMatch != null) {
                    val level = headingMatch.groupValues[1].length
                    val headingText = headingMatch.groupValues[2]
                    withStyle(SpanStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = when(level) { 1 -> 20.sp; 2 -> 18.sp; else -> 16.sp }
                    )) {
                        processInline(headingText)
                    }
                    i++
                    continue
                }
                
                // Normal text
                processInline(line)
                i++
            }
        }
    }
    
    Text(
        text = annotatedText,
        color = color,
        fontSize = fontSize,
        lineHeight = 22.sp,
    )
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.processInline(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Image ![alt](url)
            text.startsWith("![", i) -> {
                val closeBracket = text.indexOf("](", i)
                val closeParen = if (closeBracket > 0) text.indexOf(")", closeBracket + 2) else -1
                if (closeBracket > 0 && closeParen > closeBracket) {
                    val alt = text.substring(i + 2, closeBracket)
                    append("[Gambar: $alt]")
                    i = closeParen + 1
                } else { append(text[i]); i++ }
            }
            // Link [text](url)
            text.startsWith("[", i) -> {
                val closeBracket = text.indexOf("](", i)
                val closeParen = if (closeBracket > 0) text.indexOf(")", closeBracket + 2) else -1
                if (closeBracket > 0 && closeParen > closeBracket) {
                    val linkText = text.substring(i + 1, closeBracket)
                    withStyle(SpanStyle(
                        color = Color(0xFF00CCCC),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    )) {
                        append(linkText)
                    }
                    i = closeParen + 1
                } else { append(text[i]); i++ }
            }
            // Bold **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                        processInline(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else { append(text[i]); i++ }
            }
            // Inline code `text`
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end > i) {
                    withStyle(SpanStyle(
                        color = Color(0xFF00FF88),
                        background = Color(0xFF0A0D0A),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 13.sp,
                    )) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            // Italic *text*
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else { append(text[i]); i++ }
            }
            else -> { append(text[i]); i++ }
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
        isError -> Color(0xFF2A0D0D)
        isUser -> Color(0xFF0D2A1A)
        else -> Color(0xFF141E14)
    }
    val textColor = when {
        isError -> Color(0xFFFF6688)
        else -> Color(0xFFCCEECC)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        // Role label
        Text(
            text = if (isUser) "Kamu" else if (isError) "Error" else "Asisten",
            color = if (isUser) Color(0xFF00FF88) else if (isError) Color(0xFFFF6688) else Color(0xFF66AA66),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { if (isUser) it else -it },
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300)),
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 320.dp),
                colors = CardDefaults.cardColors(containerColor = backgroundColor),
                shape = RoundedCornerShape(
                    topStart = if (isUser) 16.dp else 8.dp,
                    topEnd = if (isUser) 8.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp,
                ),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.imageBase64.isNotBlank()) {
                        val decoded = remember(message.imageBase64) {
                            try {
                                val bytes = android.util.Base64.decode(message.imageBase64, android.util.Base64.NO_WRAP)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) bitmap.asImageBitmap() else null
                            } catch (_: Exception) { null }
                        }
                        if (decoded != null) {
                            Image(
                                bitmap = decoded,
                                contentDescription = "Gambar",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    if (editing && isUser) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF88),
                                unfocusedBorderColor = Color(0xFF1A331A),
                                cursorColor = Color(0xFF00FF88),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0A0D0A),
                                unfocusedContainerColor = Color(0xFF0A0D0A),
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)),
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
                        if (isUser || isError) {
                            Text(
                                text = message.content,
                                color = textColor,
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            )
                        } else {
                            MarkdownText(
                                text = message.content,
                                color = textColor,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = formatMessageTime(message.timestamp),
                            color = Color(0xFF448844),
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
                                        tint = if (showCopied.value) Color(0xFF00FF88) else Color(0xFF448844),
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
                                        tint = Color(0xFF448844),
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
    onSend: (String, String) -> Unit,
    isLoading: Boolean,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by rememberSaveable { mutableStateOf("") }
    var imageBase64 by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
                imageBase64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            } catch (_: Exception) { }
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111811)),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Ketik pesan...",
                        color = Color(0xFF448844),
                    )
                },
                singleLine = false,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = Color(0xFFCCEECC),
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00FF88),
                    unfocusedBorderColor = Color(0xFF1A331A),
                    cursorColor = Color(0xFF00FF88),
                    focusedContainerColor = Color(0xFF0A0D0A),
                    unfocusedContainerColor = Color(0xFF0A0D0A),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (!isLoading && (inputText.isNotBlank() || imageBase64.isNotBlank())) {
                        keyboardController?.hide()
                        val text = inputText
                        val img = imageBase64
                        inputText = ""
                        imageBase64 = ""
                        onSend(text, img)
                    }
                }),
                shape = RoundedCornerShape(12.dp),
            )
            if (imageBase64.isNotBlank()) {
                IconButton(
                    onClick = {
                        imageBase64 = ""
                    },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Hapus gambar",
                        tint = Color(0xFF00FF88),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            IconButton(
                onClick = {
                    imagePickerLauncher.launch("image/*")
                },
                modifier = Modifier.size(24.dp),
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Lampirkan gambar",
                    tint = if (imageBase64.isNotBlank()) Color(0xFF00FF88) else Color(0xFF448844),
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    keyboardController?.hide()
                    val text = inputText
                    val img = imageBase64
                    inputText = ""
                    imageBase64 = ""
                    onSend(text, img)
                },
                enabled = !isLoading && (inputText.isNotBlank() || imageBase64.isNotBlank()),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00FF88),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF1A331A),
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
    onAddCustomModel: (String) -> Unit = {},
    onRemoveCustomModel: (String) -> Unit = {},
    connectionStatus: ConnectionStatus,
    connectionError: String,
    errorLog: String = "",
    onClearErrorLog: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val providers = getAllProviderNames()
    val modelsByProvider: Map<String, List<String>> = 
        providers.associateWith { name ->
            if (name == "Custom") emptyList()
            else getModelsForProvider(name)
        }
    val baseUrls = providers.associateWith { name ->
        if (name == "Custom") prefs.baseUrl
        else getDefaultBaseUrl(name)
    }

    val selectedProvider = remember(prefs.apiProvider) { mutableStateOf(prefs.apiProvider) }
    val selectedModel = remember(prefs.apiProvider, prefs.model) { mutableStateOf(prefs.model) }
    val apiKey = remember(prefs.apiProvider, prefs.apiKey) { mutableStateOf(prefs.apiKey) }
    val baseUrl = remember(prefs.apiProvider, prefs.baseUrl) { mutableStateOf(prefs.baseUrl) }
    val temperature = remember(prefs.apiProvider) { mutableFloatStateOf(prefs.temperature) }
    val maxTokens = remember(prefs.apiProvider) { mutableStateOf(prefs.maxTokens.toString()) }
    val systemPrompt = remember { mutableStateOf(prefs.globalMemory) }
    val showApiKey = remember { mutableStateOf(false) }
    val availableModels = modelsByProvider[selectedProvider.value] ?: emptyList()
    val customModels = remember(selectedProvider.value) { mutableStateOf(com.example.aiclient.data.getCustomModels(prefs, selectedProvider.value)) }
    val newCustomModel = remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF141E14),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCCEECC),
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
                Text("Provider", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                                contentColor = if (isSelected) Color(0xFF00FF88) else Color(0xFF66AA66),
                            ),
                        ) {
                            Text(provider, fontSize = 12.sp)
                        }
                    }
                }

                // API Key
                Text("API Key", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = apiKey.value,
                    onValueChange = {
                        apiKey.value = it
                        onUpdateApiKey(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("sk-...", color = Color(0xFF337733)) },
                    visualTransformation = if (showApiKey.value) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showApiKey.value = !showApiKey.value }) {
                            Text(
                                if (showApiKey.value) "Sembunyi" else "Lihat",
                                color = Color(0xFF00FF88),
                                fontSize = 11.sp,
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF88),
                        unfocusedBorderColor = Color(0xFF1A331A),
                        cursorColor = Color(0xFF00FF88),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0A0D0A),
                        unfocusedContainerColor = Color(0xFF0A0D0A),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Model selection
                Text("Model", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Default models
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
                                    contentColor = if (isSelected) Color(0xFF00FF88) else Color(0xFF66AA66),
                                ),
                            ) {
                                Text(model, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    // Custom models for this provider
                    if (customModels.value.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        ) {
                            customModels.value.forEach { model ->
                                val isSelected = selectedModel.value == model
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.background(
                                        if (isSelected) Color(0xFF00FF88).copy(alpha=0.15f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    ),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedModel.value = model
                                            onUpdateModel(model)
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = if (isSelected) Color(0xFF00FF88) else Color(0xFF66AA66),
                                        ),
                                        modifier = Modifier.padding(end = 0.dp),
                                    ) {
                                        Text(model, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = {
                                            onRemoveCustomModel(model)
                                            customModels.value = customModels.value - model
                                        },
                                        modifier = Modifier.size(20.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Hapus",
                                            tint = Color(0xFF448844),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Add custom model input
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = newCustomModel.value,
                            onValueChange = { newCustomModel.value = it },
                            placeholder = { Text("Nama model kustom...", color = Color(0xFF337733)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00FF88),
                                unfocusedBorderColor = Color(0xFF1A331A),
                                cursorColor = Color(0xFF00FF88),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF0A0D0A),
                                unfocusedContainerColor = Color(0xFF0A0D0A),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        Button(
                            onClick = {
                                val m = newCustomModel.value.trim()
                                if (m.isNotBlank()) {
                                    onAddCustomModel(m)
                                    customModels.value = customModels.value + m
                                    newCustomModel.value = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Tambah", fontSize = 11.sp, color = Color.White)
                        }
                    }
                }

                // Base URL
                Text("Base URL", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                OutlinedTextField(
                    value = baseUrl.value,
                    onValueChange = {
                        baseUrl.value = it
                        onUpdateBaseUrl(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF88),
                        unfocusedBorderColor = Color(0xFF1A331A),
                        cursorColor = Color(0xFF00FF88),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0A0D0A),
                        unfocusedContainerColor = Color(0xFF0A0D0A),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Temperature
                Text(
                    "Temperature: ${"%.1f".format(temperature.floatValue)}",
                    color = Color(0xFF66AA66),
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
                        activeTrackColor = Color(0xFF00FF88),
                        inactiveTrackColor = Color(0xFF1A331A),
                        thumbColor = Color(0xFF00FF88),
                    ),
                )

                // Max Tokens
                Text("Max Tokens", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        focusedBorderColor = Color(0xFF00FF88),
                        unfocusedBorderColor = Color(0xFF1A331A),
                        cursorColor = Color(0xFF00FF88),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0A0D0A),
                        unfocusedContainerColor = Color(0xFF0A0D0A),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )


                // Test Connection
                Text("Test Koneksi", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF111811)),
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
                                    tint = Color(0xFF448844),
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Belum dicek",
                                    color = Color(0xFF448844),
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            ConnectionStatus.TESTING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = Color(0xFF00FF88),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "Mengetes...",
                                    color = Color(0xFF66AA66),
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
                                        color = Color(0xFF00FF88),
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
                                        color = Color(0xFFFF6688),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (connectionError.isNotBlank()) {
                                        Text(
                                            connectionError,
                                            color = Color(0xFF66AA66),
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
                                contentColor = Color(0xFF00FF88),
                            ),
                        ) {
                            Text("Test", fontSize = 12.sp)
                        }
                    }
                }
                // System Prompt
                Text("System Prompt", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                        focusedBorderColor = Color(0xFF00FF88),
                        unfocusedBorderColor = Color(0xFF1A331A),
                        cursorColor = Color(0xFF00FF88),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color(0xFF0A0D0A),
                        unfocusedContainerColor = Color(0xFF0A0D0A),
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Error Log section
                Text("Error Log", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                if (errorLog.isNotBlank()) {
                    val clipboard = LocalClipboardManager.current
                    OutlinedTextField(
                        value = errorLog,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        readOnly = true,
                        minLines = 3,
                        maxLines = 8,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFFFF6688),
                            fontSize = 11.sp,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF337733),
                            unfocusedBorderColor = Color(0xFF1A331A),
                            focusedContainerColor = Color(0xFF0A0D0A),
                            unfocusedContainerColor = Color(0xFF0A0D0A),
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(errorLog))
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88)),
                        ) {
                            Text("Salin Log", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onClearErrorLog,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4466)),
                        ) {
                            Text("Bersihkan", fontSize = 12.sp)
                        }
                    }
                } else {
                    Text(
                        "Belum ada error",
                        color = Color(0xFF337733),
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Backup / Restore buttons
                Text("Data", color = Color(0xFF66AA66), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onBackup,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88)),
                    ) {
                        Text(" Backup", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = onRestore,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4466)),
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
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88)),
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

@Composable
private fun TypingIndicator() {
    // ChatGPT-style smooth typing animation
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (i in 0 until 3) {
            val dotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$i"
            )
            val dotScale by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dotScale$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .graphicsLayer {
                        alpha = dotAlpha
                        scaleX = dotScale
                        scaleY = dotScale
                    }
                    .background(Color(0xFF00FF88), CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = Color(0xFF00FF88),
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            "AI mentransmisi...",
            color = Color(0xFF66AA66),
            fontSize = 13.sp,
        )
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
