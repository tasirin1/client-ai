package com.example.aiprediksi.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aiprediksi.data.OHLCV
import kotlin.math.abs

@Composable
fun CandlestickChart(
    candles: List<OHLCV>,
    modifier: Modifier = Modifier,
    bullishColor: Color = Color(0xFF00C853),
    bearishColor: Color = Color(0xFFFF1744),
    gridColor: Color = Color(0xFF333333),
    textColor: Color = Color(0xFFAAAAAA),
    onCrosshairMove: (OHLCV?) -> Unit = {},
) {
    if (candles.isEmpty()) return

    var crosshairIndex by remember { mutableStateOf(-1) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .pointerInput(candles.size) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val candleW = size.width.toFloat() / candles.size
                        val idx = (offset.x / candleW).toInt().coerceIn(0, candles.size - 1)
                        crosshairIndex = idx
                        onCrosshairMove(candles.getOrNull(idx))
                    },
                    onDragEnd = {
                        crosshairIndex = -1
                        onCrosshairMove(null)
                    },
                    onHorizontalDrag = { change, _ ->
                        val candleW = size.width.toFloat() / candles.size
                        val idx = (change.position.x / candleW).toInt().coerceIn(0, candles.size - 1)
                        crosshairIndex = idx
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
        val count = candles.size
        if (count == 0) return@Canvas

        val high = candles.maxOf { it.high }
        val low = candles.minOf { it.low }
        val range = high - low
        val pRange = if (range == 0.0) 1.0 else range

        fun priceToY(price: Double): Float = (padT + ((high - price) / pRange * chartH)).toFloat()

        val candleW = chartW / count
        val bodyW = (candleW * 0.65f).coerceAtMost(10f)
        val halfBody = bodyW / 2f
        val gridN = 5

        val textPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 24f
        }
        val boldPaint = android.graphics.Paint().apply {
            color = textColor.hashCode()
            textSize = 24f
            isFakeBoldText = true
        }

        // Grid horizontal
        for (i in 0..gridN) {
            val y = padT + (chartH / gridN) * i
            drawLine(gridColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 0.5f)
            val price = high - (pRange / gridN) * i
            drawContext.canvas.nativeCanvas.drawText(formatPrice(price), 4f, y + 8f, textPaint)
        }

        // Candles
        for (i in candles.indices) {
            val c = candles[i]
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

            // Crosshair
            if (i == crosshairIndex) {
                drawLine(Color(0x44FFFFFF), Offset(x, padT), Offset(x, padT + chartH), strokeWidth = 1f)
                drawCircle(color, 5f, Offset(x, cY))
                drawContext.canvas.nativeCanvas.drawText(
                    "${formatPrice(c.close)}", x + 8f, cY - 8f, boldPaint
                )
            }
        }

        // Last price
        if (candles.isNotEmpty()) {
            val lastY = priceToY(candles.last().close)
            val lastX = padL + (count - 1) * candleW + candleW / 2
            drawContext.canvas.nativeCanvas.drawText(formatPrice(candles.last().close), lastX, padT - 8f, boldPaint)
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
            text = "O: ${fmt(ohlcv.open)}  H: ${fmt(ohlcv.high)}  L: ${fmt(ohlcv.low)}  C: ${fmt(ohlcv.close)}",
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp,
        )
        Text(
            text = "Vol: ${fmtVol(ohlcv.volume)}  Perubahan: ${"%.2f".format(changePercent)}%",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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

fun formatPrice(p: Double): String = fmt(p)
