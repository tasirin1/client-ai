package com.example.aiclient.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ai_client_settings")

class SettingsStore(private val context: Context) {
    private val dataStore = context.dataStore

    private object Keys {
        val endpointUrl = stringPreferencesKey("endpoint_url")
        val method = stringPreferencesKey("method")
        val defaultHeaders = stringPreferencesKey("default_headers")
        val bodyTemplate = stringPreferencesKey("body_template")
        val quickInput = stringPreferencesKey("quick_input")
        val globalMemory = stringPreferencesKey("global_memory")
        val activeSessionId = stringPreferencesKey("active_session_id")
        // AI Chat settings
        val apiProvider = stringPreferencesKey("api_provider")
        val apiKey = stringPreferencesKey("api_key")
        val model = stringPreferencesKey("model")
        val baseUrl = stringPreferencesKey("base_url")
        val temperature = floatPreferencesKey("temperature")
        val maxTokens = intPreferencesKey("max_tokens")
    }

    val prefsFlow: Flow<AppPrefs> = dataStore.data.map { prefs ->
        prefs.toAppPrefs()
    }

    suspend fun update(transform: (AppPrefs) -> AppPrefs) {
        dataStore.edit { prefs ->
            val current = prefs.toAppPrefs()
            val updated = transform(current)
            prefs[Keys.endpointUrl] = updated.endpointUrl
            prefs[Keys.method] = updated.method
            prefs[Keys.defaultHeaders] = updated.defaultHeaders
            prefs[Keys.bodyTemplate] = updated.bodyTemplate
            prefs[Keys.quickInput] = updated.quickInput
            prefs[Keys.globalMemory] = updated.globalMemory
            prefs[Keys.activeSessionId] = updated.activeSessionId
            prefs[Keys.apiProvider] = updated.apiProvider
            prefs[Keys.apiKey] = updated.apiKey
            prefs[Keys.model] = updated.model
            prefs[Keys.baseUrl] = updated.baseUrl
            prefs[Keys.temperature] = updated.temperature
            prefs[Keys.maxTokens] = updated.maxTokens
        }
    }

    private fun Preferences.toAppPrefs(): AppPrefs {
        return AppPrefs(
            endpointUrl = this[Keys.endpointUrl] ?: AppPrefs().endpointUrl,
            method = this[Keys.method] ?: AppPrefs().method,
            defaultHeaders = this[Keys.defaultHeaders] ?: AppPrefs().defaultHeaders,
            bodyTemplate = this[Keys.bodyTemplate] ?: AppPrefs().bodyTemplate,
            quickInput = this[Keys.quickInput] ?: AppPrefs().quickInput,
            globalMemory = this[Keys.globalMemory] ?: AppPrefs().globalMemory,
            activeSessionId = this[Keys.activeSessionId] ?: AppPrefs().activeSessionId,
            apiProvider = this[Keys.apiProvider] ?: AppPrefs().apiProvider,
            apiKey = this[Keys.apiKey] ?: AppPrefs().apiKey,
            model = this[Keys.model] ?: AppPrefs().model,
            baseUrl = this[Keys.baseUrl] ?: AppPrefs().baseUrl,
            temperature = this[Keys.temperature] ?: AppPrefs().temperature,
            maxTokens = this[Keys.maxTokens] ?: AppPrefs().maxTokens,
        )
    }
}
