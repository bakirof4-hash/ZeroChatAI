package com.zerochat.ai.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.screens.chat.ChatViewModel
import com.zerochat.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanQrScreen(
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onStartDirectChat: (UserDto) -> Unit
) {
    val state by chatViewModel.uiState.collectAsState()
    var qrInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan & Connect",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDarkSurface)
            )
        },
        containerColor = CyberDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Scanner simulation / Deep link input card
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Enter QR Code Payload or Username",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Supports 'zerochat://user?u=username' or '@username'",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                    )

                    NeonTextField(
                        value = qrInput,
                        onValueChange = { qrInput = it },
                        label = "QR Data / Username",
                        placeholder = "zerochat://user?u=ZeroFounder",
                        leadingIcon = Icons.Default.QrCode
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    NeonButton(
                        text = if (state.isScanningQr) "Resolving User..." else "Resolve & Lookup",
                        onClick = { chatViewModel.scanQrCode(qrInput) },
                        enabled = qrInput.isNotBlank() && !state.isScanningQr,
                        icon = Icons.Default.Search,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Error banner
            if (state.errorMessage != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = StatusError
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            color = StatusError,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Scanned / Resolved User Card
            val resolvedUser = state.scannedUser
            if (resolvedUser != null) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = if (resolvedUser.isFounder) NeonBlue else CyberDarkBorder
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyberDarkSurface)
                                .border(
                                    2.dp,
                                    if (resolvedUser.isFounder) NeonBlue else CyberDarkBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = resolvedUser.username.take(2).uppercase(),
                                color = NeonBlue,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "@${resolvedUser.username}",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (resolvedUser.isVerifiedBadge || resolvedUser.isFounder) {
                                Spacer(modifier = Modifier.width(6.dp))
                                BlueVerifiedBadge(isFounder = resolvedUser.isFounder, size = 18.dp)
                            }
                        }

                        if (resolvedUser.isFounder) {
                            Text(
                                text = "FOUNDER & SUPER ADMIN",
                                color = NeonBlue,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Action Buttons
                        NeonButton(
                            text = "Start Direct Chat",
                            onClick = {
                                chatViewModel.setDirectTarget(resolvedUser)
                                onStartDirectChat(resolvedUser)
                            },
                            icon = Icons.Default.Chat,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Block / Unblock Button
                        OutlinedButton(
                            onClick = {
                                if (state.isScannedUserBlocked) {
                                    chatViewModel.unblockUser(resolvedUser.id)
                                } else {
                                    chatViewModel.blockUser(resolvedUser.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (state.isScannedUserBlocked) StatusSuccess else StatusError
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (state.isScannedUserBlocked) StatusSuccess else StatusError
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (state.isScannedUserBlocked) Icons.Default.CheckCircle else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isScannedUserBlocked) "Unblock User" else "Block User",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
