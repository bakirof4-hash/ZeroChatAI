package com.zerochat.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.ChatMessageDto
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFeedScreen(
    currentUser: UserDto?,
    viewModel: ChatViewModel,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    onNavigateToFileSecurity: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ZeroChat AI",
                            color = NeonBlue,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF002244))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "LIVE",
                                color = NeonBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    // Search users icon
                    IconButton(onClick = onNavigateToSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Users",
                            tint = TextPrimary
                        )
                    }

                    // QR Code Scanner icon
                    IconButton(onClick = onNavigateToScanQr) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = NeonBlue
                        )
                    }

                    // Offline File Security Analyzer icon
                    IconButton(onClick = onNavigateToFileSecurity) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "File Security Inspector",
                            tint = TextPrimary
                        )
                    }

                    // Founder Super Admin Dashboard Shortcut
                    if (currentUser?.isSuperAdmin == true) {
                        IconButton(onClick = onNavigateToAdminDashboard) {
                            Icon(
                                imageVector = Icons.Default.AdminPanelSettings,
                                contentDescription = "Super Admin Dashboard",
                                tint = NeonBlue
                            )
                        }
                    }

                    // Profile icon
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "My Profile",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberDarkSurface
                )
            )
        },
        containerColor = CyberDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages, key = { it.id }) { msg ->
                    ChatMessageItem(message = msg, currentUserId = currentUser?.id)
                }
            }

            // Message Composer
            Surface(
                color = CyberDarkSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    NeonTextField(
                        value = state.inputMessage,
                        onValueChange = viewModel::onInputMessageChanged,
                        label = "",
                        placeholder = "Type secure message...",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = viewModel::sendMessage,
                        enabled = state.inputMessage.isNotBlank() && !state.isSending,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (state.inputMessage.isNotBlank())
                                    Brush.horizontalGradient(listOf(ElectricBlue, NeonBlue))
                                else
                                    Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (state.inputMessage.isNotBlank()) CyberDarkBackground else TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatMessageDto, currentUserId: Int?) {
    val isMe = message.senderId == currentUserId

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        if (!isMe) {
            // Profile Avatar with Neon Ring for Founder
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (message.isFounder)
                            Brush.sweepGradient(listOf(NeonBlue, ElectricBlue, Color(0xFF00E5FF)))
                        else if (message.isAi)
                            Brush.sweepGradient(listOf(Color(0xFFA855F7), Color(0xFF6366F1)))
                        else
                            Brush.sweepGradient(listOf(Color(0xFF334155), Color(0xFF475569)))
                    )
                    .padding(2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(CyberDarkBackground)
                ) {
                    Text(
                        text = message.senderUsername.take(1).uppercase(),
                        color = if (message.isFounder) NeonBlue else TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        // Message bubble
        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            // Header: Username + Founder Blue Checkmark Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = if (isMe) "You" else message.senderUsername,
                    color = if (message.isFounder) NeonBlue else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (message.isFounder) FontWeight.Bold else FontWeight.Medium
                )

                // Consistent Blue Verified Checkmark Badge
                if (message.isFounder || message.isVerifiedBadge) {
                    BlueVerifiedBadge(
                        isFounder = message.isFounder,
                        size = 14.dp,
                        showFounderLabel = message.isFounder
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val bubbleShape = if (isMe) {
                RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
            } else {
                RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
            }

            val bubbleBackground = when {
                isMe -> Brush.linearGradient(listOf(ElectricBlue, Color(0xFF0052CC)))
                message.isFounder -> Brush.linearGradient(listOf(Color(0xFF0E1A30), Color(0xFF132342)))
                message.isAi -> Brush.linearGradient(listOf(Color(0xFF1C142E), Color(0xFF221538)))
                else -> Brush.linearGradient(listOf(CyberDarkCard, CyberDarkCard))
            }

            val bubbleBorder = when {
                message.isFounder -> NeonBlue.copy(alpha = 0.5f)
                message.isAi -> Color(0xFFA855F7).copy(alpha = 0.4f)
                else -> CyberDarkBorder
            }

            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBackground)
                    .border(1.dp, bubbleBorder, bubbleShape)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = message.content,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

