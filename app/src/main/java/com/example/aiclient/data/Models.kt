package com.example.aiclient.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class AppPrefs(
    val endpointUrl: String = "https://api.example.com/v1/chat",
    val method: String = "POST",
    val defaultHeaders: String = "Content-Type: application/json\nAccept: application/json",
    val bodyTemplate: String = """
        {
          "input": {{input_json}},
          "memory": {{memory_json}},
          "history": {{history_json}}
        }
    """.trimIndent(),
    val quickInput: String = "",
    val globalMemory: String = "",
    val activeSessionId: String = "",
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
