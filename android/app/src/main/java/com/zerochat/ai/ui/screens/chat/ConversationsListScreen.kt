package com.zerochat.ai.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.ConversationDto
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.screens.auth.AuthViewModel
import com.zerochat.ai.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsListScreen(
    chatViewModel: ChatViewModel,
    authViewModel: AuthViewModel,
    onNavigateToCommunityChat: () -> Unit,
    onNavigateToDirectChat: (Int) -> Unit,
    onNavigateToUserSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToQrCode: () -> Unit,
    onNavigateToScanQr: () -> Unit,
    onNavigateToFileSecurity: () -> Unit,
    onNavigateToAdminDashboard: () -> Unit,
    onLogout: () -> Unit
) {
    val chatState by chatViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var isSearchVisible by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        chatViewModel.loadConversations()
        chatViewModel.startConversationsPolling()
    }

    DisposableEffect(Unit) {
        onDispose {
            chatViewModel.stopConversationsPolling()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CyberDarkSurface,
                drawerContentColor = TextPrimary,
                modifier = Modifier.width(300.dp)
            ) {
                // Drawer Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(CyberDarkCard, CyberDarkSurface)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        // User Avatar
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(NeonBlue, ElectricBlue))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (authState.currentUser?.username?.take(1) ?: "U").uppercase(),
                                color = Color.White,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // User Username + Verified Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = authState.currentUser?.username ?: "User",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (authState.currentUser?.isVerifiedBadge == true || authState.currentUser?.isFounder == true) {
                                BlueVerifiedBadge(
                                    isFounder = authState.currentUser?.isFounder == true,
                                    size = 18.dp
                                )
                            }
                        }

                        // Founder / Role Pill
                        if (authState.currentUser?.isFounder == true) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = FounderBadgeBlue.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FounderBadgeBlue.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "👑 FOUNDER & SUPER ADMIN",
                                    color = NeonBlue,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = authState.currentUser?.email ?: "",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = CyberDarkBorder)

                // Drawer Navigation Items
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = null, tint = NeonBlue) },
                    label = { Text("My Profile", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToProfile()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Forum, contentDescription = null, tint = NeonBlue) },
                    label = { Text("Community Chat", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToCommunityChat()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.GroupAdd, contentDescription = null, tint = NeonBlue) },
                    label = { Text("Search Users & Contacts", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToUserSearch()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = NeonBlue) },
                    label = { Text("My QR Contact Code", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToQrCode()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = NeonBlue) },
                    label = { Text("Scan QR Code", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToScanQr()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Security, contentDescription = null, tint = NeonBlue) },
                    label = { Text("File Security Shield", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        onNavigateToFileSecurity()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )

                // Founder Admin Panel (Only for Super Admins / Founders)
                if (authState.currentUser?.isSuperAdmin == true) {
                    HorizontalDivider(color = CyberDarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = FounderGoldAccent) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Founder Admin Panel", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("⚡", fontSize = 12.sp)
                            }
                        },
                        selected = false,
                        onClick = {
                            scope.launch { drawerState.close() }
                            onNavigateToAdminDashboard()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                HorizontalDivider(color = CyberDarkBorder)

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = StatusError) },
                    label = { Text("Log Out", color = StatusError) },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showLogoutDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ZeroChat",
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(StatusSuccess)
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                                Icon(Icons.Outlined.Search, contentDescription = "Search", tint = TextPrimary)
                            }
                            IconButton(onClick = onNavigateToScanQr) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR", tint = TextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CyberDarkBackground
                        )
                    )

                    AnimatedVisibility(visible = isSearchVisible) {
                        Surface(
                            color = CyberDarkSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CyberDarkBorder)
                        ) {
                            TextField(
                                value = chatState.conversationSearchQuery,
                                onValueChange = { chatViewModel.onConversationSearchQueryChanged(it) },
                                placeholder = { Text("Search conversations...", color = TextMuted, fontSize = 14.sp) },
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                                },
                                trailingIcon = {
                                    if (chatState.conversationSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { chatViewModel.onConversationSearchQueryChanged("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMuted)
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToUserSearch,
                    containerColor = ElectricBlue,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.padding(bottom = 8.dp, end = 8.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "New Chat")
                }
            },
            containerColor = CyberDarkBackground
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                val conversations = chatState.filteredConversations

                if (conversations.isEmpty() && !chatState.isConversationsLoading) {
                    // Empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(CyberDarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Conversations Yet",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap the compose button below or search for registered users to begin secure messaging.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onNavigateToCommunityChat,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Community Chat")
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(conversations, key = { it.conversationId }) { conv ->
                            ConversationRowItem(
                                conversation = conv,
                                onClick = {
                                    if (conv.isGroup) {
                                        onNavigateToCommunityChat()
                                    } else {
                                        conv.recipientUser?.id?.let { pid ->
                                            onNavigateToDirectChat(pid)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(
                                color = CyberDarkBorder.copy(alpha = 0.5f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(start = 76.dp)
                            )
                        }
                    }
                }

                if (chatState.isConversationsLoading && conversations.isEmpty()) {
                    CircularProgressIndicator(
                        color = NeonBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    // Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to log out of ZeroChat?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.logout(onLogout)
                    }
                ) {
                    Text("Log Out", color = StatusError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = TextPrimary)
                }
            },
            containerColor = CyberDarkCard
        )
    }
}

@Composable
fun ConversationRowItem(
    conversation: ConversationDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with Online indicator
        Box(modifier = Modifier.size(52.dp)) {
            if (conversation.isGroup) {
                // Group Avatar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonBlue, ElectricBlue))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Groups,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                // User Avatar
                val initial = (conversation.recipientUser?.username?.take(1) ?: conversation.title.take(1)).uppercase()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (conversation.recipientUser?.isFounder == true)
                                    listOf(FounderBadgeBlue, ElectricBlue)
                                else
                                    listOf(CyberDarkCard, CyberDarkBorder)
                            )
                        )
                        .border(1.dp, CyberDarkBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        color = if (conversation.recipientUser?.isFounder == true) Color.White else TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Genuine Online Status Dot (computed from active WebSocket connections)
                if (conversation.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(StatusSuccess)
                            .border(2.dp, CyberDarkBackground, CircleShape)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Center: Title + Last Message Preview
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (conversation.isGroup) {
                    Text(
                        text = "📌",
                        fontSize = 12.sp
                    )
                }
                Text(
                    text = conversation.title,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (conversation.recipientUser?.isVerifiedBadge == true || conversation.recipientUser?.isFounder == true) {
                    BlueVerifiedBadge(
                        isFounder = conversation.recipientUser?.isFounder == true,
                        size = 15.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = conversation.lastMessage?.ifBlank { "No messages yet" } ?: "No messages yet",
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: Time + Unread Count
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.Center
        ) {
            val formattedTime = formatTimestamp(conversation.lastMessageTime)
            if (formattedTime.isNotEmpty()) {
                Text(
                    text = formattedTime,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            if (conversation.unreadCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(ElectricBlue)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = conversation.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(isoString: String?): String {
    if (isoString.isNullOrBlank()) return ""
    return try {
        // Sample formats: "2026-11-01T15:30:00Z" or "2026-11-01T15:30:00.123456"
        val clean = isoString.replace("Z", "").split(".")[0]
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(clean) ?: return ""
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }

        if (now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR)
        ) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("MM/dd", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        ""
    }
}

