package com.zerochat.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.theme.*

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: ImageVector? = null,
    height: Dp = 52.dp,
    isSecondary: Boolean = false
) {
    val shape = RoundedCornerShape(12.dp)

    val backgroundBrush = when {
        !enabled -> Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF1E293B)))
        isSecondary -> Brush.horizontalGradient(listOf(Color(0xFF131B2A), Color(0xFF162032)))
        else -> Brush.horizontalGradient(listOf(ElectricBlue, NeonBlue))
    }

    val borderColor = when {
        !enabled -> Color(0xFF334155)
        isSecondary -> NeonBlue.copy(alpha = 0.5f)
        else -> NeonBlue
    }

    val contentColor = when {
        !enabled -> Color(0xFF64748B)
        isSecondary -> NeonBlue
        else -> CyberDarkBackground
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(
                elevation = if (enabled && !isSecondary) 8.dp else 0.dp,
                shape = shape,
                ambientColor = NeonBlue.copy(alpha = 0.4f),
                spotColor = NeonBlue.copy(alpha = 0.4f)
            )
            .clip(shape)
            .background(backgroundBrush)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled && !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = contentColor,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
