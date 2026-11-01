package com.zerochat.ai.ui.screens.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.ChatMessageDto
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.theme.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current

    // Check if partner is active / online in conversation list
    val isPartnerOnline = state.conversations.firstOrNull { it.recipientUser?.id == targetUser.id }?.isOnline ?: false

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleFileUpload(it, context, chatViewModel)
        }
    }

    LaunchedEffect(targetUser.id) {
        chatViewModel.loadDirectMessages(targetUser.id)
        chatViewModel.startDirectMessagesPolling(targetUser.id, context)
    }

    DisposableEffect(targetUser.id) {
        onDispose {
            chatViewModel.stopDirectMessagesPolling()
            chatViewModel.clearAttachment()
        }
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
                        Box(modifier = Modifier.size(38.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            if (targetUser.isFounder) listOf(FounderBadgeBlue, ElectricBlue)
                                            else listOf(CyberDarkCard, CyberDarkBorder)
                                        )
                                    )
                                    .border(
                                        1.dp,
                                        if (targetUser.isFounder) NeonBlue else CyberDarkBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = targetUser.username.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (isPartnerOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(StatusSuccess)
                                        .border(1.5.dp, CyberDarkSurface, CircleShape)
                                )
                            }
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
                                    BlueVerifiedBadge(isFounder = targetUser.isFounder, size = 15.dp)
                                }
                            }

                            Text(
                                text = if (isPartnerOnline) "online" else "last seen recently",
                                color = if (isPartnerOnline) StatusSuccess else TextMuted,
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
                Column {
                    // Selected Attachment preview chip
                    state.selectedAttachment?.let { att ->
                        Surface(
                            color = CyberDarkCard,
                            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberDarkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = NeonBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = att.attachmentName,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${att.attachmentSize / 1024} KB",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp)
                                )
                                IconButton(
                                    onClick = { chatViewModel.clearAttachment() },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = TextMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Upload Progress Indicator
                    if (state.isUploadingAttachment) {
                        LinearProgressIndicator(
                            color = NeonBlue,
                            trackColor = CyberDarkBorder,
                            modifier = Modifier.fillMaxWidth().height(2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = "Attach File",
                                tint = NeonBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(CyberInputBackground)
                                .border(1.dp, CyberDarkBorder, RoundedCornerShape(20.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            if (state.inputMessage.isEmpty() && state.selectedAttachment == null) {
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

                        val canSend = (state.inputMessage.isNotBlank() || state.selectedAttachment != null) && !state.isSending
                        IconButton(
                            onClick = { chatViewModel.sendDirectMessage(targetUser.id) },
                            enabled = canSend,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (canSend) ElectricBlue else CyberDarkBorder)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (canSend) Color.White else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = StatusError,
                            modifier = Modifier.size(16.dp)
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
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(CyberDarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Direct Channel with @${targetUser.username}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "End-to-end encrypted direct communication. Say hello to start the conversation!",
                            color = TextMuted,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                    items(state.directMessages, key = { it.id }) { msg ->
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
                    if (isSentByMe) NeonBlue.copy(alpha = 0.22f) else CyberDarkSurface
                )
                .border(
                    1.dp,
                    if (isSentByMe) NeonBlue.copy(alpha = 0.45f) else CyberDarkBorder,
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (isSentByMe) 14.dp else 2.dp,
                        bottomEnd = if (isSentByMe) 2.dp else 14.dp
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                // If attachment is present
                if (!message.attachmentUrl.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberDarkBackground.copy(alpha = 0.5f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.InsertDriveFile,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = message.attachmentName ?: "Attachment",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (message.attachmentSize != null && message.attachmentSize > 0) {
                                Text(
                                    text = "${message.attachmentSize / 1024} KB",
                                    color = TextMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Time and delivery ticks (✓✓ for sent)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val timeStr = formatDirectTime(message.timestamp)
                    if (timeStr.isNotEmpty()) {
                        Text(
                            text = timeStr,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    if (isSentByMe) {
                        Text(
                            text = "✓✓",
                            color = NeonBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun handleFileUpload(uri: Uri, context: Context, viewModel: ChatViewModel) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return
        val bytes = inputStream.readBytes()
        inputStream.close()

        var fileName = "attachment_${System.currentTimeMillis()}"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    fileName = it.getString(idx) ?: fileName
                }
            }
        }

        val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
        val reqBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", fileName, reqBody)
        viewModel.uploadAttachment(part)
    } catch (_: Exception) {
    }
}

private fun formatDirectTime(isoString: String): String {
    return try {
        val clean = isoString.replace("Z", "").split(".")[0]
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(clean) ?: return ""
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}
