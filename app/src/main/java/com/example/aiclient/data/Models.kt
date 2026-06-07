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
)


// --- Per-provider config helpers ---
fun getProviderConfig(prefs: AppPrefs, provider: String): ProviderConfig {
    return try {
        val json = org.json.JSONObject(prefs.providerConfigs)
        if (json.has(provider)) {
            val cfg = json.getJSONObject(provider)
            ProviderConfig(
                apiKey = cfg.optString("apiKey", ""),
                model = cfg.optString("model", getDefaultModel(provider)),
                baseUrl = cfg.optString("baseUrl", getDefaultBaseUrl(provider)),
                temperature = cfg.optDouble("temperature", 0.7).toFloat(),
                maxTokens = cfg.optInt("maxTokens", 4096),
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
        json.put(provider, cfg)
        json.toString(2)
    } catch (_: Exception) { prefs.providerConfigs }
}

data class ProviderConfig(
    val apiKey: String = "",
    val model: String = "",
    val baseUrl: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val providerConfigs: String = "{}",
)

fun getDefaultModel(provider: String): String = when (provider) {
    "OpenAI" -> "gpt-4o"
    "Anthropic" -> "claude-3-5-sonnet-20241022"
    "Google" -> "gemini-1.5-flash"
    "Deepseek" -> "deepseek-chat"
    "Groq" -> "llama-3.3-70b-versatile"
    "OpenRouter" -> "openai/gpt-4o"
    else -> ""
}

fun getDefaultBaseUrl(provider: String): String = when (provider) {
    "OpenAI" -> "https://api.openai.com/v1/chat/completions"
    "Anthropic" -> "https://api.anthropic.com/v1/messages"
    "Google" -> "https://generativelanguage.googleapis.com/v1beta/models"
    "Deepseek" -> "https://api.deepseek.com/v1/chat/completions"
    "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
    "OpenRouter" -> "https://openrouter.ai/api/v1/chat/completions"
    else -> ""
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
