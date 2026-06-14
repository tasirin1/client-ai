package com.example.aiclient.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class AppPrefs(
    val endpointUrl: String = "https://api.openai.com/v1/chat/completions",
    val method: String = "POST",
    val defaultHeaders: String = "Content-Type: application/json\nAccept: application/json",
    val bodyTemplate: String = "",
    val quickInput: String = "",
    val globalMemory: String = "Kamu adalah asisten AI yang membantu.",
    val activeSessionId: String = "",
    // AI Chat settings
    val apiProvider: String = "OpenAI",
    val apiKey: String = "",
    val model: String = "gpt-4o",
    val baseUrl: String = "https://api.openai.com/v1/chat/completions",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val providerConfigs: String = "{}",
    val backupEncryptionKey: String = "",
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
)

// --- Provider metadata (single source of truth) ---
data class ProviderInfo(
    val name: String,
    val defaultModel: String,
    val defaultBaseUrl: String,
    val models: List<String>,
    val apiType: String = "openai", // "openai", "anthropic", "google"
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
        name = "Perplexity",
        defaultModel = "sonar-pro",
        defaultBaseUrl = "https://api.perplexity.ai/chat/completions",
        models = listOf("sonar-pro", "sonar", "sonar-reasoning"),
    ),
    ProviderInfo(
        name = "Together AI",
        defaultModel = "mistralai/Mixtral-8x7B-Instruct-v0.1",
        defaultBaseUrl = "https://api.together.ai/v1/chat/completions",
        models = listOf(
            "mistralai/Mixtral-8x7B-Instruct-v0.1", "meta-llama/Llama-3.3-70B-Instruct",
            "meta-llama/Llama-3.1-8B-Instruct", "deepseek-ai/deepseek-coder-33b-instruct",
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
    ProviderInfo(
        name = "AI21",
        defaultModel = "jamba-1.5",
        defaultBaseUrl = "https://api.ai21.com/studio/v1/chat/completions",
        models = listOf("jamba-1.5", "jamba-1.5-mini"),
    ),
)

// --- Quick lookup maps ---
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

fun getCustomModels(prefs: AppPrefs, provider: String): List<String> =
    getProviderConfig(prefs, provider).customModels

fun addCustomModel(prefs: AppPrefs, provider: String, model: String): String {
    val config = getProviderConfig(prefs, provider)
    val updated = config.copy(customModels = (config.customModels + model).distinct())
    return setProviderConfig(prefs, provider, updated)
}

fun removeCustomModel(prefs: AppPrefs, provider: String, model: String): String {
    val config = getProviderConfig(prefs, provider)
    val updated = config.copy(customModels = config.customModels - model)
    return setProviderConfig(prefs, provider, updated)
}


// --- Per-provider config helpers ---
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
                temperature = cfg.optDouble("temperature", 0.7).toFloat(),
                maxTokens = cfg.optInt("maxTokens", 4096),
                customModels = cmList,
            )
        } else ProviderConfig()
    } catch (_: Exception) { ProviderConfig() }
}

fun getProviderConfigsMap(prefs: AppPrefs): Map<String, ProviderConfig> {
    return try {
        val json = org.json.JSONObject(prefs.providerConfigs)
        val result = mutableMapOf<String, ProviderConfig>()
        val iterator = json.keys()
        while (iterator.hasNext()) {
            val provider = iterator.next()
            val cfg = json.getJSONObject(provider)
            val cmArr = cfg.optJSONArray("customModels")
            val cmList = if (cmArr != null) {
                (0 until cmArr.length()).map { cmArr.optString(it, "") }.filter { it.isNotBlank() }
            } else emptyList()
            result[provider] = ProviderConfig(
                apiKey = cfg.optString("apiKey", ""),
                model = cfg.optString("model", getDefaultModel(provider)),
                baseUrl = cfg.optString("baseUrl", getDefaultBaseUrl(provider)),
                temperature = cfg.optDouble("temperature", 0.7).toFloat(),
                maxTokens = cfg.optInt("maxTokens", 4096),
                customModels = cmList,
            )
        }
        result
    } catch (_: Exception) { emptyMap() }
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

// Fixed ProviderConfig data class - removed duplicate fields
// The providerConfigs and backupEncryptionKey fields were duplicates
// that already existed in the AppPrefs data class

data class ProviderConfig(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val customModels: List<String> = emptyList(),
)

fun applyProviderConfig(prefs: AppPrefs, config: ProviderConfig): AppPrefs {
    return prefs.copy(
        apiKey = config.apiKey,
        model = config.model,
        baseUrl = config.baseUrl,
        temperature = config.temperature,
        maxTokens = config.maxTokens,
    )
}
