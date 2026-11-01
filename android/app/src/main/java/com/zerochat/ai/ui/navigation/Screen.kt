package com.zerochat.ai.ui.navigation

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object RegisterEmail : Screen("register_email")
    object VerifyCode : Screen("verify_code/{purpose}") {
        fun createRoute(purpose: String = "REGISTRATION") = "verify_code/$purpose"
    }
    object CreatePassword : Screen("create_password")
    object ForgotPassword : Screen("forgot_password")
    object ConversationsList : Screen("conversations_list")
    object ChatFeed : Screen("chat_feed")
    object UserSearch : Screen("user_search")
    object Profile : Screen("profile")
    object SuperAdminDashboard : Screen("super_admin_dashboard")
    object QrCode : Screen("qr_code")
    object ScanQr : Screen("scan_qr")
    object DirectChat : Screen("direct_chat/{userId}") {
        fun createRoute(userId: Int) = "direct_chat/$userId"
    }
    object FileSecurity : Screen("file_security")
}
