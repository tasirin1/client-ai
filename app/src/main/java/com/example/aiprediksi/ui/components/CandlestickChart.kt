package com.example.aiprediksi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiprediksi.data.AnalysisResult
import com.example.aiprediksi.data.OHLCV
import com.example.aiprediksi.data.PredictionDirection
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_CANDLES = 3
private const val MAX_CANDLES = 2000
private const val DEFAULT_CANDLES = 100
private const val PAD_L = 70f
private const val PAD_R = 60f
private const val PAD_T = 35f
private const val PAD_B = 25f

@Composable
fun CandlestickChart(
    candles: List<OHLCV>,
    modifier: Modifier = Modifier,
    analysisResult: AnalysisResult? = null,
    bullishColor: Color = Color(0xFF00C853),
    bearishColor: Color = Color(0xFFFF1744),
    gridColor: Color = Color(0xFF333333),
    textColor: Color = Color(0xFFAAAAAA),
    isLive: Boolean = false,
    onCrosshairMove: (OHLCV?) -> Unit = {},
    onZoomChange: (Int) -> Unit = {},
) {
    if (candles.isEmpty()) return

    var visibleCount by remember { mutableFloatStateOf(DEFAULT_CANDLES.toFloat()) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val crosshairIndexState = remember { mutableStateOf(-1) }

    val count = candles.size
    val visibleInt = (visibleCount.roundToInt()).coerceIn(MIN_CANDLES, minOf(MAX_CANDLES, count))
    val maxScroll = (count - visibleInt).coerceAtLeast(0)
    val scrollInt = scrollOffset.roundToInt().coerceIn(0, maxScroll)
    val startIdx = maxOf(0, count - visibleInt - scrollInt)

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        if (zoom != 1f) {
                            val newCount = visibleCount / zoom
                            visibleCount = newCount.coerceIn(
                                MIN_CANDLES.toFloat(),
                                minOf(MAX_CANDLES.toFloat(), count.toFloat())
                            )
                            onZoomChange(visibleCount.roundToInt())
                        }
                        if (pan.x != 0f && zoom == 1f) {
                            val panCandles = (-pan.x / 8f).roundToInt()
                            val currScroll = scrollOffset.roundToInt()
                            scrollOffset = (currScroll + panCandles).coerceIn(0, maxScroll).toFloat()
                        }
                    }
                }
                .pointerInput(visibleInt, startIdx) {
                    detectTapGestures { offset ->
                        if (offset.x in PAD_L..(size.width - PAD_R)) {
                            val cw = (size.width - PAD_L - PAD_R) / visibleInt
                            val idx = startIdx + ((offset.x - PAD_L) / cw).toInt()
                            crosshairIndexState.value = idx.coerceIn(0, count - 1)
                            onCrosshairMove(candles.getOrNull(idx))
                        }
                    }
                }
                .pointerInput(visibleInt, startIdx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            crosshairIndexState.value = -1
                            onCrosshairMove(null)
                        },
                        onHorizontalDrag = { change, _ ->
                            if (change.position.x in PAD_L..(size.width - PAD_R)) {
                                val cw = (size.width - PAD_L - PAD_R) / visibleInt
                                val idx = startIdx + ((change.position.x - PAD_L) / cw).toInt()
                                crosshairIndexState.value = idx.coerceIn(0, count - 1)
                                onCrosshairMove(candles.getOrNull(idx))
                            }
                            change.consume()
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val chartW = w - PAD_L - PAD_R
            val chartH = h - PAD_T - PAD_B
            if (count == 0 || visibleInt == 0) return@Canvas

            val visibleCandles = candles.subList(
                startIdx, minOf(startIdx + visibleInt, count)
            )
            if (visibleCandles.isEmpty()) return@Canvas

            val high = visibleCandles.maxOf { it.high }
            val low = visibleCandles.minOf { it.low }
            val priceRange = high - low
            val extend = priceRange * 0.12f
            val adjustedHigh = high + extend
            val adjustedLow = (low - extend).coerceAtMost(low)
            val adjustedRange = adjustedHigh - adjustedLow

            fun priceToY(price: Double): Float {
                return PAD_T + ((adjustedHigh - price) / adjustedRange * chartH).toFloat()
            }

            // ===== GRID LINES =====
            val gridLines = 8
            for (i in 0..gridLines) {
                val y = PAD_T + (chartH * i / gridLines)
                drawLine(
                    gridColor.copy(alpha = 0.3f),
                    Offset(PAD_L, y), Offset(w - PAD_R, y),
                    strokeWidth = 0.5f,
                )
                val price = adjustedHigh - (adjustedRange * i / gridLines)
                this.drawContext.canvas.nativeCanvas.drawText(
                    fmtPrice(price),
                    2f, y + 4f,
                    android.graphics.Paint().apply {
                        color = textColor.hashCode()
                        textSize = 20f
                        isAntiAlias = true
                    }
                )
            }

            // ===== CANDLE WIDTH =====
            val candleW = chartW / visibleInt
            val bodyW = (candleW * 0.7f).coerceAtMost(12f).coerceAtLeast(1f)
            val halfBody = bodyW / 2f

            // ===== DRAW CANDLES =====
            for ((i, c) in visibleCandles.withIndex()) {
                val x = PAD_L + i * candleW + candleW / 2f
                val isBullish = c.close >= c.open
                val candleColor = if (isBullish) bullishColor else bearishColor

                // Wick
                val highY = priceToY(c.high)
                val lowY = priceToY(c.low)
                drawLine(
                    candleColor,
                    Offset(x, highY), Offset(x, lowY),
                    strokeWidth = 1.5f,
                )

                // Body
                val openY = priceToY(c.open)
                val closeY = priceToY(c.close)
                val bodyTop = minOf(openY, closeY)
                val bodyBottom = maxOf(openY, closeY)
                drawRect(
                    candleColor,
                    topLeft = Offset(x - halfBody, bodyTop),
                    size = Size(bodyW, (bodyBottom - bodyTop).coerceAtLeast(1f)),
                )
            }

            // ===== SMA LINES =====
            drawSMA(candles, startIdx, visibleInt, 20, Color(0xFFFFD740), priceToY, chartW)
            drawSMA(candles, startIdx, visibleInt, 50, Color(0xFF448AFF), priceToY, chartW)

            // ===== VOLUME BARS =====
            val maxVol = visibleCandles.maxOf { it.volume }
            val volScale = 0.25f // Volume bars take bottom 25% of chart
            for ((i, c) in visibleCandles.withIndex()) {
                val x = PAD_L + i * candleW + candleW / 2f
                val isBullish = c.close >= c.open
                val candleColor = if (isBullish) bullishColor else bearishColor
                val volHeight = (c.volume / maxVol * chartH * volScale).toFloat()
                drawRect(
                    candleColor.copy(alpha = 0.3f),
                    topLeft = Offset(x - halfBody, h - PAD_B - volHeight),
                    size = Size(bodyW, volHeight),
                )
            }

            // ===== PREDICTION OVERLAY =====
            drawPredictionOverlay(
                analysisResult = analysisResult,
                visibleCandles = visibleCandles,
                adjustedHigh = adjustedHigh,
                adjustedLow = adjustedLow,
                adjustedRange = adjustedRange,
                w = w, h = h,
                priceToY = priceToY,
                drawContext = this,
            )

            // ===== CROSSHAIR =====
            if (crosshairIndexState.value in startIdx until startIdx + visibleInt) {
                val idx = crosshairIndexState.value
                val candle = candles[idx]
                val relIdx = idx - startIdx
                val cx = PAD_L + relIdx * candleW + candleW / 2f
                val cy = priceToY(candle.close)

                drawLine(
                    Color(0x88FFFFFF),
                    Offset(cx, PAD_T), Offset(cx, h - PAD_B),
                    strokeWidth = 0.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
                )
                drawLine(
                    Color(0x88FFFFFF),
                    Offset(PAD_L, cy), Offset(w - PAD_R, cy),
                    strokeWidth = 0.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f),
                )

                // Price label on crosshair
                this.drawContext.canvas.nativeCanvas.drawText(
                    fmtPrice(candle.close),
                    w - PAD_R + 4f, cy + 4f,
                    android.graphics.Paint().apply {
                        color = Color(0xFFFFFFFF).hashCode()
                        textSize = 22f
                        isAntiAlias = true
                        isFakeBoldText = true
                    }
                )

                // Time label
                val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(candle.openTime))
                this.drawContext.canvas.nativeCanvas.drawText(
                    timeStr,
                    cx - 10f, h - 4f,
                    android.graphics.Paint().apply {
                        color = textColor.hashCode()
                        textSize = 18f
                        isAntiAlias = true
                    }
                )
            }
        }

        // ===== ZOOM CONTROLS =====
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = {
                    val newCount = (visibleCount / 1.5f).coerceAtLeast(MIN_CANDLES.toFloat())
                    visibleCount = newCount
                    onZoomChange(visibleCount.roundToInt())
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xAA333333),
                ),
            ) {
                Icon(Icons.Default.Add, "Zoom In", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    val newCount = (visibleCount * 1.5f).coerceAtMost(MAX_CANDLES.toFloat())
                    visibleCount = newCount
                    onZoomChange(visibleCount.roundToInt())
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xAA333333),
                ),
            ) {
                Icon(Icons.Default.Remove, "Zoom Out", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = {
                    visibleCount = DEFAULT_CANDLES.toFloat()
                    scrollOffset = 0f
                    onZoomChange(visibleCount.roundToInt())
                },
                modifier = Modifier.size(28.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color(0xAA333333),
                ),
            ) {
                Icon(Icons.Default.CenterFocusStrong, "Reset Zoom", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun DrawScope.drawSMA(
    candles: List<OHLCV>,
    startIdx: Int,
    visibleInt: Int,
    period: Int,
    color: Color,
    priceToY: (Double) -> Float,
    chartW: Float,
) {
    if (candles.size < period) return
    val smaValues = mutableListOf<Double>()
    for (i in period - 1 until candles.size) {
        val sum = candles.subList(i - period + 1, i + 1).map { it.close }.average()
        smaValues.add(sum)
    }
    if (smaValues.size < 2) return

    val offset = period - 1
    val endIdx = minOf(startIdx + visibleInt, candles.size)
    val smaStart = maxOf(startIdx, offset)
    if (smaStart >= endIdx) return

    val visibleSMA = smaValues.subList(
        smaStart - offset, endIdx - offset
    )
    if (visibleSMA.size < 2) return

    val candleW = chartW / visibleInt
    for (i in 1 until visibleSMA.size) {
        val globalI = smaStart + i
        val x1 = PAD_L + (globalI - 1 - startIdx) * candleW + candleW / 2f
        val y1 = priceToY(visibleSMA[i - 1])
        val x2 = PAD_L + (globalI - startIdx) * candleW + candleW / 2f
        val y2 = priceToY(visibleSMA[i])
        drawLine(
            color.copy(alpha = 0.7f),
            Offset(x1, y1), Offset(x2, y2),
            strokeWidth = 1.5f,
        )
    }
}

private fun DrawScope.drawPredictionOverlay(
    analysisResult: AnalysisResult?,
    visibleCandles: List<OHLCV>,
    adjustedHigh: Double,
    adjustedLow: Double,
    adjustedRange: Double,
    w: Float,
    h: Float,
    priceToY: (Double) -> Float,
) {
    if (analysisResult == null) return

    val alpha = 0.5f
    val lastCandle = visibleCandles.lastOrNull() ?: return
    val lastPrice = lastCandle.close

    // Direction arrow zone
    val dirColor = when (analysisResult.direction) {
        PredictionDirection.BULLISH -> Color(0xFF00C853)
        PredictionDirection.BEARISH -> Color(0xFFFF1744)
        PredictionDirection.NEUTRAL -> Color(0xFFFF9800)
    }
    val entryPrice = analysisResult.entryPrice ?: lastPrice
    val entryY = priceToY(entryPrice)

    // Entry line
    drawLine(
        dirColor.copy(alpha = alpha),
        Offset(PAD_L, entryY), Offset(w - PAD_R, entryY),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
    )
    this.drawContext.canvas.nativeCanvas.drawText(
        "📌 Entry ${fmtPrice(entryPrice)}",
        PAD_L + 4f, entryY - 4f,
        android.graphics.Paint().apply {
            color = dirColor.copy(alpha = alpha).hashCode()
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }
    )

    // Target Price (TP)
    if (analysisResult.targetPrice != null) {
        val y = priceToY(analysisResult.targetPrice)
        drawLine(
            Color(0xFF00C853).copy(alpha = alpha),
            Offset(PAD_L, y), Offset(w - PAD_R, y),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
        )
        this.drawContext.canvas.nativeCanvas.drawText(
            "🎯 TP ${fmtPrice(analysisResult.targetPrice)}",
            PAD_L + 4f, y - 4f,
            android.graphics.Paint().apply {
                color = Color(0xFF00C853).hashCode()
                textSize = 22f
                isAntiAlias = true
                isFakeBoldText = true
            }
        )
    }

    // Stop Loss (SL)
    if (analysisResult.stopLoss != null) {
        val y = priceToY(analysisResult.stopLoss)
        drawLine(
            Color(0xFFFF1744).copy(alpha = alpha),
            Offset(PAD_L, y), Offset(w - PAD_R, y),
            strokeWidth = 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
        )
        this.drawContext.canvas.nativeCanvas.drawText(
            "🛑 SL ${fmtPrice(analysisResult.stopLoss)}",
            PAD_L + 4f, y - 4f,
            android.graphics.Paint().apply {
                color = Color(0xFFFF1744).hashCode()
                textSize = 22f
                isAntiAlias = true
                isFakeBoldText = true
            }
        )
    }

    // Support levels
    for ((i, level) in analysisResult.supportLevels.withIndex()) {
        val y = priceToY(level)
        drawLine(
            Color(0xFF448AFF).copy(alpha = 0.4f),
            Offset(PAD_L, y), Offset(w - PAD_R, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f),
        )
        this.drawContext.canvas.nativeCanvas.drawText(
            "S${i + 1} ${fmtPrice(level)}",
            PAD_L + 4f, y - 4f,
            android.graphics.Paint().apply {
                color = Color(0xFF448AFF).copy(alpha = 0.7f).hashCode()
                textSize = 18f
                isAntiAlias = true
            }
        )
    }

    // Resistance levels
    for ((i, level) in analysisResult.resistanceLevels.withIndex()) {
        val y = priceToY(level)
        drawLine(
            Color(0xFFFF9800).copy(alpha = 0.4f),
            Offset(PAD_L, y), Offset(w - PAD_R, y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f),
        )
        this.drawContext.canvas.nativeCanvas.drawText(
            "R${i + 1} ${fmtPrice(level)}",
            PAD_L + 4f, y - 4f,
            android.graphics.Paint().apply {
                color = Color(0xFFFF9800).copy(alpha = 0.7f).hashCode()
                textSize = 18f
                isAntiAlias = true
            }
        )
    }

    // Confidence badge on top-right
    val confText = "Keyakinan: ${analysisResult.confidence.toInt()}% | ${analysisResult.riskLevel.label}"
    this.drawContext.canvas.nativeCanvas.drawText(
        confText,
        w - PAD_R - 10f, PAD_T - 8f,
        android.graphics.Paint().apply {
            color = dirColor.hashCode()
            textSize = 22f
            isAntiAlias = true
            isFakeBoldText = true
        }
    )
}

@Composable
fun ChartStatusBar(
    isLive: Boolean,
    zoomLevel: Int,
    totalCandles: Int,
    connectionStatus: String = "",
    onToggleLive: () -> Unit,
    onReconnect: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (isLive) Color(0xFF00C853) else Color(0xFF888888),
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (isLive) "LIVE" else "PAUSED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isLive) Color(0xFF00C853) else Color(0xFF888888),
            )
            if (connectionStatus.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    connectionStatus,
                    fontSize = 10.sp,
                    color = Color(0xFF888888),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🖐 ${zoomLevel}", fontSize = 11.sp, color = Color(0xFF888888))
            Spacer(Modifier.width(8.dp))
            Text("${totalCandles} candles", fontSize = 11.sp, color = Color(0xFF888888))
        }
    }
}

@Composable
fun PriceInfoBar(
    ohlcv: OHLCV?,
    changePercent: Double,
    modifier: Modifier = Modifier,
) {
    if (ohlcv == null) return
    val color = if (changePercent >= 0) Color(0xFF00C853) else Color(0xFFFF1744)
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            "O: ${fmt(ohlcv.open)}  H: ${fmt(ohlcv.high)}  L: ${fmt(ohlcv.low)}  C: ${fmt(ohlcv.close)}",
            color = Color(0xFFAAAAAA), fontSize = 12.sp,
        )
        Text(
            "Vol: ${fmtVol(ohlcv.volume)}  ${"%.2f".format(changePercent)}%",
            color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

fun fmt(p: Double): String = when {
    p >= 1000 -> "%.2f".format(p)
    p >= 1 -> "%.4f".format(p)
    else -> "%.6f".format(p)
}
fun fmtVol(v: Double): String = when {
    v >= 1_000_000 -> "%.2fM".format(v / 1_000_000)
    v >= 1_000 -> "%.2fK".format(v / 1_000)
    else -> "%.2f".format(v)
}
fun fmtPrice(p: Double): String = fmt(p)
