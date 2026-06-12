package com.example.aiprediksi.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import kotlinx.coroutines.flow.first
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

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
    companion object {
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
        private const val KEY_ALGORITHM = "AES"
        private const val KEY_SIZE = 256

        fun generateKey(): String {
            val keyGen = KeyGenerator.getInstance(KEY_ALGORITHM)
            keyGen.init(KEY_SIZE, SecureRandom())
            val key = keyGen.generateKey()
            return Base64.encodeToString(key.encoded, Base64.NO_WRAP)
        }

        private fun loadKey(keyBase64: String): SecretKey {
            val decoded = Base64.decode(keyBase64, Base64.NO_WRAP)
            return SecretKeySpec(decoded, KEY_ALGORITHM)
        }

        fun encrypt(plainText: String, keyBase64: String): String {
            val key = loadKey(keyBase64)
            val cipher = Cipher.getInstance(AES_MODE)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.ENCRYPT_MODE, key, spec)
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(iv.size + cipherText.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
            return Base64.encodeToString(combined, Base64.NO_WRAP)
        }

        fun decrypt(encryptedBase64: String, keyBase64: String): String {
            val key = loadKey(keyBase64)
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(AES_MODE)
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
            val cipherText = combined.copyOfRange(GCM_IV_LENGTH, combined.size)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            val plainBytes = cipher.doFinal(cipherText)
            return String(plainBytes, StandardCharsets.UTF_8)
        }
    }

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
                    put("selectedAssetType", prefs.selectedAssetType)
                    put("favoriteAssets", prefs.favoriteAssets)
                    put("defaultTimeframe", prefs.defaultTimeframe)
                    put("enableAutoAnalyze", prefs.enableAutoAnalyze)
                }
                put("appPrefs", p)
            }
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
            val messagesArr = JSONArray()
            for (m in backup.messages) {
                messagesArr.put(JSONObject().apply {
                    put("id", m.id)
                    put("sessionId", m.sessionId)
                    put("role", m.role)
                    put("content", m.content)
                    put("createdAt", m.createdAt)
                    put("timestamp", m.timestamp)
                    put("imageBase64", m.imageBase64)
                    put("assetType", m.assetType)
                    put("predictionData", m.predictionData)
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
                    selectedAssetType = p.optString("selectedAssetType", AppPrefs().selectedAssetType),
                    favoriteAssets = p.optString("favoriteAssets", AppPrefs().favoriteAssets),
                    defaultTimeframe = p.optString("defaultTimeframe", AppPrefs().defaultTimeframe),
                    enableAutoAnalyze = p.optBoolean("enableAutoAnalyze", AppPrefs().enableAutoAnalyze),
                )
            } else null
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
                        imageBase64 = m.optString("imageBase64", ""),
                        assetType = m.optString("assetType", ""),
                        predictionData = m.optString("predictionData", ""),
                    ))
                }
            }
            BackupData(version, backupDate, appPrefs, sessions, messages)
        } catch (e: Exception) { null }
    }

    suspend fun restore(backup: BackupData): Boolean {
        return try {
            if (backup.appPrefs != null) settingsStore.update { backup.appPrefs }
            if (backup.sessions.isNotEmpty() || backup.messages.isNotEmpty()) {
                chatRepository.restoreAll(backup.sessions, backup.messages)
            }
            true
        } catch (e: Exception) { false }
    }
}
