package com.example.aiprediksi.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

class MarketDataRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS) // no timeout for streaming
        .build(),
) {
    private val binanceRest = "https://api.binance.com"
    private val binanceWs = "wss://stream.binance.com:9443/ws"

    /**
     * Fetch historical klines from Binance REST API
     */
    suspend fun fetchKlines(
        symbol: String,
        interval: String,
        limit: Int = 100,
    ): Result<List<OHLCV>> = withContext(Dispatchers.IO) {
        try {
            val url = "$binanceRest/api/v3/klines?symbol=${symbol.uppercase()}&interval=$interval&limit=$limit"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("API ${response.code}: ${body.take(100)}"))
            }

            val arr = JSONArray(body)
            val candles = mutableListOf<OHLCV>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONArray(i)
                candles.add(OHLCV(
                    openTime = item.getLong(0),
                    open = item.getString(1).toDouble(),
                    high = item.getString(2).toDouble(),
                    low = item.getString(3).toDouble(),
                    close = item.getString(4).toDouble(),
                    volume = item.getString(5).toDouble(),
                    closeTime = item.getLong(6),
                ))
            }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * REAL-TIME Kline stream via Binance WebSocket
     * Emits OHLCV updates as they arrive from the exchange
     */
    fun klineStream(symbol: String, interval: String): Flow<OHLCVUpdate> = callbackFlow {
        val streamName = "${symbol.lowercase()}@kline_$interval"
        val url = "$binanceWs/$streamName"
        
        val wsRequest = Request.Builder().url(url).build()
        val ws = client.newWebSocket(wsRequest, object : okhttp3.WebSocketListener() {
            override fun onOpen(ws: okhttp3.WebSocket, response: Response) {
                // connected
            }

            override fun onMessage(ws: okhttp3.WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val k = json.getJSONObject("k")
                    val update = OHLCVUpdate(
                        openTime = k.getLong("t"),
                        closeTime = k.getLong("T"),
                        open = k.getString("o").toDouble(),
                        high = k.getString("h").toDouble(),
                        low = k.getString("l").toDouble(),
                        close = k.getString("c").toDouble(),
                        volume = k.getString("v").toDouble(),
                        isClosed = k.getBoolean("x"),
                    )
                    trySend(update)
                } catch (_: Exception) {}
            }

            override fun onFailure(ws: okhttp3.WebSocket, t: Throwable, response: Response?) {
                // Will auto-reconnect via callbackFlow retry
                close(t)
            }

            override fun onClosed(ws: okhttp3.WebSocket, code: Int, reason: String) {
                close()
            }
        })

        awaitClose {
            ws.close(1000, "Client closed")
        }
    }

    /**
     * Binance 24hr ticker for price change
     */
    suspend fun fetch24hrTicker(symbol: String): Result<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        try {
            val url = "$binanceRest/api/v3/ticker/24hr?symbol=${symbol.uppercase()}"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty"))
            val json = JSONObject(body)
            val change = json.getDouble("priceChange")
            val percent = json.getDouble("priceChangePercent")
            Result.success(change to percent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getBinanceSymbol(asset: AssetInfo): String = "${asset.symbol}USDT"

    // ======================== TECHNICAL INDICATORS ========================

    fun calculateSMA(candles: List<OHLCV>, period: Int = 20): List<Double> {
        if (candles.size < period) return emptyList()
        val closes = candles.map { it.close }
        return (period..closes.size).map { i ->
            closes.subList(i - period, i).average()
        }
    }

    fun calculateRSI(candles: List<OHLCV>, period: Int = 14): Double {
        if (candles.size < period + 1) return 50.0
        val closes = candles.map { it.close }
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gains += diff else losses -= diff
        }
        if (losses == 0.0) return 100.0
        val rs = (gains / period) / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun findSupportResistance(candles: List<OHLCV>): Pair<List<Double>, List<Double>> {
        if (candles.size < 10) return emptyList<Double>() to emptyList<Double>()
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val supports = mutableListOf<Double>()
        val resistances = mutableListOf<Double>()
        for (i in 2 until candles.size - 2) {
            if (highs[i] > highs[i - 1] && highs[i] > highs[i - 2] &&
                highs[i] > highs[i + 1] && highs[i] > highs[i + 2]) {
                resistances.add(highs[i])
            }
            if (lows[i] < lows[i - 1] && lows[i] < lows[i - 2] &&
                lows[i] < lows[i + 1] && lows[i] < lows[i + 2]) {
                supports.add(lows[i])
            }
        }
        return supports.takeLast(3) to resistances.takeLast(3)
    }

    fun calculateChange(candles: List<OHLCV>): Double {
        if (candles.size < 2) return 0.0
        val first = candles.first().close
        val last = candles.last().close
        return if (first != 0.0) ((last - first) / first) * 100.0 else 0.0
    }
}

/**
 * Real-time OHLCV update from WebSocket stream
 */
data class OHLCVUpdate(
    val openTime: Long,
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val isClosed: Boolean,
)
