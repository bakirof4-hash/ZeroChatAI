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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.zerochat.ai.ui.theme.*
import com.zerochat.ai.util.GoogleAuthManager
import kotlinx.coroutines.launch

@Composable
fun RegisterEmailScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onRegisterSuccess: () -> Unit = {},
    onCodeSent: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showEmailOtpSection by remember { mutableStateOf(false) }

    // Google Play Services status
    val isPlayServicesAvailable = remember { GoogleAuthManager.isGooglePlayServicesAvailable(context) }

    val legacyGoogleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK || result.data != null) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (!idToken.isNullOrBlank()) {
                    viewModel.signInWithGoogle(idToken, onSuccess = onRegisterSuccess)
                } else {
                    viewModel.setErrorMessage(
                        "Google ID token qaytarmadi. Iltimos, Google Cloud / Firebase'da Web Client ID sozlamasini tekshiring."
                    )
                }
            } catch (e: ApiException) {
                if (e.statusCode != 12501) {
                    val detailedMsg = GoogleAuthManager.getErrorMessageForStatusCode(e.statusCode, e.message)
                    viewModel.setErrorMessage(detailedMsg)
                }
            } catch (e: Exception) {
                viewModel.setErrorMessage("Sign-In error: ${e.localizedMessage}")
            }
        }
    }

    val startGoogleSignUp: () -> Unit = {
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
                        viewModel.signInWithGoogle(idToken, onSuccess = onRegisterSuccess)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CyberDarkSurface)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactScreen) 16.dp else 24.dp))

                Text(
                    text = "Join ZeroChat AI",
                    color = TextPrimary,
                    fontSize = if (isCompactScreen) 24.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Text(
                    text = "Instant 1-Tap Google Account Creation",
                    color = NeonBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(if (isCompactScreen) 16.dp else 24.dp))

                // Google Registration Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Sign Up with Google",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Fast, passwordless registration. Zero passwords stored.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        GoogleSignInButton(
                            text = "Continue with Google",
                            isLoading = state.isLoading,
                            onClick = startGoogleSignUp
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Device without Google Play Services Alternative Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showEmailOtpSection = !showEmailOtpSection }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = if (showEmailOtpSection) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (!isPlayServicesAvailable)
                                    "Google Play Services unavailable? Sign up with code"
                                else
                                    "Alternative: Register with 6-digit email code",
                                color = NeonBlue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Alternative Zero-Password Email OTP Flow
                        AnimatedVisibility(visible = showEmailOtpSection || !isPlayServicesAvailable) {
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
                                    text = "Passwordless 6-Digit Email Code",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "We will send a one-time 6-digit verification code to confirm ownership. No password required.",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                NeonTextField(
                                    value = state.email,
                                    onValueChange = viewModel::onEmailChanged,
                                    label = "Email Address",
                                    placeholder = "youremail@gmail.com",
                                    leadingIcon = Icons.Default.Email,
                                    errorMessage = state.emailError
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                NeonButton(
                                    text = if (state.cooldownSeconds > 0)
                                        "Wait ${state.cooldownSeconds}s"
                                    else
                                        "Send Verification Code",
                                    onClick = {
                                        viewModel.sendVerificationCode(
                                            purpose = "REGISTRATION",
                                            onSuccess = onCodeSent
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

                // Security & Privacy Guarantee
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0C1929))
                        .border(1.dp, NeonBlue.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Security Guarantee",
                            tint = NeonBlue,
                            modifier = Modifier
                                .size(22.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Zero Password Policy",
                                color = NeonBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "ZeroChat AI never asks for, manages, or stores passwords. Authentication is cryptographically verified directly through Google OAuth 2.0 OpenID Connect.",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Text(
                        text = "Already have an account? ",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Sign In",
                        color = NeonBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateBack() }
                    )
                }
            }
        }
    }
}
