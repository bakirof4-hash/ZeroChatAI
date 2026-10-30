package com.zerochat.ai.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.components.CodeInputField
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.theme.*

@Composable
fun ForgotPasswordScreen(
    viewModel: AuthViewModel,
    onNavigateBack: () -> Unit,
    onResetComplete: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var step by remember { mutableStateOf(1) } // 1: Email, 2: Code, 3: New Password

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
                onClick = {
                    if (step > 1) step-- else onNavigateBack()
                },
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
                text = "Reset Password",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = when (step) {
                    1 -> "Step 1: Enter your registered email address"
                    2 -> "Step 2: Enter the 6-digit verification code"
                    else -> "Step 3: Create a new account password"
                },
                color = NeonBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    when (step) {
                        1 -> {
                            NeonTextField(
                                value = state.email,
                                onValueChange = viewModel::onEmailChanged,
                                label = "Registered Email Address",
                                placeholder = "name@gmail.com",
                                leadingIcon = Icons.Default.Email,
                                errorMessage = state.emailError
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            NeonButton(
                                text = if (state.cooldownSeconds > 0) "Wait ${state.cooldownSeconds}s" else "Send Reset Code",
                                onClick = {
                                    viewModel.sendVerificationCode(
                                        purpose = "PASSWORD_RESET",
                                        onSuccess = { step = 2 }
                                    )
                                },
                                enabled = state.cooldownSeconds == 0,
                                isLoading = state.isLoading
                            )
                        }
                        2 -> {
                            Text(
                                text = "Enter 6-Digit Code",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(14.dp))

                            CodeInputField(
                                code = state.code,
                                onCodeChange = viewModel::onCodeChanged,
                                isError = state.codeError != null || state.errorMessage != null
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            NeonButton(
                                text = "Verify Reset Code",
                                onClick = {
                                    viewModel.verifyCode(
                                        purpose = "PASSWORD_RESET",
                                        onSuccess = { step = 3 }
                                    )
                                },
                                enabled = state.code.length == 6,
                                isLoading = state.isLoading
                            )
                        }
                        3 -> {
                            NeonTextField(
                                value = state.password,
                                onValueChange = viewModel::onPasswordChanged,
                                label = "New Password",
                                placeholder = "At least 8 characters",
                                leadingIcon = Icons.Default.Lock,
                                isPassword = true
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            NeonTextField(
                                value = state.confirmPassword,
                                onValueChange = viewModel::onConfirmPasswordChanged,
                                label = "Confirm New Password",
                                placeholder = "Re-enter new password",
                                leadingIcon = Icons.Default.Lock,
                                isPassword = true
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            NeonButton(
                                text = "Update Password",
                                onClick = {
                                    viewModel.confirmPasswordReset(onSuccess = onResetComplete)
                                },
                                enabled = state.password.length >= 8 && state.password == state.confirmPassword,
                                isLoading = state.isLoading
                            )
                        }
                    }

                    if (state.errorMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.errorMessage!!,
                            color = StatusError,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state.successMessage != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = state.successMessage!!,
                            color = StatusSuccess,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

