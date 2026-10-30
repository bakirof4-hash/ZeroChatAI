package com.zerochat.ai.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.ChatMessageDto
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    targetUser: UserDto,
    currentUser: UserDto?,
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToFileSecurity: () -> Unit
) {
    val state by chatViewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(targetUser.id) {
        chatViewModel.loadDirectMessages(targetUser.id)
    }

    LaunchedEffect(state.directMessages.size) {
        if (state.directMessages.isNotEmpty()) {
            listState.animateScrollToItem(state.directMessages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CyberDarkBackground)
                                .border(
                                    1.dp,
                                    if (targetUser.isFounder) NeonBlue else CyberDarkBorder,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = targetUser.username.take(2).uppercase(),
                                color = NeonBlue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = targetUser.username,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (targetUser.isVerifiedBadge || targetUser.isFounder) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    BlueVerifiedBadge(isFounder = targetUser.isFounder, size = 16.dp)
                                }
                            }

                            Text(
                                text = if (targetUser.isFounder) "Founder • Direct Channel" else "Encrypted Direct Channel",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
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
                actions = {
                    IconButton(onClick = onNavigateToFileSecurity) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Inspector",
                            tint = NeonBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDarkSurface)
            )
        },
        bottomBar = {
            Surface(
                color = CyberDarkSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateToFileSecurity,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach & Scan File",
                            tint = NeonBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(CyberInputBackground)
                            .border(1.dp, CyberDarkBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (state.inputMessage.isEmpty()) {
                            Text(
                                text = "Message @${targetUser.username}...",
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = state.inputMessage,
                            onValueChange = chatViewModel::onInputMessageChanged,
                            textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                            cursorBrush = SolidColor(NeonBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { chatViewModel.sendDirectMessage(targetUser.id) },
                        enabled = state.inputMessage.isNotBlank() && !state.isSending,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (state.inputMessage.isNotBlank()) NeonBlue else CyberDarkBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (state.inputMessage.isNotBlank()) CyberDarkBackground else TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        containerColor = CyberDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Error banner
            if (state.errorMessage != null) {
                Surface(
                    color = StatusError.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = state.errorMessage ?: "",
                            color = StatusError,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (state.isLoading && state.directMessages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonBlue)
                }
            } else if (state.directMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Direct Channel with @${targetUser.username}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Messages are private and persisted on server.",
                            color = TextMuted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.directMessages) { msg ->
                        DirectMessageBubble(
                            message = msg,
                            isSentByMe = (msg.senderId == currentUser?.id)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DirectMessageBubble(
    message: ChatMessageDto,
    isSentByMe: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Text(
                text = if (isSentByMe) "You" else message.senderUsername,
                color = if (isSentByMe) NeonBlue else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (message.isVerifiedBadge || message.isFounder) {
                Spacer(modifier = Modifier.width(4.dp))
                BlueVerifiedBadge(isFounder = message.isFounder, size = 12.dp)
            }
        }

        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isSentByMe) 14.dp else 2.dp,
                        bottomEnd = if (isSentByMe) 2.dp else 14.dp
                    )
                )
                .background(
                    if (isSentByMe) NeonBlue.copy(alpha = 0.25f) else CyberDarkSurface
                )
                .border(
                    1.dp,
                    if (isSentByMe) NeonBlue.copy(alpha = 0.5f) else CyberDarkBorder,
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isSentByMe) 14.dp else 2.dp,
                        bottomEnd = if (isSentByMe) 2.dp else 14.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content,
                color = TextPrimary,
                fontSize = 14.sp
            )
        }
    }
}
