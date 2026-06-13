package com.example.aiprediksi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiprediksi.data.AssetDatabase
import com.example.aiprediksi.data.AssetInfo
import com.example.aiprediksi.data.AssetType
import com.example.aiprediksi.data.ChartInterval
import com.example.aiprediksi.data.ProviderConfig
import com.example.aiprediksi.data.getAllProviderNames
import com.example.aiprediksi.data.getDefaultBaseUrl
import com.example.aiprediksi.data.getDefaultModel
import com.example.aiprediksi.data.getModelsForProvider
import com.example.aiprediksi.ui.PrediksiAITheme
import com.example.aiprediksi.ui.components.CandlestickChart
import com.example.aiprediksi.ui.components.ChartStatusBar
import com.example.aiprediksi.ui.components.PriceInfoBar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as AiPrediksiApplication
        setContent {
            PrediksiAITheme {
                DashboardScreen(appContainer = app.container)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(appContainer: AppContainer) {
    val vm: AppViewModel = viewModel(factory = AppViewModel.factory(appContainer))
    val state by vm.uiState.collectAsState()

    LaunchedEffect(Unit) {
        vm.fetchMarketData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("PrediksiAI", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            state.selectedAsset.symbol,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.fetchMarketData() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                    IconButton(onClick = { vm.toggleSettings() }) {
                        Icon(Icons.Default.Settings, "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ===== ASSET SELECTOR =====
            AssetSelectorBar(
                selectedType = state.selectedAsset.type,
                selectedAsset = state.selectedAsset,
                onTypeSelected = { /* filter by type - simplified */ },
                onAssetSelected = { vm.selectAsset(it) },
            )

            // ===== TIMEFRAME =====
            TimeframeSelector(
                selected = state.interval,
                onSelected = { vm.selectInterval(it) },
            )

            // ===== CANDLESTICK CHART =====
            Box {
                if (state.isLoadingData) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (state.dataError.isNotBlank()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(340.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.dataError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { vm.fetchMarketData() }) {
                                Text("Coba lagi")
                            }
                        }
                    }
                } else {
                    CandlestickChart(
                        candles = state.candles,
                        isLive = state.isLive,
                        analysisResult = state.analysisResult,
                        onCrosshairMove = { vm.setHoveredCandle(it) },
                    )
                }
            }

            // ===== CHART STATUS BAR ====
            ChartStatusBar(
                isLive = state.isLive,
                zoomLevel = state.visibleCandleCount,
                totalCandles = state.candles.size,
                onToggleLive = { vm.toggleLive() },
            )

            // ===== PRICE INFO =====
            if (state.hoveredCandle != null) {
                PriceInfoBar(
                    ohlcv = state.hoveredCandle,
                    changePercent = state.changePercent,
                )
            } else if (state.candles.isNotEmpty()) {
                PriceInfoBar(
                    ohlcv = state.candles.last(),
                    changePercent = state.changePercent,
                )
            }

            // ===== INDICATORS =====
            if (state.candles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IndicatorChip("RSI", "%.1f".format(state.rsi), rsiColor(state.rsi))
                    IndicatorChip("24h", "${"%.2f".format(state.changePercent)}%",
                        if (state.changePercent >= 0) Color(0xFF00C853) else Color(0xFFFF1744))
                    if (state.supports.isNotEmpty()) {
                        IndicatorChip("Support", fmtPrice(state.supports.last()), Color(0xFF448AFF))
                    }
                    if (state.resistances.isNotEmpty()) {
                        IndicatorChip("Resistance", fmtPrice(state.resistances.last()), Color(0xFFFF9800))
                    }
                }
            }

            // ===== ANALYZE BUTTON =====
            Button(
                onClick = { vm.analyzeChart() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enabled = state.candles.isNotEmpty() && !state.isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFC)),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (state.isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Menganalisis...", color = Color.White)
                } else {
                    Icon(Icons.Default.TrendingUp, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("🔮 Analisis AI", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            // ===== ANALISIS TEXT =====
            if (state.streamingAnalysis.isNotBlank()) {
                AnalysisCard(
                    text = state.streamingAnalysis,
                    isLoading = state.isAnalyzing,
                )
            }

            val result = state.analysisResult
            if (result != null) {
                val dirColor = when (result.direction) {
                    com.example.aiprediksi.data.PredictionDirection.BULLISH -> Color(0xFF00C853)
                    com.example.aiprediksi.data.PredictionDirection.BEARISH -> Color(0xFFFF1744)
                    com.example.aiprediksi.data.PredictionDirection.NEUTRAL -> Color(0xFFAAAAAA)
                }
                val dirEmoji = when (result.direction) {
                    com.example.aiprediksi.data.PredictionDirection.BULLISH -> "🟢"
                    com.example.aiprediksi.data.PredictionDirection.BEARISH -> "🔴"
                    com.example.aiprediksi.data.PredictionDirection.NEUTRAL -> "⚪"
                }
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        Text("$dirEmoji ${result.direction.label}", 
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = dirColor)
                        if (result.targetPrice != null) Text("🎯 ${fmtPrice(result.targetPrice)}", fontSize = 13.sp, color = Color(0xFF00C853))
                        if (result.stopLoss != null) Text("🛑 ${fmtPrice(result.stopLoss)}", fontSize = 13.sp, color = Color(0xFFFF1744))
                        Text("${"%.0f".format(result.confidence)}%", 
                            fontWeight = FontWeight.Bold, fontSize = 14.sp, color = dirColor)
                    }
                }
            }

            if (state.errorMessage.isNotBlank()) {
                Text(
                    state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ===== ERROR LOG =====
            if (state.errorLog.isNotBlank()) {
                val showLogState = rememberSaveable { mutableStateOf(false) }
                TextButton(onClick = { showLogState.value = !showLogState.value }) {
                    Text("Error Log (${state.errorLog.lines().size})", fontSize = 11.sp)
                }
                AnimatedVisibility(visible = showLogState.value) {
                    Text(
                        state.errorLog, color = Color(0xFFFF6666), fontSize = 10.sp,
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

            Spacer(Modifier.height(60.dp))
        }
    }

    // ===== SETTINGS OVERLAY =====
    if (state.showSettings) {
        SettingsOverlay(vm, state)
    }
}

// ======================== ASSET SELECTOR ========================

@Composable
private fun AssetSelectorBar(
    selectedType: AssetType,
    selectedAsset: AssetInfo,
    onTypeSelected: (AssetType) -> Unit,
    onAssetSelected: (AssetInfo) -> Unit,
) {
    val allAssets = remember { AssetDatabase.getAllAssets() }
    val showDropdownState = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Current asset button
        Box {
            OutlinedButton(
                onClick = { showDropdownState.value = true },
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("${selectedAsset.symbol} — ${selectedAsset.name}", fontSize = 13.sp, maxLines = 1)
            }
            DropdownMenu(
                expanded = showDropdownState.value,
                onDismissRequest = { showDropdownState.value = false },
                modifier = Modifier.height(300.dp),
            ) {
                AssetType.entries.forEach { type ->
                    Text(
                        "${type.emoji} ${type.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (type == selectedType) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                    )
                    AssetDatabase.getAssetsByType(type).take(30).forEach { asset ->
                        DropdownMenuItem(
                            text = {
                                Text("${asset.symbol} — ${asset.name}", fontSize = 13.sp)
                            },
                            onClick = {
                                onAssetSelected(asset)
                                showDropdownState.value = false
                            },
                        )
                    }
                }
            }
        }
    }
}

// ======================== TIMEFRAME ========================

@Composable
private fun TimeframeSelector(
    selected: ChartInterval,
    onSelected: (ChartInterval) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChartInterval.entries.forEach { interval ->
            val isSel = interval == selected
            Box(
                modifier = Modifier
                    .clickable { onSelected(interval) }
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(6.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    interval.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ======================== INDICATORS ========================

@Composable
private fun IndicatorChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
    }
}

// ======================== ANALYSIS ========================

@Composable
private fun AnalysisCard(text: String, isLoading: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("🔮 Analisis AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                text + if (isLoading) " ▌" else "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable

// ======================== SETTINGS OVERLAY ========================

@Composable
private fun SettingsOverlay(vm: AppViewModel, state: UiState) {
    val providers = remember { getAllProviderNames() }
    val selectedProviderState = rememberSaveable { mutableStateOf(state.prefs.apiProvider) }
    val apiKeyState = rememberSaveable { mutableStateOf(state.prefs.apiKey) }
    val modelState = rememberSaveable { mutableStateOf(state.prefs.model) }
    val temperatureState = rememberSaveable { mutableStateOf(state.prefs.temperature) }
    val maxTokensState = rememberSaveable { mutableStateOf(state.prefs.maxTokens.toString()) }
    val showProviderDropdownState = rememberSaveable { mutableStateOf(false) }
    val showModelDropdownState = rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.prefs.apiProvider, state.prefs.apiKey, state.prefs.model) {
        selectedProviderState.value = state.prefs.apiProvider
        apiKeyState.value = state.prefs.apiKey
        modelState.value = state.prefs.model
        temperatureState.value = state.prefs.temperature
        maxTokensState.value = state.prefs.maxTokens.toString()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) {},
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.Center),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚙️ Pengaturan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { vm.toggleSettings() }) {
                        Icon(Icons.Default.Close, "Tutup")
                    }
                }

                Text("Provider AI", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        providers.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    selectedProviderState.value = p
                                    showProviderDropdownState.value = false
                                    vm.selectProvider(p)
                                },
                            )
                        }
                    }
                }

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

                OutlinedTextField(
                    value = apiKeyState.value,
                    onValueChange = { apiKeyState.value = it },
                    label = { Text("API Key", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                )

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
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Simpan", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ======================== HELPERS ========================

private fun rsiColor(rsi: Double): Color = when {
    rsi >= 70 -> Color(0xFFFF1744)
    rsi <= 30 -> Color(0xFF00C853)
    else -> Color(0xFFFF9800)
}

private fun fmtPrice(p: Double): String = when {
    p >= 1000 -> "%.2f".format(p)
    p >= 1 -> "%.4f".format(p)
    else -> "%.6f".format(p)
}
