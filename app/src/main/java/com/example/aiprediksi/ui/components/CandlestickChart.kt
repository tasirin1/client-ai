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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiprediksi.data.OHLCV
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
