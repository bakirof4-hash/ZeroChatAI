package com.zerochat.ai.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.components.CodeInputField
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.theme.*

@Composable
fun VerifyCodeScreen(
    viewModel: AuthViewModel,
    purpose: String = "REGISTRATION",
    onNavigateBack: () -> Unit,
    onCodeVerified: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val minutes = state.expiresInSeconds / 60
    val seconds = state.expiresInSeconds % 60
    val expiryFormatted = String.format("%02d:%02d", minutes, seconds)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberDarkBackground)
            .padding(24.dp)
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Verify Code",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Enter the 6-digit code sent to ${state.email}",
                color = TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Expiry & Countdown Timer Card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Timer",
                            tint = if (state.expiresInSeconds < 60) StatusError else NeonBlue,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Code expires in: ",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            text = expiryFormatted,
                            color = if (state.expiresInSeconds < 60) StatusError else NeonBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    CodeInputField(
                        code = state.code,
                        onCodeChange = viewModel::onCodeChanged,
                        isError = state.codeError != null || state.errorMessage != null,
                        onComplete = {
                            viewModel.verifyCode(purpose = purpose, onSuccess = onCodeVerified)
                        }
                    )

                    if (state.codeError != null) {
                        Text(
                            text = state.codeError!!,
                            color = StatusError,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (state.errorMessage != null) {
                        Text(
                            text = state.errorMessage!!,
                            color = StatusError,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    NeonButton(
                        text = "Verify Code",
                        onClick = { viewModel.verifyCode(purpose = purpose, onSuccess = onCodeVerified) },
                        enabled = state.code.length == 6,
                        isLoading = state.isLoading
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Resend Code with cooldown
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.cooldownSeconds == 0) {
                                viewModel.sendVerificationCode(purpose = purpose)
                            }
                            .padding(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Resend",
                            tint = if (state.cooldownSeconds == 0) NeonBlue else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.cooldownSeconds > 0)
                                "Resend code in ${state.cooldownSeconds}s"
                            else
                                "Resend verification code",
                            color = if (state.cooldownSeconds == 0) NeonBlue else TextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Dev Mode Code Hint (for local development when SMTP is not configured)
            if (state.devCode != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, NeonBlue.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dev Notice: Test code is ${state.devCode} (SMTP offline)",
                            color = NeonBlue,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Protected by automated rate limiting & max 5 attempt lockout",
                color = TextMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )
        }
    }
}

