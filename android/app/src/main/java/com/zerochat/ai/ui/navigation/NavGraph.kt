package com.zerochat.ai.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.zerochat.ai.ui.screens.admin.AdminViewModel
import com.zerochat.ai.ui.screens.admin.SuperAdminDashboardScreen
import com.zerochat.ai.ui.screens.auth.*
import com.zerochat.ai.ui.screens.chat.*
import com.zerochat.ai.ui.screens.profile.ProfileScreen
import com.zerochat.ai.ui.screens.profile.QrCodeScreen
import com.zerochat.ai.ui.screens.profile.ScanQrScreen
import com.zerochat.ai.ui.screens.security.FileSecurityScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    authViewModel: AuthViewModel,
    adminViewModel: AdminViewModel,
    chatViewModel: ChatViewModel
) {
    val authState by authViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val startDestination = if (authState.isLoggedIn) Screen.ConversationsList.route else Screen.Login.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToRegister = { navController.navigate(Screen.RegisterEmail.route) },
                onNavigateToForgotPassword = { navController.navigate(Screen.ForgotPassword.route) },
                onNavigateToVerifyCode = { purpose ->
                    navController.navigate(Screen.VerifyCode.createRoute(purpose))
                },
                onLoginSuccess = {
                    chatViewModel.loadConversations()
                    chatViewModel.loadMessages()
                    navController.navigate(Screen.ConversationsList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.RegisterEmail.route) {
            RegisterEmailScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    chatViewModel.loadConversations()
                    chatViewModel.loadMessages()
                    navController.navigate(Screen.ConversationsList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onCodeSent = {
                    navController.navigate(Screen.VerifyCode.createRoute("REGISTRATION"))
                }
            )
        }

        composable(
            route = Screen.VerifyCode.route,
            arguments = listOf(navArgument("purpose") { type = NavType.StringType; defaultValue = "REGISTRATION" })
        ) { backStackEntry ->
            val purpose = backStackEntry.arguments?.getString("purpose") ?: "REGISTRATION"
            VerifyCodeScreen(
                viewModel = authViewModel,
                purpose = purpose,
                onNavigateBack = { navController.popBackStack() },
                onCodeVerified = {
                    if (purpose == "LOGIN") {
                        authViewModel.passwordlessLogin(
                            onSuccess = {
                                chatViewModel.loadConversations()
                                chatViewModel.loadMessages()
                                navController.navigate(Screen.ConversationsList.route) {
                                    popUpTo(Screen.Login.route) { inclusive = true }
                                }
                            }
                        )
                    } else if (purpose == "REGISTRATION") {
                        navController.navigate(Screen.CreatePassword.route)
                    } else {
                        navController.navigate(Screen.ForgotPassword.route)
                    }
                }
            )
        }

        composable(Screen.CreatePassword.route) {
            CreatePasswordScreen(
                viewModel = authViewModel,
                onRegistrationSuccess = {
                    chatViewModel.loadConversations()
                    chatViewModel.loadMessages()
                    navController.navigate(Screen.ConversationsList.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onResetComplete = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                    }
                }
            )
        }

        // Telegram-inspired Main Conversations List
        composable(Screen.ConversationsList.route) {
            ConversationsListScreen(
                chatViewModel = chatViewModel,
                authViewModel = authViewModel,
                onNavigateToCommunityChat = { navController.navigate(Screen.ChatFeed.route) },
                onNavigateToDirectChat = { targetUserId ->
                    val user = chatState.conversations.firstOrNull { it.recipientUser?.id == targetUserId }?.recipientUser
                    if (user != null) {
                        chatViewModel.setDirectTarget(user)
                    }
                    navController.navigate(Screen.DirectChat.createRoute(targetUserId))
                },
                onNavigateToUserSearch = { navController.navigate(Screen.UserSearch.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToQrCode = { navController.navigate(Screen.QrCode.route) },
                onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                onNavigateToFileSecurity = { navController.navigate(Screen.FileSecurity.route) },
                onNavigateToAdminDashboard = { navController.navigate(Screen.SuperAdminDashboard.route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // Global Public Community Chat
        composable(Screen.ChatFeed.route) {
            ChatFeedScreen(
                currentUser = authState.currentUser,
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSearch = { navController.navigate(Screen.UserSearch.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToAdminDashboard = { navController.navigate(Screen.SuperAdminDashboard.route) },
                onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                onNavigateToFileSecurity = { navController.navigate(Screen.FileSecurity.route) }
            )
        }

        composable(Screen.UserSearch.route) {
            UserSearchScreen(
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onUserSelected = { targetUser ->
                    chatViewModel.setDirectTarget(targetUser)
                    navController.navigate(Screen.DirectChat.createRoute(targetUser.id))
                }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                user = authState.currentUser,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAdminDashboard = { navController.navigate(Screen.SuperAdminDashboard.route) },
                onNavigateToQrCode = { navController.navigate(Screen.QrCode.route) },
                onNavigateToScanQr = { navController.navigate(Screen.ScanQr.route) },
                onNavigateToFileSecurity = { navController.navigate(Screen.FileSecurity.route) },
                onLogout = {
                    authViewModel.logout {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Screen.QrCode.route) {
            QrCodeScreen(
                user = authState.currentUser,
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToScan = { navController.navigate(Screen.ScanQr.route) }
            )
        }

        composable(Screen.ScanQr.route) {
            ScanQrScreen(
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartDirectChat = { targetUser ->
                    chatViewModel.setDirectTarget(targetUser)
                    navController.navigate(Screen.DirectChat.createRoute(targetUser.id))
                }
            )
        }

        composable(
            route = Screen.DirectChat.route,
            arguments = listOf(navArgument("userId") { type = NavType.IntType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getInt("userId") ?: 0
            val target = chatState.currentDirectTarget?.takeIf { it.id == userId }
                ?: chatState.conversations.firstOrNull { it.recipientUser?.id == userId }?.recipientUser

            if (target != null) {
                DirectChatScreen(
                    targetUser = target,
                    currentUser = authState.currentUser,
                    chatViewModel = chatViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToFileSecurity = { navController.navigate(Screen.FileSecurity.route) }
                )
            } else {
                navController.popBackStack()
            }
        }

        composable(Screen.FileSecurity.route) {
            FileSecurityScreen(
                chatViewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SuperAdminDashboard.route) {
            SuperAdminDashboardScreen(
                viewModel = adminViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
