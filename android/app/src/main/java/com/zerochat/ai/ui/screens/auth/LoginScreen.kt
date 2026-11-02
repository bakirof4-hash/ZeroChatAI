package com.zerochat.ai.ui.screens.auth

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.zerochat.ai.BuildConfig
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.GoogleSignInButton
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.components.ServerConfigDialog
import com.zerochat.ai.ui.theme.*
import com.zerochat.ai.util.GoogleAuthManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    onNavigateToVerifyCode: (String) -> Unit = {},
    onLoginSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showServerDialog by remember { mutableStateOf(false) }
    var showNoPlayServicesOption by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Universal Google Play Services detection
    val isPlayServicesAvailable = remember { GoogleAuthManager.isGooglePlayServicesAvailable(context) }

    // Legacy fallback client for devices where Credential Manager is unsupported or fails
    // Google Sign-In Activity Result Launcher (Legacy Fallback)
    val legacyGoogleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.signInWithGoogle(idToken, onSuccess = onLoginSuccess)
                } else {
                    viewModel.setErrorMessage(
                        "Google ID token qaytarmadi. Iltimos, Google Cloud / Firebase'da Web Client ID sozlamasini tekshiring."
                    )
                }
            } catch (e: ApiException) {
                // 12501: User cancelled
                if (e.statusCode != 12501) {
                    val detailedMsg = GoogleAuthManager.getErrorMessageForStatusCode(e.statusCode, e.message)
                    viewModel.setErrorMessage(detailedMsg)
                }
            } catch (e: Exception) {
                viewModel.setErrorMessage("Sign-In error: ${e.localizedMessage}")
            }
        }
    }

    // Unified launcher: Modern Credential Manager with automatic legacy fallback
    val startGoogleSignIn: () -> Unit = {
        viewModel.clearMessages()
        val webClientId = GoogleAuthManager.resolveWebClientId(context)
        if (webClientId.isBlank()) {
            viewModel.setErrorMessage(
                "Google Sign-In sozlanmagan: Web Client ID kiritilmagan.\n" +
                "Iltimos, server sozlamalari orqali yoki local.properties faylida GOOGLE_WEB_CLIENT_ID ni ko'rsating."
            )
        } else {
            coroutineScope.launch {
                GoogleAuthManager.signInWithCredentialManager(
                    context = context,
                    webClientId = webClientId,
                    onTokenReceived = { idToken ->
                        viewModel.signInWithGoogle(idToken, onSuccess = onLoginSuccess)
                    },
                    onError = { err ->
                        viewModel.setErrorMessage(err)
                    },
                    onFallbackToLegacy = {
                        val client = GoogleAuthManager.createLegacyGoogleSignInClient(context, webClientId)
                        legacyGoogleSignInLauncher.launch(client.signInIntent)
                    }
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
    ) {
        val screenHeight = maxHeight
        val isCompactScreen = screenHeight < 640.dp
        val horizontalPadding = if (maxWidth > 600.dp) 64.dp else 24.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 16.dp)
        ) {
            // Server Configuration Button for testing & network switching
            IconButton(
                onClick = { showServerDialog = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = "Server Settings",
                    tint = NeonBlue
                )
            }

            if (showServerDialog) {
                ServerConfigDialog(
                    onDismiss = { showServerDialog = false },
                    onUrlUpdated = { }
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(if (isCompactScreen) 24.dp else 40.dp))

                // Glowing App Icon / Branding
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isCompactScreen) 64.dp else 76.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(NeonBlue, ElectricBlue, Color(0xFF001F3F))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "ZeroChat Shield",
                        tint = Color.White,
                        modifier = Modifier.size(if (isCompactScreen) 34.dp else 40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ZeroChat AI",
                    color = NeonBlue,
                    fontSize = if (isCompactScreen) 24.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp
                )

                Text(
                    text = "Universal Google Identity & Real-Time Messaging",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isCompactScreen) 24.dp else 32.dp))

                // Google Authentication Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign In",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Supports Samsung, Xiaomi, POCO, Pixel, OPPO, Vivo, realme & all Android 9+",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Primary Google Sign-In Button (Credential Manager + Legacy Fallback)
                        GoogleSignInButton(
                            text = "Continue with Google",
                            isLoading = state.isLoading,
                            onClick = startGoogleSignIn
                        )

                        if (state.errorMessage != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = state.errorMessage!!,
                                color = StatusError,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (state.successMessage != null) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = state.successMessage!!,
                                color = StatusSuccess,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Device without Google Play Services Alternative Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showNoPlayServicesOption = !showNoPlayServicesOption }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (showNoPlayServicesOption) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!isPlayServicesAvailable)
                                    "Google Play Services unavailable? Sign in with code"
                                else
                                    "No Google account or alternative sign-in",
                                color = NeonBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Alternative Zero-Password Email OTP Flow
                        AnimatedVisibility(visible = showNoPlayServicesOption || !isPlayServicesAvailable) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, NeonBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Passwordless 6-Digit Code Login",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Receive a 6-digit one-time code in your Gmail inbox. Zero passwords needed.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                NeonTextField(
                                    value = state.email,
                                    onValueChange = viewModel::onEmailChanged,
                                    label = "Gmail Address",
                                    placeholder = "yourname@gmail.com",
                                    leadingIcon = Icons.Default.Email,
                                    errorMessage = state.emailError
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                NeonButton(
                                    text = if (state.cooldownSeconds > 0)
                                        "Wait ${state.cooldownSeconds}s"
                                    else
                                        "Send Login Code",
                                    onClick = {
                                        viewModel.sendVerificationCode(
                                            purpose = "LOGIN",
                                            onSuccess = { onNavigateToVerifyCode("LOGIN") }
                                        )
                                    },
                                    enabled = state.cooldownSeconds == 0 && state.email.isNotBlank(),
                                    isLoading = state.isLoading
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Security & Device Guarantee Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C1929))
                        .border(1.dp, NeonBlue.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Zero passwords requested, managed, or stored",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Credential Manager & RS256 token verification",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Founder status verified exclusively via Google token",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Navigation CTA
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "New to ZeroChat? ",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Create Account",
                        color = NeonBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToRegister() }
                    )
                }
            }
        }
    }
}
