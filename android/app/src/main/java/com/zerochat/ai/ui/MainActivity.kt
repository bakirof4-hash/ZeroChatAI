package com.zerochat.ai.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.zerochat.ai.ZeroChatApplication
import com.zerochat.ai.ui.navigation.NavGraph
import com.zerochat.ai.ui.screens.admin.AdminViewModel
import com.zerochat.ai.ui.screens.auth.AuthViewModel
import com.zerochat.ai.ui.screens.chat.ChatViewModel
import com.zerochat.ai.ui.theme.ZeroChatAITheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ZeroChatApplication

        val authViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AuthViewModel(app.authRepository) as T
            }
        })[AuthViewModel::class.java]

        val adminViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return AdminViewModel(app.adminRepository) as T
            }
        })[AdminViewModel::class.java]

        val chatViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(app.chatRepository) as T
            }
        })[ChatViewModel::class.java]

        setContent {
            ZeroChatAITheme {
                val navController = rememberNavController()
                NavGraph(
                    navController = navController,
                    authViewModel = authViewModel,
                    adminViewModel = adminViewModel,
                    chatViewModel = chatViewModel
                )
            }
        }
    }
}

