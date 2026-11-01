package com.zerochat.ai

import android.app.Application
import com.zerochat.ai.data.api.ApiClient
import com.zerochat.ai.data.local.TokenManager
import com.zerochat.ai.data.repository.AdminRepository
import com.zerochat.ai.data.repository.AuthRepository
import com.zerochat.ai.data.repository.ChatRepository
import com.zerochat.ai.data.repository.UpdateRepository

class ZeroChatApplication : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var adminRepository: AdminRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var updateRepository: UpdateRepository
        private set

    override fun onCreate() {
        super.onCreate()

        tokenManager = TokenManager(this)
        val apiService = ApiClient.getService(this)

        authRepository = AuthRepository(apiService, tokenManager)
        adminRepository = AdminRepository(apiService)
        chatRepository = ChatRepository(apiService)
        updateRepository = UpdateRepository(apiService)

        com.zerochat.ai.util.NotificationHelper.createNotificationChannels(this)
    }
}

