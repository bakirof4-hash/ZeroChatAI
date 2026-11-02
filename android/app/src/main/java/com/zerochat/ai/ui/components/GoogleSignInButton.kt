package com.zerochat.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.theme.NeonBlue

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "Continue with Google",
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !isLoading, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFFFFF),
        shadowElevation = 2.dp
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF4285F4),
                    strokeWidth = 2.5.dp
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    GoogleLogoIcon(modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = text,
                        color = Color(0xFF1F1F1F),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

/**
 * High-fidelity vector rendering of official Google "G" logo colors:
 * Blue (#4285F4), Red (#EA4335), Yellow (#FBBC05), Green (#34A853).
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w / 2f

        // Draw Google G symbol geometry using arcs and paths
        // Blue horizontal bar
        drawRect(
            color = Color(0xFF4285F4),
            topLeft = Offset(cx, cy - h * 0.11f),
            size = Size(w * 0.48f, h * 0.22f)
        )

        // Blue right arc
        val bluePath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = -35f,
                sweepAngleDegrees = 70f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(bluePath, Color(0xFF4285F4), style = Fill)

        // Green bottom arc
        val greenPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = 35f,
                sweepAngleDegrees = 85f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(greenPath, Color(0xFF34A853), style = Fill)

        // Yellow left-bottom arc
        val yellowPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = 120f,
                sweepAngleDegrees = 85f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(yellowPath, Color(0xFFFBBC05), style = Fill)

        // Red top arc
        val redPath = Path().apply {
            moveTo(cx, cy)
            arcTo(
                rect = androidx.compose.ui.geometry.Rect(0f, 0f, w, h),
                startAngleDegrees = 205f,
                sweepAngleDegrees = 120f,
                forceMoveTo = false
            )
            close()
        }
        drawPath(redPath, Color(0xFFEA4335), style = Fill)

        // Inner white cutout for the "G" ring
        drawCircle(
            color = Color(0xFFFFFFFF),
            radius = r * 0.58f,
            center = Offset(cx, cy)
        )

        // Inner white cutout for top right gap of "G"
        drawRect(
            color = Color(0xFFFFFFFF),
            topLeft = Offset(cx + w * 0.05f, 0f),
            size = Size(w * 0.5f, cy - h * 0.11f)
        )
    }
}
