package com.example.aiprediksi

import android.content.Context
import com.example.aiprediksi.data.AppDatabase
import com.example.aiprediksi.data.BackupManager
import com.example.aiprediksi.data.ChatRepository
import com.example.aiprediksi.data.MarketDataRepository
import com.example.aiprediksi.data.SettingsStore
import com.example.aiprediksi.network.GenericApiClient

class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    val settingsStore = SettingsStore(context)
    val chatRepository = ChatRepository(database.chatDao())
    val marketDataRepo = MarketDataRepository()
    val apiClient = GenericApiClient()
    val backupManager = BackupManager(chatRepository, settingsStore)
}
