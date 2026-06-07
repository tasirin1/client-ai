package com.example.aiclient.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets

data class BackupData(
    val version: Int = 1,
    val backupDate: Long = System.currentTimeMillis(),
    val appPrefs: AppPrefs? = null,
    val sessions: List<SessionEntity> = emptyList(),
    val messages: List<MessageEntity> = emptyList(),
)

class BackupManager(
    private val chatRepository: ChatRepository,
    private val settingsStore: SettingsStore,
) {
    suspend fun createBackup(): BackupData {
        val prefs = settingsStore.prefsFlow.first()
        val sessions = chatRepository.getAllSessionsOnce()
        val messages = chatRepository.getAllMessagesOnce()
        return BackupData(
            version = 1,
            backupDate = System.currentTimeMillis(),
            appPrefs = prefs,
            sessions = sessions,
            messages = messages,
        )
    }

    fun serialize(backup: BackupData): String {
        val root = JSONObject().apply {
            put("version", backup.version)
            put("backupDate", backup.backupDate)

            // AppPrefs
            val prefs = backup.appPrefs
            if (prefs != null) {
                val p = JSONObject().apply {
                    put("endpointUrl", prefs.endpointUrl)
                    put("method", prefs.method)
                    put("defaultHeaders", prefs.defaultHeaders)
                    put("bodyTemplate", prefs.bodyTemplate)
                    put("quickInput", prefs.quickInput)
                    put("globalMemory", prefs.globalMemory)
                    put("activeSessionId", prefs.activeSessionId)
                    put("apiProvider", prefs.apiProvider)
                    put("apiKey", prefs.apiKey)
                    put("model", prefs.model)
                    put("baseUrl", prefs.baseUrl)
                    put("temperature", prefs.temperature.toDouble())
                    put("maxTokens", prefs.maxTokens)
                    put("providerConfigs", prefs.providerConfigs)
                }
                put("appPrefs", p)
            }

            // Sessions
            val sessionsArr = JSONArray()
            for (s in backup.sessions) {
                sessionsArr.put(JSONObject().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("createdAt", s.createdAt)
                    put("updatedAt", s.updatedAt)
                })
            }
            put("sessions", sessionsArr)

            // Messages
            val messagesArr = JSONArray()
            for (m in backup.messages) {
                messagesArr.put(JSONObject().apply {
                    put("id", m.id)
                    put("sessionId", m.sessionId)
                    put("role", m.role)
                    put("content", m.content)
                    put("createdAt", m.createdAt)
                    put("timestamp", m.timestamp)
                })
            }
            put("messages", messagesArr)
        }
        return root.toString(2)
    }

    fun deserialize(jsonString: String): BackupData? {
        return try {
            val root = JSONObject(jsonString)
            val version = root.optInt("version", 1)
            val backupDate = root.optLong("backupDate", System.currentTimeMillis())

            // AppPrefs
            val appPrefs = if (root.has("appPrefs")) {
                val p = root.getJSONObject("appPrefs")
                AppPrefs(
                    endpointUrl = p.optString("endpointUrl", AppPrefs().endpointUrl),
                    method = p.optString("method", AppPrefs().method),
                    defaultHeaders = p.optString("defaultHeaders", AppPrefs().defaultHeaders),
                    bodyTemplate = p.optString("bodyTemplate", AppPrefs().bodyTemplate),
                    quickInput = p.optString("quickInput", AppPrefs().quickInput),
                    globalMemory = p.optString("globalMemory", AppPrefs().globalMemory),
                    activeSessionId = p.optString("activeSessionId", AppPrefs().activeSessionId),
                    apiProvider = p.optString("apiProvider", AppPrefs().apiProvider),
                    apiKey = p.optString("apiKey", AppPrefs().apiKey),
                    model = p.optString("model", AppPrefs().model),
                    baseUrl = p.optString("baseUrl", AppPrefs().baseUrl),
                    temperature = p.optDouble("temperature", AppPrefs().temperature.toDouble()).toFloat(),
                    maxTokens = p.optInt("maxTokens", AppPrefs().maxTokens),
                    providerConfigs = p.optString("providerConfigs", AppPrefs().providerConfigs),
                )
            } else null

            // Sessions
            val sessions = mutableListOf<SessionEntity>()
            val sessionsArr = root.optJSONArray("sessions")
            if (sessionsArr != null) {
                for (i in 0 until sessionsArr.length()) {
                    val s = sessionsArr.getJSONObject(i)
                    sessions.add(SessionEntity(
                        id = s.getString("id"),
                        title = s.optString("title", ""),
                        createdAt = s.optLong("createdAt", 0),
                        updatedAt = s.optLong("updatedAt", 0),
                    ))
                }
            }

            // Messages
            val messages = mutableListOf<MessageEntity>()
            val messagesArr = root.optJSONArray("messages")
            if (messagesArr != null) {
                for (i in 0 until messagesArr.length()) {
                    val m = messagesArr.getJSONObject(i)
                    messages.add(MessageEntity(
                        id = m.getString("id"),
                        sessionId = m.getString("sessionId"),
                        role = m.optString("role", ""),
                        content = m.optString("content", ""),
                        createdAt = m.optLong("createdAt", 0),
                        timestamp = m.optLong("timestamp", m.optLong("createdAt", 0)),
                    ))
                }
            }

            BackupData(version, backupDate, appPrefs, sessions, messages)
        } catch (e: Exception) { null }
    }

    suspend fun restore(backup: BackupData): Boolean {
        return try {
            // Restore settings
            if (backup.appPrefs != null) {
                settingsStore.update { backup.appPrefs }
            }
            // Restore sessions & messages
            if (backup.sessions.isNotEmpty() || backup.messages.isNotEmpty()) {
                chatRepository.restoreAll(backup.sessions, backup.messages)
            }
            true
        } catch (e: Exception) { false }
    }

    suspend fun writeToStream(backup: BackupData, outputStream: OutputStream) {
        val json = serialize(backup)
        outputStream.write(json.toByteArray(StandardCharsets.UTF_8))
        outputStream.flush()
    }

    suspend fun readFromStream(inputStream: InputStream): BackupData? {
        val reader = BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8))
        val text = reader.readText()
        return deserialize(text)
    }
}
