package com.example.aiprediksi.data

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarketDataRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build(),
) {
    private val binanceBase = "https://api.binance.com"

    /**
     * Fetch candlestick data from Binance public API (FREE, no API key needed)
     */
    suspend fun fetchBinanceKlines(
        symbol: String,    // e.g. "BTCUSDT"
        interval: String,  // e.g. "1h", "4h", "1d"
        limit: Int = 100,
    ): Result<List<OHLCV>> = withContext(Dispatchers.IO) {
        try {
            val url = "$binanceBase/api/v3/klines?symbol=${symbol.uppercase()}&interval=$interval&limit=$limit"
            val request = Request.Builder().url(url).get().build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Binance API error ${response.code}: ${body.take(100)}"))
            }

            val arr = JSONArray(body)
            val candles = mutableListOf<OHLCV>()
            for (i in 0 until arr.length()) {
                val item = arr.getJSONArray(i)
                candles.add(
                    OHLCV(
                        openTime = item.getLong(0),
                        open = item.getString(1).toDouble(),
                        high = item.getString(2).toDouble(),
                        low = item.getString(3).toDouble(),
                        close = item.getString(4).toDouble(),
                        volume = item.getString(5).toDouble(),
                        closeTime = item.getLong(6),
                    )
                )
            }
            Result.success(candles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get Binance symbol from asset info
     */
    fun getBinanceSymbol(asset: AssetInfo): String {
        return when (asset.type) {
            AssetType.CRYPTO -> "${asset.symbol}USDT"
            else -> "${asset.symbol}USDT" // fallback
        }
    }

    /**
     * Get interval from ChartInterval
     */
    fun getInterval(interval: ChartInterval): String = interval.binanceValue

    /**
     * Simple technical analysis: calculate SMA, RSI, support/resistance
     */
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
        val rs = gains / period / (losses / period)
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun findSupportResistance(candles: List<OHLCV>): Pair<List<Double>, List<Double>> {
        if (candles.size < 10) return emptyList<Double>() to emptyList<Double>()
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        // Simple pivot points
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
