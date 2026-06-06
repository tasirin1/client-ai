package com.example.aiclient.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val chatDao: ChatDao,
) {
    fun observeSessions(): Flow<List<SessionEntity>> = chatDao.observeSessions()

    fun observeMessages(sessionId: String): Flow<List<MessageEntity>> = chatDao.observeMessages(sessionId)

    fun observeLastMessagesForAllSessions(): Flow<List<MessageEntity>> = chatDao.observeLastMessagesForAllSessions()

    suspend fun getSessionOnce(sessionId: String): SessionEntity? = chatDao.getSessionOnce(sessionId)

    suspend fun getLastMessage(sessionId: String): MessageEntity? = chatDao.getLastMessage(sessionId)

    suspend fun ensureSession(sessionId: String?, titleHint: String = "Sesi baru"): SessionEntity {
        val now = System.currentTimeMillis()
        val session = when {
            sessionId.isNullOrBlank() -> SessionEntity(
                id = UUID.randomUUID().toString(),
                title = titleHint,
                createdAt = now,
                updatedAt = now,
            )
            else -> SessionEntity(
                id = sessionId,
                title = titleHint,
                createdAt = now,
                updatedAt = now,
            )
        }
        chatDao.upsertSession(session)
        return session
    }

    suspend fun createSession(title: String): SessionEntity {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsertSession(session)
        return session
    }

    suspend fun renameSession(sessionId: String, title: String) {
        val now = System.currentTimeMillis()
        val existing = chatDao.getSessionOnce(sessionId)
        val session = existing?.copy(title = title, updatedAt = now)
            ?: SessionEntity(
                id = sessionId,
                title = title,
                createdAt = now,
                updatedAt = now,
            )
        chatDao.upsertSession(session)
    }

    suspend fun addMessage(sessionId: String, role: String, content: String) {
        val now = System.currentTimeMillis()
        chatDao.upsertMessage(
            MessageEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = role,
                content = content,
                createdAt = now,
                timestamp = now,
            ),
        )
    }

    suspend fun getMessagesOnce(sessionId: String): List<MessageEntity> = chatDao.getMessagesOnce(sessionId)

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesBySession(sessionId)
        chatDao.deleteSession(sessionId)
    }
}
