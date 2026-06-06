package com.example.aiclient.network

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ApiResult(
    val statusCode: Int,
    val statusMessage: String,
    val responseBody: String,
)

fun String.toJsonString(): String {
    val escaped = buildString(length + 2) {
        append('"')
        for (char in this@toJsonString) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
    return escaped
}

class GenericApiClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun renderTemplate(
        template: String,
        input: String,
        memory: String,
        history: String,
        model: String = "",
        temperature: Float = 0.7f,
        maxTokens: Int = 4096,
        apiKey: String = "",
    ): String {
        val inputJson = input.toJsonString()
        val memoryJson = memory.toJsonString()
        val historyJson = history.toJsonString()
        return template
            .replace("{{input}}", input)
            .replace("{{input_json}}", inputJson)
            .replace("{{memory}}", memory)
            .replace("{{memory_json}}", memoryJson)
            .replace("{{history}}", history)
            .replace("{{history_json}}", historyJson)
            .replace("{{model}}", model)
            .replace("{{temperature}}", temperature.toString())
            .replace("{{max_tokens}}", maxTokens.toString())
            .replace("{{system_prompt}}", memory)
            .replace("{{system_prompt_json}}", memoryJson)
            .replace("{{api_key}}", apiKey)
    }

    suspend fun execute(
        url: String,
        method: String,
        headersText: String,
        body: String,
    ): ApiResult {
        return withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url.trim())
            val headers = parseHeaders(headersText)
            requestBuilder.headers(headers)

            val verb = method.trim().uppercase()
            if (verb == "GET" || verb == "HEAD") {
                requestBuilder.method(verb, null)
            } else {
                val mediaType = headers["Content-Type"]?.toMediaType() ?: "application/json; charset=utf-8".toMediaType()
                requestBuilder.method(verb, body.toRequestBody(mediaType))
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                ApiResult(
                    statusCode = response.code,
                    statusMessage = response.message,
                    responseBody = bodyString,
                )
            }
        }
    }

    private fun parseHeaders(text: String): Headers {
        val builder = Headers.Builder()
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                val index = line.indexOf(':')
                if (index > 0) {
                    val name = line.substring(0, index).trim()
                    val value = line.substring(index + 1).trim()
                    if (name.isNotEmpty()) {
                        builder.add(name, value)
                    }
                }
        }
        return builder.build()
    }
}
