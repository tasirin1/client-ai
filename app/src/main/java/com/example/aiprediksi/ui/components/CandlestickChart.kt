package com.example.aiprediksi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.PathEffect
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

private const val MIN_CANDLES = 10
private const val MAX_CANDLES = 200
private const val DEFAULT_CANDLES = 80

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
) {
    if (candles.isEmpty()) return

    var visibleCount by remember { mutableFloatStateOf(DEFAULT_CANDLES.toFloat()) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    val crosshairIndexState = remember { mutableStateOf(-1) }

    val count = candles.size
    val visibleInt = (visibleCount.roundToInt()).coerceIn(MIN_CANDLES, minOf(MAX_CANDLES, count))
    val startIdx = ((count - visibleInt) + scrollOffset.roundToInt()).coerceIn(0, count - visibleInt)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newCount = visibleCount / zoom
                    visibleCount = newCount.coerceIn(MIN_CANDLES.toFloat(), minOf(MAX_CANDLES.toFloat(), count.toFloat()))
                    scrollOffset = (scrollOffset + pan.x / 20f).coerceIn(-(count - visibleInt).toFloat(), 0f)
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
        val padL = 65f
        val padR = 55f
        val padT = 30f
        val padB = 20f
        val chartW = w - padL - padR
        val chartH = h - padT - padB
        if (count == 0 || visibleInt == 0) return@Canvas

        val visibleCandles = candles.subList(startIdx, minOf(startIdx + visibleInt, count))
        if (visibleCandles.isEmpty()) return@Canvas

        val high = visibleCandles.maxOf { it.high }
        val low = visibleCandles.minOf { it.low }
        // Extend high/low to leave room for prediction labels
        val priceRange = high - low
        val extend = priceRange * 0.08
        val adjustedHigh = high + extend
        val adjustedLow = (low - extend).coerceAtMost(low)
        val adjRange = adjustedHigh - adjustedLow
        val pRange = if (adjRange == 0.0) 1.0 else adjRange

        fun priceToY(p: Double): Float = (padT + ((adjustedHigh - p) / pRange * chartH)).toFloat()

        val candleW = chartW / visibleInt
        val bodyW = (candleW * 0.7f).coerceIn(1.5f, 12f)
        val halfBody = bodyW / 2f
        val gridN = 4

        val textPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 21f
        }
        val boldPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 22f
            isFakeBoldText = true
        }

        // ===== GRID =====
        for (i in 0..gridN) {
            val y = padT + (chartH / gridN) * i
            drawLine(gridColor.copy(alpha = 0.5f), Offset(padL, y), Offset(w - padR, y), strokeWidth = 0.5f)
            val price = adjustedHigh - (adjRange / gridN) * i
            drawContext.canvas.nativeCanvas.drawText(fmtPrice(price), 4f, y + 7f, textPaint)
        }

        // Zoom label
        drawContext.canvas.nativeCanvas.drawText(
            "${visibleInt}/${count}", w - padR, padT - 8f, textPaint.apply { textSize = 19f })

        // ===== CANDLES =====
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
            drawLine(color, Offset(x, hY), Offset(x, lY), strokeWidth = 1.5f)
            drawRect(color, topLeft = Offset(x - halfBody, top), size = Size(bodyW, bodyH))
        }

        // ===== CROSSHAIR =====
        val chIdx = crosshairIndexState.value
        if (chIdx in startIdx until startIdx + visibleInt) {
            val visIdx = chIdx - startIdx
            val x = padL + visIdx * candleW + candleW / 2
            val c = candles[chIdx]
            val cY = priceToY(c.close)
            drawLine(Color(0x66FFFFFF), Offset(x, padT), Offset(x, padT + chartH), strokeWidth = 1f)
            drawCircle(if (c.close >= c.open) bullishColor else bearishColor, 5f, Offset(x, cY))
            drawContext.canvas.nativeCanvas.drawText(fmtPrice(c.close), x + 8f, cY - 8f, boldPaint)
        }

        // ===== PREDIKSI OVERLAY DI CHART =====
        if (analysisResult != null) {
            val dashPath = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            val labelW = padR - 4f

            // Fungsi bikin label di kanan chart
            fun drawLevelLabel(price: Double, label: String, color: Color, y: Float) {
                // Garis horizontal putus-putus
                drawLine(
                    color.copy(alpha = 0.8f),
                    Offset(padL, y),
                    Offset(w - padR, y),
                    strokeWidth = 2f,
                    pathEffect = dashPath,
                )
                // Label di kanan
                val bgRect = android.graphics.RectF(
                    w - padR, y - 10f, w - 2f, y + 10f
                )
                drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 4f, 4f, android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(180, 0, 0, 0)
                    style = android.graphics.Paint.Style.FILL
                })
                drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 4f, 4f, android.graphics.Paint().apply {
                    this.color = color.hashCode()
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.5f
                })
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    w - padR + 4f,
                    y + 4f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = 20f
                        isFakeBoldText = true
                    }
                )
            }

            // Arrow indikasi arah di sebelah kanan chart
            val dirColor = when (analysisResult.direction) {
                PredictionDirection.BULLISH -> Color(0xFF00C853)
                PredictionDirection.BEARISH -> Color(0xFFFF1744)
                PredictionDirection.NEUTRAL -> Color(0xFFAAAAAA)
            }
            val arrow = when (analysisResult.direction) {
                PredictionDirection.BULLISH -> "↑"
                PredictionDirection.BEARISH -> "↓"
                PredictionDirection.NEUTRAL -> "→"
            }

            // Arrow besar + confidence di area kanan atas chart
            val arrowPaint = android.graphics.Paint().apply {
                color = dirColor.hashCode()
                textSize = 40f
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(arrow, w - padR + 6f, padT + 40f, arrowPaint)

            val confPaint = android.graphics.Paint().apply {
                color = dirColor.hashCode()
                textSize = 18f
            }
            drawContext.canvas.nativeCanvas.drawText(
                "${"%.0f".format(analysisResult.confidence)}%",
                w - padR + 6f, padT + 65f, confPaint
            )

            // Garis Entry
            if (analysisResult.entryPrice != null) {
                val y = priceToY(analysisResult.entryPrice)
                drawLevelLabel(analysisResult.entryPrice, "Entry", Color(0xFF448AFF), y)
            } else {
                // Jika tidak ada entry, pakai harga terakhir sebagai entry
                val lastPrice = candles.last().close
                val y = priceToY(lastPrice)
                drawLevelLabel(lastPrice, "Entry", Color(0xFF448AFF), y)
            }

            // Garis Target (TP)
            if (analysisResult.targetPrice != null) {
                val y = priceToY(analysisResult.targetPrice)
                drawLevelLabel(analysisResult.targetPrice, "TP 🎯", Color(0xFF00C853), y)
            }

            // Garis Stop Loss
            if (analysisResult.stopLoss != null) {
                val y = priceToY(analysisResult.stopLoss)
                drawLevelLabel(analysisResult.stopLoss, "SL 🛑", Color(0xFFFF1744), y)
            }

            // Support levels
            for ((i, level) in analysisResult.supportLevels.withIndex()) {
                val y = priceToY(level)
                drawLine(
                    Color(0xFF448AFF).copy(alpha = 0.4f),
                    Offset(padL, y), Offset(w - padR, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "S${i + 1} ${fmtPrice(level)}",
                    padL + 4f, y - 4f,
                    android.graphics.Paint().apply {
                        color = Color(0xFF448AFF).copy(alpha = 0.7f).hashCode()
                        textSize = 18f
                    }
                )
            }

            // Resistance levels
            for ((i, level) in analysisResult.resistanceLevels.withIndex()) {
                val y = priceToY(level)
                drawLine(
                    Color(0xFFFF9800).copy(alpha = 0.4f),
                    Offset(padL, y), Offset(w - padR, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f), 0f),
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "R${i + 1} ${fmtPrice(level)}",
                    padL + 4f, y - 4f,
                    android.graphics.Paint().apply {
                        color = Color(0xFFFF9800).copy(alpha = 0.7f).hashCode()
                        textSize = 18f
                    }
                )
            }
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
        Row(
            modifier = Modifier.clickable { onToggleLive() },
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
