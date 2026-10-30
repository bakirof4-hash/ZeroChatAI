package com.zerochat.ai.ui.screens.security

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.components.GlassCard
import com.zerochat.ai.ui.components.NeonButton
import com.zerochat.ai.ui.screens.chat.ChatViewModel
import com.zerochat.ai.ui.theme.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileSecurityScreen(
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val state by chatViewModel.uiState.collectAsState()

    fun runSampleApkTest() {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)
        zos.putNextEntry(ZipEntry("AndroidManifest.xml"))
        val manifest = (
            "android.permission.RECEIVE_BOOT_COMPLETED\n" +
            "android.permission.SYSTEM_ALERT_WINDOW\n" +
            "android.permission.SEND_SMS\n" +
            "android.permission.REQUEST_INSTALL_PACKAGES"
        ).toByteArray()
        zos.write(manifest)
        zos.closeEntry()

        zos.putNextEntry(ZipEntry("classes.dex"))
        zos.write("dex\n035\u0000".toByteArray())
        zos.closeEntry()
        zos.close()

        val bytes = bos.toByteArray()
        val reqBody = bytes.toRequestBody("application/vnd.android.package-archive".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "suspicious_sample.apk", reqBody)
        chatViewModel.inspectFile(part)
    }

    fun runSampleExeTest() {
        val exeBytes = (
            "MZ\u0000\u0000PE\u0000\u0000" +
            "VirtualAllocEx WriteProcessMemory CreateRemoteThread SetWindowsHookEx"
        ).toByteArray()
        val reqBody = exeBytes.toRequestBody("application/x-msdownload".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "trojan_dropper.exe", reqBody)
        chatViewModel.inspectFile(part)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Offline Security Analyzer",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Info Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Zero-Execution Static Inspector",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Inspects APK permissions and PE headers/imports entirely offline without execution or installation. No cloud AI credentials required.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { runSampleApkTest() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Inspect APK", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Button(
                                onClick = { runSampleExeTest() },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue.copy(alpha = 0.2f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NeonBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Inspect EXE", color = NeonBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Loading indicator
            if (state.isInspectingFile) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonBlue)
                    }
                }
            }

            // Results Card
            val result = state.fileInspection
            if (result != null && !state.isInspectingFile) {
                item {
                    val threatColor = when (result.threatLevel) {
                        "CRITICAL" -> StatusError
                        "HIGH" -> StatusError
                        "SUSPICIOUS" -> Color(0xFFFFA000)
                        "MEDIUM" -> Color(0xFFFFA000)
                        "LOW" -> NeonBlue
                        else -> StatusSuccess
                    }

                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        borderColor = threatColor
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = result.filename,
                                        color = TextPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = result.fileType,
                                        color = TextMuted,
                                        fontSize = 12.sp
                                    )
                                }

                                Surface(
                                    color = threatColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, threatColor)
                                ) {
                                    Text(
                                        text = result.threatLevel,
                                        color = threatColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Risk Score
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Risk Score",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "${result.riskScore} / 100",
                                    color = threatColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            LinearProgressIndicator(
                                progress = { (result.riskScore / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .padding(top = 4.dp),
                                color = threatColor,
                                trackColor = CyberDarkBorder
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = "SHA-256: ${result.sha256.take(24)}...",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Findings list header
                item {
                    Text(
                        text = "Static Structural Findings (${result.findings.size})",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(result.findings) { finding ->
                    val findingColor = when (finding.severity) {
                        "CRITICAL" -> StatusError
                        "HIGH" -> StatusError
                        "SUSPICIOUS" -> Color(0xFFFFA000)
                        else -> NeonBlue
                    }

                    Surface(
                        color = CyberDarkSurface,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CyberDarkBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Surface(
                                color = findingColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = finding.severity,
                                    color = findingColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = finding.message,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Disclaimer Card
                item {
                    Surface(
                        color = Color(0xFF1E2430),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E384D)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = NeonBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = result.disclaimer,
                                color = TextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
