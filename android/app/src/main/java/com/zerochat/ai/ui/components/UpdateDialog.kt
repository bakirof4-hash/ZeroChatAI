package com.zerochat.ai.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zerochat.ai.ui.screens.update.UpdateState
import com.zerochat.ai.ui.theme.*
import com.zerochat.ai.util.UpdateManager

@Composable
fun UpdateDialog(
    state: UpdateState,
    context: Context,
    onUpdateClick: () -> Unit,
    onDismissClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    if (state is UpdateState.Idle || state is UpdateState.Dismissed) {
        return
    }

    val isMandatory = when (state) {
        is UpdateState.UpdateAvailable -> state.isMandatory
        else -> false
    }

    Dialog(
        onDismissRequest = {
            if (!isMandatory && state !is UpdateState.Downloading && state !is UpdateState.Verifying) {
                onDismissClick()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !isMandatory && state !is UpdateState.Downloading && state !is UpdateState.Verifying,
            dismissOnClickOutside = false
        )
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            cornerRadius = 20.dp,
            borderColor = NeonBlue.copy(alpha = 0.6f),
            backgroundColor = CyberDarkCard
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(NeonBlue.copy(alpha = 0.15f))
                        .border(1.dp, NeonBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = "Update",
                        tint = NeonBlue,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isMandatory) "Mandatory Security Update" else "New Version Available",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                when (state) {
                    is UpdateState.UpdateAvailable -> {
                        // Version tags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(CyberDarkSurface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current: v${state.currentVersionName}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                            Text(
                                text = "->",
                                fontSize = 14.sp,
                                color = NeonBlue,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Latest: v${state.latestVersionName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = StatusSuccess
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Release Notes",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CyberDarkSurface.copy(alpha = 0.6f))
                                .border(1.dp, CyberDarkBorder, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = state.releaseNotes,
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security verified",
                                tint = StatusSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SHA-256 Checksum & Signature Verified",
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!state.isMandatory) {
                                NeonButton(
                                    text = "Later",
                                    onClick = onDismissClick,
                                    isSecondary = true,
                                    modifier = Modifier.weight(1f),
                                    height = 46.dp
                                )
                            }
                            NeonButton(
                                text = "Update Now",
                                onClick = onUpdateClick,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                        }
                    }

                    is UpdateState.Downloading -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Downloading Package... ${state.progressPercent}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeonBlue
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { state.progressPercent / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeonBlue,
                            trackColor = CyberDarkSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Secure HTTPS streaming in progress",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    is UpdateState.Verifying -> {
                        Spacer(modifier = Modifier.height(10.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = StatusSuccess,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Verifying cryptographic integrity...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Comparing SHA-256 & signing certificate",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }

                    is UpdateState.ReadyToInstall -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Ready",
                            tint = StatusSuccess,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Package Verified & Ready",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap Install to open the official Android Package Installer.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        NeonButton(
                            text = "Install Update",
                            onClick = {
                                UpdateManager.startInstallIntent(context, state.apkFile)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            height = 46.dp
                        )
                    }

                    is UpdateState.Error -> {
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = StatusError,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Update Verification Failed",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = StatusError
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.message,
                            fontSize = 12.sp,
                            color = TextSecondary,
                            lineHeight = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            NeonButton(
                                text = "Close",
                                onClick = onDismissClick,
                                isSecondary = true,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                            NeonButton(
                                text = "Retry",
                                onClick = onRetryClick,
                                modifier = Modifier.weight(1f),
                                height = 46.dp
                            )
                        }
                    }

                    else -> {}
                }
            }
        }
    }
}

