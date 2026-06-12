package com.example.aiprediksi.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ===================== ASET / PREDIKSI =====================

enum class AssetType(val label: String, val symbol: String, val emoji: String) {
    CRYPTO("Crypto", "₿", "₿"),
    STOCK("Saham", "📈", "📈"),
    FOREX("Forex", "💱", "💱"),
    COMMODITY("Komoditas", "🛢️", "🛢️"),
    INDEX("Indeks", "📊", "📊"),
    ETF("ETF", "📋", "📋"),
}

data class AssetInfo(
    val name: String,
    val symbol: String,
    val type: AssetType,
    val price: Double = 0.0,
    val change24h: Double = 0.0,
    val changePercent24h: Double = 0.0,
)

data class PredictionResult(
    val asset: String,
    val direction: PredictionDirection,
    val confidence: Float,
    val targetPrice: Double? = null,
    val stopLoss: Double? = null,
    val reasoning: String = "",
    val timeframe: String = "24h",
    val riskLevel: RiskLevel = RiskLevel.MEDIUM,
    val indicators: List<String> = emptyList(),
)

enum class PredictionDirection(val label: String, val emoji: String) {
    BULLISH("Bullish", "🟢"),
    BEARISH("Bearish", "🔴"),
    NEUTRAL("Netral", "⚪"),
}

enum class RiskLevel(val label: String) {
    LOW("Rendah"),
    MEDIUM("Sedang"),
    HIGH("Tinggi"),
}

// ===================== PROVIDER AI (dari client-ai) =====================

data class AppPrefs(
    val endpointUrl: String = "https://api.openai.com/v1/chat/completions",
    val method: String = "POST",
    val defaultHeaders: String = "Content-Type: application/json\nAccept: application/json",
    val bodyTemplate: String = "",
    val quickInput: String = "",
    val globalMemory: String = "Kamu adalah asisten prediksi AI yang ahli dalam analisis crypto, saham, forex, dan komoditas. Berikan analisis berdasarkan data yang tersedia.",
    val activeSessionId: String = "",
    val apiProvider: String = "OpenAI",
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val baseUrl: String = "https://api.openai.com/v1/chat/completions",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 4096,
    val providerConfigs: String = "{}",
    val backupEncryptionKey: String = "",
    val selectedAssetType: String = AssetType.CRYPTO.name,
    val favoriteAssets: String = "[]",
    val defaultTimeframe: String = "24h",
    val enableAutoAnalyze: Boolean = false,
)

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index(value = ["sessionId"])],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val timestamp: Long = createdAt,
    val imageBase64: String = "",
    val assetType: String = "",
    val predictionData: String = "", // JSON dari PredictionResult
)

// --- Provider metadata ---
data class ProviderInfo(
    val name: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val models: List<String>,
    val apiType: String = "openai",
)

val ALL_PROVIDERS: List<ProviderInfo> = listOf(
    ProviderInfo(
        name = "OpenAI",
        defaultModel = "gpt-4o",
        defaultBaseUrl = "https://api.openai.com/v1/chat/completions",
        models = listOf(
            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo",
            "o1", "o1-mini", "o3-mini",
        ),
    ),
    ProviderInfo(
        name = "Anthropic",
        defaultModel = "claude-3-5-sonnet-20241022",
        defaultBaseUrl = "https://api.anthropic.com/v1/messages",
        models = listOf(
            "claude-3-5-sonnet-20241022", "claude-3-opus-20240229",
            "claude-3-sonnet-20240229", "claude-3-haiku-20240307",
            "claude-3-5-haiku-20241022",
        ),
        apiType = "anthropic",
    ),
    ProviderInfo(
        name = "Google",
        defaultModel = "gemini-1.5-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/models",
        models = listOf(
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro",
            "gemini-2.0-flash-lite", "gemini-1.0-pro",
        ),
        apiType = "google",
    ),
    ProviderInfo(
        name = "Deepseek",
        defaultModel = "deepseek-chat",
        defaultBaseUrl = "https://api.deepseek.com/v1/chat/completions",
        models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder"),
    ),
    ProviderInfo(
        name = "Groq",
        defaultModel = "llama-3.3-70b-versatile",
        defaultBaseUrl = "https://api.groq.com/openai/v1/chat/completions",
        models = listOf(
            "llama-3.3-70b-versatile", "llama-3.1-8b-instant", "llama-guard-3-8b",
            "llama3-70b-8192", "llama3-8b-8192", "mixtral-8x7b-32768",
            "gemma2-9b-it", "deepseek-r1-distill-llama-70b",
            "llama-3.2-11b-vision-preview",
        ),
    ),
    ProviderInfo(
        name = "OpenRouter",
        defaultModel = "openai/gpt-4o",
        defaultBaseUrl = "https://openrouter.ai/api/v1/chat/completions",
        models = listOf(
            "openai/gpt-4o", "openai/gpt-4o-mini", "openai/gpt-4-turbo",
            "anthropic/claude-3.5-sonnet", "anthropic/claude-3-haiku",
            "google/gemini-2.5-flash", "google/gemini-2.0-flash", "google/gemini-1.5-flash",
            "meta-llama/llama-3.3-70b-instruct", "meta-llama/llama-3.1-8b-instruct",
            "meta-llama/llama-3.2-90b-vision-instruct", "meta-llama/llama-3.2-11b-vision-instruct",
            "deepseek/deepseek-r1", "deepseek/deepseek-chat",
            "mistralai/mistral-small-24b-instruct", "mistralai/mistral-large",
            "cohere/command-r-plus", "cohere/command-r",
            "qwen/qwen-2.5-72b-instruct", "x-ai/grok-2",
        ),
    ),
    ProviderInfo(
        name = "Mistral",
        defaultModel = "mistral-large",
        defaultBaseUrl = "https://api.mistral.ai/v1/chat/completions",
        models = listOf(
            "mistral-large", "mistral-medium", "mistral-small", "open-mistral-nemo",
            "codestral",
        ),
    ),
    ProviderInfo(
        name = "xAI",
        defaultModel = "grok-2",
        defaultBaseUrl = "https://api.x.ai/v1/chat/completions",
        models = listOf("grok-2", "grok-beta"),
    ),
    ProviderInfo(
        name = "Cohere",
        defaultModel = "command-r-plus",
        defaultBaseUrl = "https://api.cohere.ai/v1/chat/completions",
        models = listOf("command-r-plus", "command-r", "command"),
    ),
    ProviderInfo(
        name = "Together AI",
        defaultModel = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
        defaultBaseUrl = "https://api.together.xyz/v1/chat/completions",
        models = listOf(
            "meta-llama/Llama-3.3-70B-Instruct-Turbo",
            "meta-llama/Llama-3.1-8B-Instruct-Turbo",
            "deepseek-ai/deepseek-coder-33b-instruct",
        ),
    ),
    ProviderInfo(
        name = "Fireworks AI",
        defaultModel = "accounts/fireworks/models/llama-v3p3-70b-instruct",
        defaultBaseUrl = "https://api.fireworks.ai/inference/v1/chat/completions",
        models = listOf(
            "accounts/fireworks/models/llama-v3p3-70b-instruct",
            "accounts/fireworks/models/llama-v3p1-8b-instruct",
            "accounts/fireworks/models/mixtral-8x7b-instruct",
        ),
    ),
    ProviderInfo(
        name = "GitHub Models",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://models.inference.ai.azure.com/chat/completions",
        models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo"),
    ),
)

private val providerMap: Map<String, ProviderInfo> by lazy {
    ALL_PROVIDERS.associateBy { it.name }
}

private val providerNames: List<String> by lazy {
    ALL_PROVIDERS.map { it.name } + "Custom"
}

fun getAllProviderNames(): List<String> = providerNames
fun getModelsForProvider(provider: String): List<String> =
    providerMap[provider]?.models ?: emptyList()
fun getBaseUrlForProvider(provider: String): String =
    providerMap[provider]?.defaultBaseUrl ?: ""
fun getDefaultModel(provider: String): String = when (provider) {
    "Custom" -> ""
    else -> providerMap[provider]?.defaultModel ?: ""
}
fun getDefaultBaseUrl(provider: String): String = when (provider) {
    "Custom" -> ""
    else -> providerMap[provider]?.defaultBaseUrl ?: ""
}
fun getFallbackChain(): List<Pair<String, List<String>>> =
    ALL_PROVIDERS.map { it.name to it.models }
fun getApiType(provider: String): String =
    providerMap[provider]?.apiType ?: "openai"

// --- Per-provider config ---
data class ProviderConfig(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.3f,
    val maxTokens: Int = 4096,
    val customModels: List<String> = emptyList(),
)

fun getProviderConfig(prefs: AppPrefs, provider: String): ProviderConfig {
    return try {
        val json = org.json.JSONObject(prefs.providerConfigs)
        if (json.has(provider)) {
            val cfg = json.getJSONObject(provider)
            val cmArr = cfg.optJSONArray("customModels")
            val cmList = if (cmArr != null) {
                (0 until cmArr.length()).map { cmArr.optString(it, "") }.filter { it.isNotBlank() }
            } else emptyList()
            ProviderConfig(
                apiKey = cfg.optString("apiKey", ""),
                model = cfg.optString("model", getDefaultModel(provider)),
                baseUrl = cfg.optString("baseUrl", getDefaultBaseUrl(provider)),
                temperature = cfg.optDouble("temperature", 0.3).toFloat(),
                maxTokens = cfg.optInt("maxTokens", 4096),
                customModels = cmList,
            )
        } else ProviderConfig()
    } catch (_: Exception) { ProviderConfig() }
}

fun setProviderConfig(prefs: AppPrefs, provider: String, config: ProviderConfig): String {
    return try {
        val json = org.json.JSONObject(prefs.providerConfigs)
        val cfg = org.json.JSONObject()
        cfg.put("apiKey", config.apiKey)
        cfg.put("model", config.model)
        cfg.put("baseUrl", config.baseUrl)
        cfg.put("temperature", config.temperature.toDouble())
        cfg.put("maxTokens", config.maxTokens)
        val cmArr = org.json.JSONArray(config.customModels)
        cfg.put("customModels", cmArr)
        json.put(provider, cfg)
        json.toString(2)
    } catch (_: Exception) { prefs.providerConfigs }
}

fun applyProviderConfig(prefs: AppPrefs, config: ProviderConfig): AppPrefs {
    return prefs.copy(
        apiKey = config.apiKey,
        model = config.model,
        baseUrl = config.baseUrl,
        temperature = config.temperature,
        maxTokens = config.maxTokens,
    )
}

// --- Daftar aset yang bisa diprediksi ---
object AssetDatabase {
    val cryptoAssets = listOf(
        AssetInfo("Bitcoin", "BTC", AssetType.CRYPTO),
        AssetInfo("Ethereum", "ETH", AssetType.CRYPTO),
        AssetInfo("Binance Coin", "BNB", AssetType.CRYPTO),
        AssetInfo("Solana", "SOL", AssetType.CRYPTO),
        AssetInfo("XRP", "XRP", AssetType.CRYPTO),
        AssetInfo("Cardano", "ADA", AssetType.CRYPTO),
        AssetInfo("Dogecoin", "DOGE", AssetType.CRYPTO),
        AssetInfo("Polkadot", "DOT", AssetType.CRYPTO),
        AssetInfo("Avalanche", "AVAX", AssetType.CRYPTO),
        AssetInfo("Chainlink", "LINK", AssetType.CRYPTO),
        AssetInfo("Polygon", "MATIC", AssetType.CRYPTO),
        AssetInfo("Shiba Inu", "SHIB", AssetType.CRYPTO),
        AssetInfo("Litecoin", "LTC", AssetType.CRYPTO),
        AssetInfo("Uniswap", "UNI", AssetType.CRYPTO),
        AssetInfo("Stellar", "XLM", AssetType.CRYPTO),
        AssetInfo("Near Protocol", "NEAR", AssetType.CRYPTO),
        AssetInfo("Aptos", "APT", AssetType.CRYPTO),
        AssetInfo("Arbitrum", "ARB", AssetType.CRYPTO),
        AssetInfo("Optimism", "OP", AssetType.CRYPTO),
        AssetInfo("Sui", "SUI", AssetType.CRYPTO),
        AssetInfo("Pepe", "PEPE", AssetType.CRYPTO),
        AssetInfo("Toncoin", "TON", AssetType.CRYPTO),
        AssetInfo("TRON", "TRX", AssetType.CRYPTO),
        AssetInfo("Internet Computer", "ICP", AssetType.CRYPTO),
        AssetInfo("Render", "RNDR", AssetType.CRYPTO),
        AssetInfo("Fantom", "FTM", AssetType.CRYPTO),
        AssetInfo("Hedera", "HBAR", AssetType.CRYPTO),
        AssetInfo("Cronos", "CRO", AssetType.CRYPTO),
        AssetInfo("VeChain", "VET", AssetType.CRYPTO),
        AssetInfo("The Graph", "GRT", AssetType.CRYPTO),
    )

    val stockAssets = listOf(
        AssetInfo("Tesla", "TSLA", AssetType.STOCK),
        AssetInfo("Apple", "AAPL", AssetType.STOCK),
        AssetInfo("Microsoft", "MSFT", AssetType.STOCK),
        AssetInfo("NVIDIA", "NVDA", AssetType.STOCK),
        AssetInfo("Amazon", "AMZN", AssetType.STOCK),
        AssetInfo("Meta", "META", AssetType.STOCK),
        AssetInfo("Google", "GOOGL", AssetType.STOCK),
        AssetInfo("Netflix", "NFLX", AssetType.STOCK),
        AssetInfo("AMD", "AMD", AssetType.STOCK),
        AssetInfo("Intel", "INTC", AssetType.STOCK),
        AssetInfo("Palantir", "PLTR", AssetType.STOCK),
        AssetInfo("CrowdStrike", "CRWD", AssetType.STOCK),
        AssetInfo("Coinbase", "COIN", AssetType.STOCK),
        AssetInfo("MicroStrategy", "MSTR", AssetType.STOCK),
        AssetInfo("Block Inc", "SQ", AssetType.STOCK),
        AssetInfo("Shopify", "SHOP", AssetType.STOCK),
        AssetInfo("Snowflake", "SNOW", AssetType.STOCK),
        AssetInfo("Adobe", "ADBE", AssetType.STOCK),
        AssetInfo("Salesforce", "CRM", AssetType.STOCK),
        AssetInfo("Alibaba", "BABA", AssetType.STOCK),
        AssetInfo("Berkshire Hathaway", "BRK.B", AssetType.STOCK),
        AssetInfo("JPMorgan", "JPM", AssetType.STOCK),
        AssetInfo("Johnson & Johnson", "JNJ", AssetType.STOCK),
        AssetInfo("Walmart", "WMT", AssetType.STOCK),
        AssetInfo("Visa", "V", AssetType.STOCK),
        AssetInfo("Mastercard", "MA", AssetType.STOCK),
        AssetInfo("PayPal", "PYPL", AssetType.STOCK),
        AssetInfo("Uber", "UBER", AssetType.STOCK),
        AssetInfo("Airbnb", "ABNB", AssetType.STOCK),
        AssetInfo("Samsung", "SSNLF", AssetType.STOCK),
    )

    val forexAssets = listOf(
        AssetInfo("EUR/USD", "EURUSD", AssetType.FOREX),
        AssetInfo("GBP/USD", "GBPUSD", AssetType.FOREX),
        AssetInfo("USD/JPY", "USDJPY", AssetType.FOREX),
        AssetInfo("USD/CHF", "USDCHF", AssetType.FOREX),
        AssetInfo("AUD/USD", "AUDUSD", AssetType.FOREX),
        AssetInfo("USD/CAD", "USDCAD", AssetType.FOREX),
        AssetInfo("NZD/USD", "NZDUSD", AssetType.FOREX),
        AssetInfo("EUR/GBP", "EURGBP", AssetType.FOREX),
        AssetInfo("EUR/JPY", "EURJPY", AssetType.FOREX),
        AssetInfo("GBP/JPY", "GBPJPY", AssetType.FOREX),
    )

    val commodityAssets = listOf(
        AssetInfo("Emas", "XAU/USD", AssetType.COMMODITY),
        AssetInfo("Perak", "XAG/USD", AssetType.COMMODITY),
        AssetInfo("Minyak WTI", "CL/USD", AssetType.COMMODITY),
        AssetInfo("Minyak Brent", "BN/USD", AssetType.COMMODITY),
        AssetInfo("Gas Alam", "NG/USD", AssetType.COMMODITY),
        AssetInfo("Tembaga", "HG/USD", AssetType.COMMODITY),
        AssetInfo("Platinum", "PL/USD", AssetType.COMMODITY),
        AssetInfo("Palladium", "PA/USD", AssetType.COMMODITY),
        AssetInfo("Wheat", "ZW/USD", AssetType.COMMODITY),
        AssetInfo("Jagung", "ZC/USD", AssetType.COMMODITY),
    )

    val indexAssets = listOf(
        AssetInfo("S&P 500", "SPX", AssetType.INDEX),
        AssetInfo("Nasdaq", "IXIC", AssetType.INDEX),
        AssetInfo("Dow Jones", "DJI", AssetType.INDEX),
        AssetInfo("FTSE 100", "UK100", AssetType.INDEX),
        AssetInfo("Nikkei 225", "N225", AssetType.INDEX),
        AssetInfo("DAX", "DAX", AssetType.INDEX),
        AssetInfo("HSI", "HSI", AssetType.INDEX),
        AssetInfo("CAC 40", "CAC40", AssetType.INDEX),
        AssetInfo("S&P/ASX 200", "ASX200", AssetType.INDEX),
        AssetInfo("KOSPI", "KS11", AssetType.INDEX),
    )

    fun getAssetsByType(type: AssetType): List<AssetInfo> = when (type) {
        AssetType.CRYPTO -> cryptoAssets
        AssetType.STOCK -> stockAssets
        AssetType.FOREX -> forexAssets
        AssetType.COMMODITY -> commodityAssets
        AssetType.INDEX -> indexAssets
        AssetType.ETF -> stockAssets // fallback
    }

    fun getAllAssets(): List<AssetInfo> = cryptoAssets + stockAssets + forexAssets + commodityAssets + indexAssets

    fun searchAssets(query: String): List<AssetInfo> {
        val q = query.lowercase()
        return getAllAssets().filter {
            it.name.lowercase().contains(q) || it.symbol.lowercase().contains(q)
        }
    }

    fun getTimeframes(): List<String> = listOf("1h", "4h", "12h", "24h", "3d", "1w", "1M")
}

// --- Sinyal / Indikator teknikal ---
data class TechnicalIndicator(
    val name: String,
    val value: String,
    val signal: SignalType,
    val description: String = "",
)

enum class SignalType(val emoji: String) {
    BUY("🟢"),
    SELL("🔴"),
    NEUTRAL("⚪"),
}
