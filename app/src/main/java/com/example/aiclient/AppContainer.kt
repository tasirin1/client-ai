package com.example.aiclient

import android.content.Context
import com.example.aiclient.data.AppDatabase
import com.example.aiclient.data.ChatRepository
import com.example.aiclient.data.SettingsStore
import com.example.aiclient.network.GenericApiClient

class AppContainer(context: Context) {
    private val database = AppDatabase.create(context)
    val settingsStore = SettingsStore(context)
    val chatRepository = ChatRepository(database.chatDao())
    val apiClient = GenericApiClient()
}
