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
