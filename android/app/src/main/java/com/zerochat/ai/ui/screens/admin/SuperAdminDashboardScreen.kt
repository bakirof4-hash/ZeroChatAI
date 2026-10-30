package com.zerochat.ai.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.data.model.AuditLogDto
import com.zerochat.ai.data.model.SecurityEventDto
import com.zerochat.ai.data.model.UserDto
import com.zerochat.ai.ui.components.BlueVerifiedBadge
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.components.NeonTextField
import com.zerochat.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuperAdminDashboardScreen(
    viewModel: AdminViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val tabs = listOf("Overview", "Users", "Security", "Audit Logs", "SMTP")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Super Admin Dashboard",
                            color = NeonBlue,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BlueVerifiedBadge(isFounder = true, size = 16.dp)
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CyberDarkSurface)
            )
        },
        containerColor = CyberDarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = CyberDarkSurface,
                contentColor = NeonBlue,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        text = {
                            Text(
                                text = title,
                                color = if (state.selectedTab == index) NeonBlue else TextSecondary,
                                fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            if (state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusError.copy(alpha = 0.2f))
                        .padding(12.dp)
                ) {
                    Text(text = state.errorMessage!!, color = StatusError, fontSize = 13.sp)
                }
            }

            if (state.successMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(StatusSuccess.copy(alpha = 0.2f))
                        .padding(12.dp)
                ) {
                    Text(text = state.successMessage!!, color = StatusSuccess, fontSize = 13.sp)
                }
            }

            Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = NeonBlue,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    when (state.selectedTab) {
                        0 -> AdminOverviewTab(state = state)
                        1 -> AdminUsersTab(state = state, viewModel = viewModel)
                        2 -> AdminSecurityTab(events = state.securityEvents)
                        3 -> AdminAuditLogsTab(logs = state.auditLogs)
                        4 -> AdminSmtpTab(state = state)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminOverviewTab(state: AdminUiState) {
    val stats = state.stats
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Platform Telemetry & Metrics",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Total Users",
                value = stats?.totalUsers?.toString() ?: "0",
                icon = Icons.Default.People,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Verified Badges",
                value = stats?.verifiedUsers?.toString() ?: "0",
                icon = Icons.Default.Verified,
                accentColor = NeonBlue,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Active Sessions",
                value = stats?.activeSessions?.toString() ?: "0",
                icon = Icons.Default.VpnKey,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Security Events",
                value = stats?.totalSecurityEvents?.toString() ?: "0",
                icon = Icons.Default.Security,
                accentColor = StatusWarning,
                modifier = Modifier.weight(1f)
            )
        }

        // Live SMTP Status Card
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Email,
                        contentDescription = null,
                        tint = if (stats?.smtpEnabled == true) StatusSuccess else StatusWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gmail SMTP Service Status",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (stats?.smtpEnabled == true)
                        "Configured and active via ${stats.smtpHost}:587 (TLS)."
                    else
                        "Inactive / Dev Mode. To enable live Gmail delivery, add your Google App Password to backend .env.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = ElectricBlue
) {
    GlassCard(modifier = modifier) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, color = TextSecondary, fontSize = 12.sp)
                Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
fun AdminUsersTab(state: AdminUiState, viewModel: AdminViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        NeonTextField(
            value = state.searchQuery,
            onValueChange = viewModel::onSearchQueryChanged,
            label = "Filter Users",
            placeholder = "Search username or email...",
            leadingIcon = Icons.Default.Search
        )

        Spacer(modifier = Modifier.height(14.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(state.users, key = { it.id }) { user ->
                AdminUserRow(
                    user = user,
                    onToggleBadge = { viewModel.toggleUserBadge(user.id, user.isVerifiedBadge) },
                    onToggleStatus = { viewModel.toggleUserStatus(user.id, user.isActive) }
                )
            }
        }
    }
}

@Composable
fun AdminUserRow(
    user: UserDto,
    onToggleBadge: () -> Unit,
    onToggleStatus: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.username,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    if (user.isFounder || user.isVerifiedBadge) {
                        BlueVerifiedBadge(isFounder = user.isFounder, size = 15.dp, showFounderLabel = user.isFounder)
                    }
                }

                // Active / Inactive pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (user.isActive) StatusSuccess.copy(alpha = 0.2f) else StatusError.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (user.isActive) "ACTIVE" else "BANNED",
                        color = if (user.isActive) StatusSuccess else StatusError,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = user.email,
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            if (!user.isFounder) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NeonButton(
                        text = if (user.isVerifiedBadge) "Revoke Badge" else "Grant Blue Badge",
                        onClick = onToggleBadge,
                        isSecondary = user.isVerifiedBadge,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )

                    NeonButton(
                        text = if (user.isActive) "Ban User" else "Unban User",
                        onClick = onToggleStatus,
                        isSecondary = user.isActive,
                        height = 36.dp,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Text(
                    text = "Founder Account (Protected & Immutable)",
                    color = NeonBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun AdminSecurityTab(events: List<SecurityEventDto>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(events, key = { it.id }) { event ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = event.eventType,
                            color = when (event.severity) {
                                "CRITICAL" -> StatusError
                                "SUSPICIOUS" -> StatusWarning
                                else -> TextPrimary
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = event.severity,
                            color = when (event.severity) {
                                "CRITICAL" -> StatusError
                                "SUSPICIOUS" -> StatusWarning
                                else -> NeonBlue
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = event.details ?: "No additional telemetry",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Target: ${event.email ?: "N/A"} | IP: ${event.ipAddress ?: "Local"}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogsTab(logs: List<AuditLogDto>) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(logs, key = { it.id }) { log ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = log.action,
                            color = NeonBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = log.actorEmail,
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = log.details ?: "",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Time: ${log.timestamp} | Target: ${log.targetEmail ?: "N/A"}",
                        color = TextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AdminSmtpTab(state: AdminUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Gmail SMTP & Security Configuration",
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Gmail App Password Setup Guide",
                    color = NeonBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "1. Enable 2-Step Verification on your Google Account.\n" +
                            "2. Navigate to https://myaccount.google.com/apppasswords\n" +
                            "3. Generate an App Password named 'ZeroChat AI'.\n" +
                            "4. Copy the 16-character code into backend/.env:\n" +
                            "   SMTP_USER=youremail@gmail.com\n" +
                            "   SMTP_PASSWORD=your16charcode\n" +
                            "   SMTP_ENABLED=True\n\n" +
                            "ZeroChat AI never stores or transmits regular account passwords.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

