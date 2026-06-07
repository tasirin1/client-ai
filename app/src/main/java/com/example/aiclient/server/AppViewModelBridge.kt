package com.example.aiclient.server

import com.example.aiclient.data.AppPrefs
import com.example.aiclient.data.MessageEntity
import com.example.aiclient.network.toJsonString
import com.example.aiclient.server.WebServer.Companion.esc
import org.json.JSONObject
import org.json.JSONArray

object AppViewModelBridge {
    fun buildRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        if (prefs.apiKey.isBlank()) return Triple(prefs.baseUrl, prefs.defaultHeaders, prefs.bodyTemplate)
        return when (prefs.apiProvider) {
            "Google" -> buildGoogleRequest(prefs, history, input)
            "Anthropic" -> buildAnthropicRequest(prefs, history, input)
            else -> buildOpenAiRequest(prefs, history, input)
        }
    }

    private fun buildOpenAiRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("Authorization: Bearer ${prefs.apiKey}")
        }
        val messages = mutableListOf<String>()
        if (prefs.globalMemory.isNotBlank()) {
            messages.add("""{"role": "system", "content": ${prefs.globalMemory.toJsonString()}}""")
        }
        for (msg in history) {
            val role = when (msg.role) { "request" -> "user"; "response" -> "assistant"; "error" -> "assistant"; else -> msg.role }
            messages.add("""{"role": "${role}", "content": ${msg.content.toJsonString()}}""")
        }
        if (input.isNotBlank()) {
            messages.add("""{"role": "user", "content": ${input.toJsonString()}}""")
        }
        val body = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${prefs.model}\",")
            appendLine("  \"messages\": [")
            appendLine("    ${messages.joinToString(",\n")}")
            appendLine("  ],")
            appendLine("  \"temperature\": ${prefs.temperature},")
            appendLine("  \"max_tokens\": ${prefs.maxTokens}")
            append("}")
        }
        return Triple(prefs.baseUrl, headers, body)
    }

    private fun buildGoogleRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val url = prefs.baseUrl.trimEnd('/') + "/${prefs.model}:generateContent?key=${prefs.apiKey}"
        val headers = "Content-Type: application/json"
        val contents = mutableListOf<String>()
        for (msg in history) {
            val role = when (msg.role) { "request" -> "user"; "response" -> "model"; "error" -> "model"; else -> msg.role }
            contents.add("""{"role": "${role}", "parts": [{"text": ${msg.content.toJsonString()}}]}""")
        }
        if (input.isNotBlank()) {
            contents.add("""{"role": "user", "parts": [{"text": ${input.toJsonString()}}]}""")
        }
        val body = buildString {
            appendLine("{")
            if (prefs.globalMemory.isNotBlank()) {
                appendLine("  \"system_instruction\": {\"parts\": [{\"text\": ${prefs.globalMemory.toJsonString()}}]},")
            }
            appendLine("  \"contents\": [${contents.joinToString(",\n    ")}],")
            appendLine("  \"generationConfig\": {\"temperature\": ${prefs.temperature},\"maxOutputTokens\": ${prefs.maxTokens}}")
            append("}")
        }
        return Triple(url, headers, body)
    }

    private fun buildAnthropicRequest(prefs: AppPrefs, history: List<MessageEntity>, input: String): Triple<String, String, String> {
        val headers = buildString {
            append("Content-Type: application/json\n")
            append("x-api-key: ${prefs.apiKey}\n")
            append("anthropic-version: 2023-06-01")
        }
        val messages = mutableListOf<String>()
        for (msg in history) {
            val role = when (msg.role) { "request" -> "user"; "response" -> "assistant"; "error" -> "assistant"; else -> msg.role }
            messages.add("""{"role": "${role}", "content": ${msg.content.toJsonString()}}""")
        }
        if (input.isNotBlank()) {
            messages.add("""{"role": "user", "content": ${input.toJsonString()}}""")
        }
        val body = buildString {
            appendLine("{")
            appendLine("  \"model\": \"${prefs.model}\",")
            appendLine("  \"max_tokens\": ${prefs.maxTokens},")
            if (prefs.globalMemory.isNotBlank()) {
                appendLine("  \"system\": ${prefs.globalMemory.toJsonString()},")
            }
            appendLine("  \"messages\": [${messages.joinToString(",\n    ")}]")
            append("}")
        }
        return Triple(prefs.baseUrl, headers, body)
    }

    fun extractResponseText(provider: String, responseBody: String): String {
        if (responseBody.isBlank()) return ""
        return try {
            val json = JSONObject(responseBody)
            when (provider) {
                "Google" -> {
                    val candidates = json.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val content = candidates.getJSONObject(0).optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) parts.getJSONObject(0).optString("text", responseBody) else responseBody
                    } else responseBody
                }
                "Anthropic" -> {
                    val contentArr = json.optJSONArray("content")
                    if (contentArr != null && contentArr.length() > 0) contentArr.getJSONObject(0).optString("text", responseBody) else responseBody
                }
                else -> {
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val message = choices.getJSONObject(0).optJSONObject("message")
                        message?.optString("content", responseBody) ?: responseBody
                    } else json.optString("text", responseBody)
                }
            }
        } catch (e: Exception) { responseBody }
    }
}
