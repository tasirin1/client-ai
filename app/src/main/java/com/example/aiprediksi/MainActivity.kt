package com.example.aiprediksi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiprediksi.data.AssetDatabase
import com.example.aiprediksi.data.AssetInfo
import com.example.aiprediksi.data.AssetType
import com.example.aiprediksi.data.MessageEntity
import com.example.aiprediksi.data.ProviderConfig
import com.example.aiprediksi.data.getAllProviderNames
import com.example.aiprediksi.data.getModelsForProvider
import com.example.aiprediksi.data.getDefaultBaseUrl
import com.example.aiprediksi.data.getDefaultModel
import com.example.aiprediksi.ui.PrediksiAITheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AiPrediksiApplication
        setContent {
            PrediksiAITheme {
                MainScreen(appContainer = app.container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(appContainer: AppContainer) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(appContainer))
    val state by vm.uiState.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val inputText = rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll ke bawah saat ada pesan baru
    LaunchedEffect(state.messages.size, state.streamingText) {
        if (state.messages.isNotEmpty() || state.streamingText.isNotBlank()) {
            listState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(vm, state, onClose = { scope.launch { drawerState.close() } })
        },
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = getSessionTitle(state.messages, state.currentSessionId),
                    isDrawerOpen = drawerState.isOpen,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onNewChat = { vm.createNewSession() },
                    onSettings = { vm.toggleSettings() },
                )
            },
            bottomBar = {
                if (state.showSettings) {
                    SettingsPanel(vm, state)
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
            ) {
                // Asset type selector chips
                AssetTypeChips(
                    selectedType = state.selectedAssetType,
                    onTypeSelected = { vm.setAssetType(it) },
                )

                // Chat messages
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    items(state.messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg)
                    }

                    // Streaming indicator
                    if (state.isLoading && state.streamingText.isBlank()) {
                        item { TypingIndicator() }
                    }
                    if (state.streamingText.isNotBlank() && state.isLoading) {
                        item {
                            StreamingBubble(text = state.streamingText)
                        }
                    }

                    // Error
                    if (state.errorMessage.isNotBlank()) {
                        item {
                            Text(
                                text = state.errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                }

                // Error log (collapsible)
                if (state.errorLog.isNotBlank()) {
                    val showLog = rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { showLog.value = !showLog.value }) {
                        Text("Error Log (${state.errorLog.lines().size} line)", fontSize = 11.sp)
                    }
                    AnimatedVisibility(visible = showLog.value) {
                        Text(
                            text = state.errorLog,
                            color = Color(0xFFFF6666),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .height(100.dp)
                                .background(Color(0x33000000), RoundedCornerShape(4.dp))
                                .padding(4.dp),
                            maxLines = 10,
                        )
                    }
                }

                // Input area
                ChatInput(
                    text = inputText.value,
                    onTextChange = { inputText.value = it },
                    onSend = {
                        if (inputText.value.isNotBlank()) {
                            vm.sendPrediction(inputText.value, state.selectedAssetType)
                            inputText.value = ""
                        }
                    },
                    isLoading = state.isLoading,
                )
            }
        }
    }
}

// ======================== DRAWER ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerContent(vm: AppViewModel, state: UiState, onClose: () -> Unit) {
    val searchQuery = rememberSaveable { mutableStateOf("") }

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight(),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // Header
            TopAppBar(
                title = { Text("PrediksiAI", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, "Tutup")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            // Search
            OutlinedTextField(
                value = searchQuery.value,
                onValueChange = {
                    searchQuery.value = it
                    vm.updateSearchQuery(it)
                },
                placeholder = { Text("Cari sesi...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, "Cari", modifier = Modifier.size(18.dp)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )

            // Sessions list
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val grouped = groupSessionsByDate(state.sessions)
                grouped.forEach { (label, previews) ->
                    item {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    items(previews, key = { it.session.id }) { preview ->
                        SessionItem(
                            preview = preview,
                            isActive = preview.session.id == state.currentSessionId,
                            onClick = {
                                vm.selectSession(preview.session.id)
                                onClose()
                            },
                            onDelete = { vm.deleteSession(preview.session.id) },
                        )
                    }
                }

                if (state.sessions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "Belum ada sesi prediksi",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionItem(
    preview: SessionPreview,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
    else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(bgColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preview.session.title,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (preview.lastMessage != null) {
                Text(
                    text = preview.lastMessage,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Hapus", modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ======================== TOP BAR ========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    isDrawerOpen: Boolean,
    onMenuClick: () -> Unit,
    onNewChat: () -> Unit,
    onSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, "Menu")
            }
        },
        actions = {
            IconButton(onClick = onNewChat) {
                Icon(Icons.Default.Add, "Sesi baru")
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, "Pengaturan")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ======================== ASSET TYPE CHIPS ========================

@Composable
private fun AssetTypeChips(
    selectedType: AssetType,
    onTypeSelected: (AssetType) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssetType.entries.forEach { type ->
            val isSelected = type == selectedType
            val bgColor = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant
            val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .clickable { onTypeSelected(type) }
                    .background(bgColor, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "${type.emoji} ${type.label}",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = textColor,
                )
            }
        }
    }
}

// ======================== MESSAGE BUBBLES ========================

@Composable
private fun MessageBubble(message: MessageEntity) {
    val isUser = message.role == "user"
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val bgColor = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
    else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurface
    val cornerShape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(bgColor, cornerShape)
                .padding(12.dp),
        ) {
            // Asset type label
            if (message.assetType.isNotBlank()) {
                Text(
                    text = try {
                        val at = AssetType.valueOf(message.assetType)
                        "${at.emoji} ${at.label}"
                    } catch (_: Exception) { message.assetType },
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }

            Column {
                if (isUser) {
                    Text(
                        text = message.content,
                        color = textColor,
                        fontSize = 14.sp,
                    )
                } else {
                    // Render markdown-style text
                    val text = message.predictionData.ifBlank { message.content }
                    PredictionContent(text)
                }
            }
        }
        // Timestamp
        Text(
            text = formatMessageTime(message.createdAt),
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun PredictionContent(text: String) {
    val lines = text.lines()
    Column {
        var isBold = false
        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("📊") || trimmed.startsWith("🟢") || trimmed.startsWith("🔴") || trimmed.startsWith("⚪") -> {
                    Text(
                        text = trimmed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                trimmed.startsWith("🎯") -> {
                    Text(trimmed, fontSize = 14.sp, color = Color(0xFF00C853),
                        fontWeight = FontWeight.SemiBold)
                }
                trimmed.startsWith("🛑") -> {
                    Text(trimmed, fontSize = 14.sp, color = Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                trimmed.startsWith("📈") -> {
                    Text(trimmed, fontSize = 14.sp, color = Color(0xFF448AFF),
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                }
                trimmed.startsWith("**") && trimmed.endsWith("**") -> {
                    Text(
                        text = trimmed.removeSurrounding("**"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                trimmed.startsWith("- ") -> {
                    Text(
                        text = "•  ${trimmed.removePrefix("- ")}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 8.dp, top = 1.dp, end = 8.dp, bottom = 1.dp),
                    )
                }
                trimmed.startsWith("⚠️") -> {
                    Text(
                        text = trimmed,
                        fontSize = 11.sp,
                        color = Color(0xFFFFB74D),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                trimmed.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> {
                    Text(
                        text = trimmed,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = text + " ▌",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
        )
        Text(
            "AI menganalisis...",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

// ======================== INPUT ========================

@Composable
private fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text("Tanya prediksi...", fontSize = 14.sp) },
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!isLoading) onSend() }),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && !isLoading,
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (text.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape,
                ),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    Icons.Default.Send,
                    "Kirim",
                    tint = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ======================== SETTINGS PANEL ========================

@Composable
private fun SettingsPanel(vm: AppViewModel, state: UiState) {
    val providers = remember { getAllProviderNames() }
    val selectedProviderState = rememberSaveable { mutableStateOf(state.prefs.apiProvider) }
    val apiKeyState = rememberSaveable { mutableStateOf(state.prefs.apiKey) }
    val modelState = rememberSaveable { mutableStateOf(state.prefs.model) }
    val temperatureState = rememberSaveable { mutableStateOf(state.prefs.temperature) }
    val maxTokensState = rememberSaveable { mutableStateOf(state.prefs.maxTokens.toString()) }
    val showProviderDropdownState = rememberSaveable { mutableStateOf(false) }
    val showModelDropdownState = rememberSaveable { mutableStateOf(false) }

    // Update local state when prefs change
    LaunchedEffect(state.prefs.apiProvider, state.prefs.apiKey, state.prefs.model) {
        selectedProviderState.value = state.prefs.apiProvider
        apiKeyState.value = state.prefs.apiKey
        modelState.value = state.prefs.model
        temperatureState.value = state.prefs.temperature
        maxTokensState.value = state.prefs.maxTokens.toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Pengaturan AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                IconButton(onClick = { vm.toggleSettings() }) {
                    Icon(Icons.Default.Close, "Tutup")
                }
            }

            // Provider
            Text("Provider", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(
                    onClick = { showProviderDropdownState.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(selectedProviderState.value, maxLines = 1)
                }
                DropdownMenu(
                    expanded = showProviderDropdownState.value,
                    onDismissRequest = { showProviderDropdownState.value = false },
                ) {
                    providers.forEach { provider ->
                        DropdownMenuItem(
                            text = { Text(provider) },
                            onClick = {
                                selectedProviderState.value = provider
                                showProviderDropdownState.value = false
                                vm.selectProvider(provider)
                            },
                        )
                    }
                }
            }

            // Model
            Text("Model", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                OutlinedButton(
                    onClick = { showModelDropdownState.value = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(modelState.value.ifBlank { "Pilih model" }, maxLines = 1)
                }
                DropdownMenu(
                    expanded = showModelDropdownState.value,
                    onDismissRequest = { showModelDropdownState.value = false },
                ) {
                    getModelsForProvider(selectedProviderState.value).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m) },
                            onClick = {
                                modelState.value = m
                                showModelDropdownState.value = false
                                vm.updateSetting { it.copy(model = m) }
                            },
                        )
                    }
                }
            }

            // API Key
            OutlinedTextField(
                value = apiKeyState.value,
                onValueChange = { apiKeyState.value = it },
                label = { Text("API Key", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            // Temperature
            Text("Temperature: ${"%.1f".format(temperatureState.value)}", fontSize = 12.sp)
            Slider(
                value = temperatureState.value,
                onValueChange = { temperatureState.value = it },
                valueRange = 0f..1f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )

            // Max Tokens
            OutlinedTextField(
                value = maxTokensState.value,
                onValueChange = { maxTokensState.value = it.filter { c -> c.isDigit() } },
                label = { Text("Max Tokens", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodySmall,
            )

            // Save button
            Button(
                onClick = {
                    val config = ProviderConfig(
                        apiKey = apiKeyState.value,
                        model = modelState.value,
                        baseUrl = getDefaultBaseUrl(selectedProviderState.value),
                        temperature = temperatureState.value,
                        maxTokens = maxTokensState.value.toIntOrNull() ?: 4096,
                    )
                    vm.updateProviderConfig(selectedProviderState.value, config)
                    vm.toggleSettings()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Simpan", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ======================== HELPERS ========================

private fun getSessionTitle(messages: List<MessageEntity>, sessionId: String): String {
    if (sessionId.isBlank()) return "PrediksiAI"
    val userMsg = messages.firstOrNull { it.role == "user" }
    return userMsg?.content?.take(28)?.trim()?.ifBlank { "Prediksi" } ?: "Prediksi"
}

private fun formatMessageTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val value = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    val format = if (sameDay) "HH:mm" else "d MMM HH:mm"
    return SimpleDateFormat(format, Locale.getDefault()).format(Date(timestamp))
}
