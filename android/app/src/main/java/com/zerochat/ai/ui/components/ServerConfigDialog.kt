
package com.zerochat.ai.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zerochat.ai.data.api.ApiClient
import com.zerochat.ai.ui.theme.*

@Composable
fun ServerConfigDialog(
    onDismiss: () -> Unit,
    onUrlUpdated: (String) -> Unit
) {
    val context = LocalContext.current
    var currentUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var inputUrl by remember { mutableStateOf(ApiClient.getBaseUrl()) }
    var warningMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val isEmulator = remember { ApiClient.isEmulator() }
    val deviceDesc = remember { ApiClient.getDeviceDescription() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(CyberDarkCard)
                .border(1.dp, CyberDarkBorder, RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Server Configuration",
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Device info badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isEmulator) Icons.Default.Computer else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (isEmulator) NeonBlue else ElectricBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deviceDesc,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Quick Presets:",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Presets
                ServerPresetItem(
                    title = "Render Cloud (Live API)",
                    subtitle = "https://zerochatai-1.onrender.com/",
                    icon = Icons.Default.Dns,
                    isSelected = inputUrl.contains("zerochatai-1.onrender.com"),
                    onClick = {
                        inputUrl = "https://zerochatai-1.onrender.com/"
                        warningMessage = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ServerPresetItem(
                    title = "Custom Cloud Domain",
                    subtitle = "https://api.zerochat.ai/",
                    icon = Icons.Default.Dns,
                    isSelected = inputUrl.startsWith("https://api.zerochat.ai"),
                    onClick = {
                        inputUrl = "https://api.zerochat.ai/"
                        warningMessage = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ServerPresetItem(
                    title = "Local PC (Wi-Fi LAN)",
                    subtitle = "http://192.168.1.68:8000/",
                    icon = Icons.Default.Wifi,
                    isSelected = inputUrl.contains("192.168.1.68"),
                    onClick = {
                        inputUrl = "http://192.168.1.68:8000/"
                        warningMessage = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ServerPresetItem(
                    title = "USB Reverse Tunnel (adb)",
                    subtitle = "http://127.0.0.1:8000/",
                    icon = Icons.Default.Usb,
                    isSelected = inputUrl.contains("127.0.0.1"),
                    onClick = {
                        inputUrl = "http://127.0.0.1:8000/"
                        warningMessage = null
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                ServerPresetItem(
                    title = "Android Emulator",
                    subtitle = "http://10.0.2.2:8000/ (Emulator Only)",
                    icon = Icons.Default.Computer,
                    isSelected = inputUrl.contains("10.0.2.2"),
                    onClick = {
                        inputUrl = "http://10.0.2.2:8000/"
                        if (!isEmulator) {
                            warningMessage = "Warning: 10.0.2.2 only works inside an Android Emulator. Physical phones cannot connect to 10.0.2.2."
                        } else {
                            warningMessage = null
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // URL Text Field
                NeonTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        if (!isEmulator && it.contains("10.0.2.2")) {
                            warningMessage = "Physical phone detected: 10.0.2.2 is unreachable. Use Wi-Fi LAN IP or USB reverse."
                        } else {
                            warningMessage = null
                        }
                    },
                    label = "Backend Base URL",
                    placeholder = "https://api.zerochat.ai/ or http://192.168.1.x:8000/",
                    leadingIcon = Icons.Default.Dns,
                    errorMessage = warningMessage
                )

                if (successMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = successMessage!!,
                        color = StatusSuccess,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NeonButton(
                        text = "Reset",
                        onClick = {
                            val defaultUrl = ApiClient.resetToDefault(context)
                            inputUrl = defaultUrl
                            currentUrl = defaultUrl
                            warningMessage = null
                            successMessage = "Reset to default: $defaultUrl"
                            onUrlUpdated(defaultUrl)
                        },
                        modifier = Modifier.weight(0.4f),
                        isSecondary = true,
                        icon = Icons.Default.Refresh
                    )

                    NeonButton(
                        text = "Save & Apply",
                        onClick = {
                            if (!isEmulator && inputUrl.contains("10.0.2.2")) {
                                warningMessage = "Cannot use 10.0.2.2 on physical device. Please enter your PC IP (e.g. 192.168.1.68) or 127.0.0.1."
                                return@NeonButton
                            }
                            ApiClient.setBaseUrl(inputUrl, context)
                            currentUrl = ApiClient.getBaseUrl()
                            successMessage = "Backend URL set to $currentUrl"
                            onUrlUpdated(currentUrl)
                            onDismiss()
                        },
                        modifier = Modifier.weight(0.6f),
                        icon = Icons.Default.Check
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Tip: To connect via Wi-Fi, run 'uvicorn app.main:app --host 0.0.0.0 --port 8000' and ensure phone & PC are on the same network. To connect via USB, run 'adb reverse tcp:8000 tcp:8000' and use http://127.0.0.1:8000/.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
private fun ServerPresetItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) NeonBlue else CyberDarkBorder
    val bgColor = if (isSelected) Color(0xFF0F2338) else Color(0xFF0F172A)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) NeonBlue else TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isSelected) NeonBlue else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = NeonBlue,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

