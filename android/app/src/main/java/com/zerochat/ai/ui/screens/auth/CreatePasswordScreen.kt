package com.zerochat.ai.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.theme.*

@Composable
fun CreatePasswordScreen(
    viewModel: AuthViewModel,
    onRegistrationSuccess: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    val hasMinLength = state.password.length >= 8
    val hasUpper = state.password.any { it.isUpperCase() }
    val hasLower = state.password.any { it.isLowerCase() }
    val hasDigit = state.password.any { it.isDigit() }
    val passwordsMatch = state.password.isNotEmpty() && state.password == state.confirmPassword
    val isUsernameValid = state.username.isNotBlank() && state.usernameError == null && state.isUsernameAvailable != false
    val isFormValid = hasMinLength && hasUpper && hasLower && hasDigit && passwordsMatch && isUsernameValid

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
            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Set Account Password",
                color = TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Text(
                text = "Final Step: Create your ZeroChat AI credentials",
                color = NeonBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    NeonTextField(
                        value = state.username,
                        onValueChange = viewModel::onUsernameChanged,
                        label = "Display Username",
                        placeholder = "e.g. Alex",
                        leadingIcon = Icons.Default.Person,
                        errorMessage = state.usernameError
                    )

                    if (state.isUsernameAvailable == true && state.usernameError == null) {
                        Text(
                            text = "✓ @${state.username.trim()} is available",
                            color = StatusSuccess,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NeonTextField(
                        value = state.password,
                        onValueChange = viewModel::onPasswordChanged,
                        label = "Account Password",
                        placeholder = "Choose a strong password",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NeonTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmPasswordChanged,
                        label = "Confirm Password",
                        placeholder = "Re-enter your password",
                        leadingIcon = Icons.Default.Lock,
                        isPassword = true,
                        errorMessage = if (state.confirmPassword.isNotEmpty() && !passwordsMatch) "Passwords do not match" else null
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Password Requirements Checklist
                    Text(
                        text = "Security Requirements (Argon2id Hash):",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    PasswordRequirementRow(label = "At least 8 characters", isMet = hasMinLength)
                    PasswordRequirementRow(label = "At least one uppercase letter (A-Z)", isMet = hasUpper)
                    PasswordRequirementRow(label = "At least one lowercase letter (a-z)", isMet = hasLower)
                    PasswordRequirementRow(label = "At least one numeric digit (0-9)", isMet = hasDigit)
                    PasswordRequirementRow(label = "Passwords match identically", isMet = passwordsMatch)

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

                    Spacer(modifier = Modifier.height(24.dp))

                    NeonButton(
                        text = "Complete Registration",
                        onClick = { viewModel.createPasswordAndRegister(onSuccess = onRegistrationSuccess) },
                        enabled = isFormValid,
                        isLoading = state.isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = NeonBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Zero-plaintext storage & Argon2id encryption enforced",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun PasswordRequirementRow(label: String, isMet: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (isMet) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isMet) StatusSuccess else TextMuted,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            color = if (isMet) TextPrimary else TextMuted,
            fontSize = 12.sp
        )
    }
}

