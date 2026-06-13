package com.example.aiprediksi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiprediksi.data.OHLCV
import com.example.aiprediksi.data.AnalysisResult
import com.example.aiprediksi.data.PredictionDirection
import kotlin.math.abs
import kotlin.math.roundToInt

// Zoom levels: number of candles to display
private const val MIN_CANDLES = 10
private const val MAX_CANDLES = 200
private const val DEFAULT_CANDLES = 80

@Composable
fun CandlestickChart(
    candles: List<OHLCV>,
    modifier: Modifier = Modifier,
    bullishColor: Color = Color(0xFF00C853),
    bearishColor: Color = Color(0xFFFF1744),
    gridColor: Color = Color(0xFF333333),
    textColor: Color = Color(0xFFAAAAAA),
    isLive: Boolean = false,
    analysisResult: com.example.aiprediksi.data.AnalysisResult? = null,
    onCrosshairMove: (OHLCV?) -> Unit = {},
) {
    if (candles.isEmpty()) return

    // Zoom state: visible candle count
    var visibleCount by remember { mutableFloatStateOf(DEFAULT_CANDLES.toFloat()) }
    // Scroll offset (pan)
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val crosshairIndexState = remember { mutableStateOf(-1) }

    val count = candles.size
    val visibleInt = (visibleCount.roundToInt()).coerceIn(MIN_CANDLES, minOf(MAX_CANDLES, count))
    val startIdx = ((count - visibleInt) + scrollOffset.roundToInt()).coerceIn(0, count - visibleInt)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(360.dp)
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    // Zoom: change visible candle count
                    val newCount = visibleCount / zoom
                    visibleCount = newCount.coerceIn(MIN_CANDLES.toFloat(), minOf(MAX_CANDLES.toFloat(), count.toFloat()))
                    
                    // Pan: scroll through history
                    scrollOffset = (scrollOffset + pan.x / 20f).coerceIn(
                        -(count - visibleInt).toFloat(),
                        0f
                    )
                }
            }
            .pointerInput(candles.size) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val candleW = size.width.toFloat() / visibleInt
                        val idx = startIdx + (offset.x / candleW).toInt()
                        crosshairIndexState.value = idx.coerceIn(0, count - 1)
                        onCrosshairMove(candles.getOrNull(idx))
                    },
                    onDragEnd = {
                        crosshairIndexState.value = -1
                        onCrosshairMove(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        val candleW = size.width.toFloat() / visibleInt
                        val idx = startIdx + (change.position.x / candleW).toInt()
                        crosshairIndexState.value = idx.coerceIn(0, count - 1)
                        onCrosshairMove(candles.getOrNull(idx))
                    },
                )
            },
    ) {
        val w = size.width
        val h = size.height
        val padL = 60f
        val padR = 10f
        val padT = 30f
        val padB = 20f
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        if (count == 0 || visibleInt == 0) return@Canvas

        val visibleCandles = candles.subList(startIdx, minOf(startIdx + visibleInt, count))
        if (visibleCandles.isEmpty()) return@Canvas

        val high = visibleCandles.maxOf { it.high }
        val low = visibleCandles.minOf { it.low }
        val range = high - low
        val pRange = if (range == 0.0) 1.0 else range

        fun priceToY(p: Double): Float = (padT + ((high - p) / pRange * chartH)).toFloat()

        val candleW = chartW / visibleInt
        val bodyW = (candleW * 0.7f).coerceIn(1.5f, 12f)
        val halfBody = bodyW / 2f
        val gridN = 4

        val textPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 22f
        }
        val boldPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 22f
            isFakeBoldText = true
        }

        // Grid horizontal
        for (i in 0..gridN) {
            val y = padT + (chartH / gridN) * i
            drawLine(gridColor.copy(alpha = 0.5f), Offset(padL, y), Offset(w - padR, y), strokeWidth = 0.5f)
            val price = high - (pRange / gridN) * i
            drawContext.canvas.nativeCanvas.drawText(fmtPrice(price), 4f, y + 7f, textPaint)
        }

        // Visible range label
        val visibleLabel = "${visibleInt}/${count}"
        drawContext.canvas.nativeCanvas.drawText(
            visibleLabel, w - padR - 40f, padT - 8f, textPaint.apply { textSize = 20f })

        // Candles
        for (i in visibleCandles.indices) {
            val c = visibleCandles[i]
            val x = padL + i * candleW + candleW / 2
            val isBullish = c.close >= c.open
            val color = if (isBullish) bullishColor else bearishColor
            val oY = priceToY(c.open)
            val cY = priceToY(c.close)
            val hY = priceToY(c.high)
            val lY = priceToY(c.low)
            val top = minOf(oY, cY)
            val bot = maxOf(oY, cY)
            val bodyH = abs(cY - oY).coerceAtLeast(1f)

            // Wick
            drawLine(color, Offset(x, hY), Offset(x, lY), strokeWidth = 1.5f)
            // Body
            drawRect(color, topLeft = Offset(x - halfBody, top), size = Size(bodyW, bodyH))
        }

        // ===== PREDICTION OVERLAY =====
        if (analysisResult != null) {
            drawPredictionOverlay(
                analysisResult, priceToY, padL, padT, chartW, chartH,
                textPaint, boldPaint, bullishColor, bearishColor
            )
        }

        // Crosshair
        val chIdx = crosshairIndexState.value
        if (chIdx in startIdx until startIdx + visibleInt) {
            val visIdx = chIdx - startIdx
            val x = padL + visIdx * candleW + candleW / 2
            val c = candles[chIdx]
            val cY = priceToY(c.close)

            drawLine(Color(0x66FFFFFF), Offset(x, padT), Offset(x, padT + chartH), strokeWidth = 1f)
            drawCircle(
                if (c.close >= c.open) bullishColor else bearishColor,
                5f, Offset(x, cY)
            )
            drawContext.canvas.nativeCanvas.drawText(
                fmtPrice(c.close), x + 8f, cY - 8f, boldPaint
            )
        }
    }
}

@Composable
fun ChartStatusBar(
    isLive: Boolean,
    zoomLevel: Int,
    totalCandles: Int,
    onToggleLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Live indicator
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        }

        // Zoom indicator + pinch hint
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "🖐 ${zoomLevel}",
                fontSize = 11.sp,
                color = Color(0xFF888888),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "${totalCandles} candles",
                fontSize = 11.sp,
                color = Color(0xFF888888),
            )
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

/**
 * Draw prediction overlay on the chart: direction arrow, target/stop lines, 
 * entry price, support/resistance levels, projected path
 */
private fun DrawScope.drawPredictionOverlay(
    result: com.example.aiprediksi.data.AnalysisResult,
    priceToY: (Double) -> Float,
    padL: Float,
    padT: Float,
    chartW: Float,
    chartH: Float,
    textPaint: android.graphics.Paint,
    boldPaint: android.graphics.Paint,
    bullishColor: Color,
    bearishColor: Color,
) {
    val lastX = padL + chartW
    val midX = padL + chartW * 0.7f
    val dirColor = when (result.direction) {
        PredictionDirection.BULLISH -> bullishColor
        PredictionDirection.BEARISH -> bearishColor
        PredictionDirection.NEUTRAL -> Color(0xFFAAAAAA)
    }
    val dirEmoji = when (result.direction) {
        PredictionDirection.BULLISH -> "🟢"
        PredictionDirection.BEARISH -> "🔴"
        PredictionDirection.NEUTRAL -> "⚪"
    }
    val dashEffect = floatArrayOf(10f, 6f)

    // ---- 1. Direction Arrow at last candle ----
    val arrowY = priceToY(result.entryPrice ?: 0.0).let { if (it.isNaN()) padT + 20f else it }
    val arrowSize = 24f
    val arrowX = lastX - 30f

    // Arrow background circle
    drawCircle(dirColor.copy(alpha = 0.2f), arrowSize + 8f, Offset(arrowX, arrowY))
    drawCircle(dirColor, arrowSize + 4f, Offset(arrowX, arrowY), style = Stroke(width = 2f))

    // Arrow direction (Compose Path)
    val arrowPath = androidx.compose.ui.graphics.Path().apply {
        when (result.direction) {
            PredictionDirection.BULLISH -> {
                moveTo(arrowX, arrowY - arrowSize)
                lineTo(arrowX - arrowSize * 0.6f, arrowY + arrowSize * 0.3f)
                lineTo(arrowX + arrowSize * 0.6f, arrowY + arrowSize * 0.3f)
                close()
            }
            PredictionDirection.BEARISH -> {
                moveTo(arrowX, arrowY + arrowSize)
                lineTo(arrowX - arrowSize * 0.6f, arrowY - arrowSize * 0.3f)
                lineTo(arrowX + arrowSize * 0.6f, arrowY - arrowSize * 0.3f)
                close()
            }
            PredictionDirection.NEUTRAL -> {
                moveTo(arrowX - arrowSize * 0.6f, arrowY)
                lineTo(arrowX + arrowSize * 0.6f, arrowY)
                moveTo(arrowX, arrowY - arrowSize * 0.4f)
                lineTo(arrowX, arrowY + arrowSize * 0.4f)
            }
        }
    }
    drawPath(arrowPath, dirColor)

    // ---- 2. Target Price Line (green dashed) ----
    if (result.targetPrice != null) {
        val targetY = priceToY(result.targetPrice)
        val isAbove = result.direction == PredictionDirection.BULLISH
        // Dashed line across chart
        drawLine(
            bullishColor.copy(alpha = 0.7f),
            Offset(padL, targetY),
            Offset(lastX, targetY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(dashEffect, 0f),
        )
        // Target label
        drawContext.canvas.nativeCanvas.drawText(
            "🎯 ${fmtPrice(result.targetPrice)}",
            padL + 4f, targetY - 8f,
            boldPaint.apply { color = bullishColor.hashCode() }
        )
    }

    // ---- 3. Stop Loss Line (red dashed) ----
    if (result.stopLoss != null) {
        val slY = priceToY(result.stopLoss)
        drawLine(
            bearishColor.copy(alpha = 0.7f),
            Offset(padL, slY),
            Offset(lastX, slY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(dashEffect, 0f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            "🛑 ${fmtPrice(result.stopLoss)}",
            padL + 4f, slY - 8f,
            boldPaint.apply { color = bearishColor.hashCode() }
        )
    }

    // ---- 4. Support Levels (blue dashed) ----
    result.supportLevels.forEachIndexed { i, s ->
        val sY = priceToY(s)
        drawLine(
            Color(0xFF448AFF).copy(alpha = 0.5f),
            Offset(padL, sY),
            Offset(lastX, sY),
            strokeWidth = 1f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            "S${i + 1} ${fmtPrice(s)}",
            padL + 4f, sY - 4f,
            textPaint.apply { color = Color(0xFF448AFF).hashCode() }
        )
    }

    // ---- 5. Resistance Levels (orange dashed) ----
    result.resistanceLevels.forEachIndexed { i, r ->
        val rY = priceToY(r)
        drawLine(
            Color(0xFFFF9800).copy(alpha = 0.5f),
            Offset(padL, rY),
            Offset(lastX, rY),
            strokeWidth = 1f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f),
        )
        drawContext.canvas.nativeCanvas.drawText(
            "R${i + 1} ${fmtPrice(r)}",
            padL + 4f, rY - 4f,
            textPaint.apply { color = Color(0xFFFF9800).hashCode() }
        )
    }

    // ---- 6. Confidence Badge ----
    val confText = "${dirEmoji} ${result.direction.label} ${"%.0f".format(result.confidence)}%"
    val confColor = dirColor
    drawRoundRect(
        confColor.copy(alpha = 0.15f),
        topLeft = Offset(lastX - 160f, padT + 4f),
        size = androidx.compose.ui.geometry.Size(156f, 28f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
    )
    drawContext.canvas.nativeCanvas.drawText(
        confText,
        lastX - 150f, padT + 24f,
        boldPaint.apply { color = confColor.hashCode(); textSize = 24f }
    )

    // ---- 7. Dotted Projected Path ----
    val endY = when (result.direction) {
        PredictionDirection.BULLISH -> priceToY(result.targetPrice ?: 0.0)
        PredictionDirection.BEARISH -> priceToY(result.stopLoss ?: 0.0)
        PredictionDirection.NEUTRAL -> priceToY(0.0)
    }
    if (!endY.isNaN() && endY > 0f) {
        val startY = priceToY(result.entryPrice ?: 0.0).let { if (it.isNaN()) arrowY else it }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(lastX, startY)
            // Curve projection
            cubicTo(
                lastX + 60f, startY,
                lastX + 40f, endY,
                lastX + 80f, endY,
            )
        }
        drawPath(
            path,
            dirColor.copy(alpha = 0.6f),
            style = Stroke(
                width = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
        // Arrow at end of projection
        val projEndX = lastX + 80f
        when (result.direction) {
            PredictionDirection.BULLISH -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(projEndX, endY - 8f)
                    lineTo(projEndX - 6f, endY + 4f)
                    lineTo(projEndX + 6f, endY + 4f)
                    close()
                }, dirColor.copy(alpha = 0.8f))
            }
            PredictionDirection.BEARISH -> {
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(projEndX, endY + 8f)
                    lineTo(projEndX - 6f, endY - 4f)
                    lineTo(projEndX + 6f, endY - 4f)
                    close()
                }, dirColor.copy(alpha = 0.8f))
            }
            else -> {}
        }
    }
}

