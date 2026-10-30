package com.zerochat.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.theme.*

/**
 * Founder & Verified Blue Badge Component.
 *
 * SPECIFICATIONS:
 * 1. Assign the founder account a blue verified badge (✓) displayed next to the username.
 * 2. Visually distinct from ordinary user badges:
 *    - Founder badge incorporates a luminous radial neon glow, electric cyan border,
 *      and distinct "FOUNDER" pill identifier.
 *    - Standard verified badge provides a clean blue checkmark.
 * 3. Appears consistently in:
 *    - Chat message headers
 *    - User profile screens
 *    - Group chat participant lists
 *    - Search results
 */
@Composable
fun BlueVerifiedBadge(
    isFounder: Boolean = false,
    size: Dp = 18.dp,
    showFounderLabel: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        if (isFounder) {
            // Distinct Founder Blue Badge with Electric Neon Gradient and Checkmark
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = NeonBlue,
                        spotColor = NeonBlue
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00E5FF),
                                Color(0xFF0077FE),
                                Color(0xFF0044CC)
                            )
                        )
                    )
                    .border(1.dp, Color(0xFFE0F7FF), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Founder Verified Checkmark",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.72f)
                )
            }

            if (showFounderLabel) {
                // Founder & Super Admin tag pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF002244), Color(0xFF003875))
                            )
                        )
                        .border(1.dp, NeonBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "Super Admin",
                        tint = NeonBlue,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "FOUNDER",
                        color = NeonBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        } else {
            // Standard Verified Blue Checkmark
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF38BDF8),
                                Color(0xFF0284C7)
                            )
                        )
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Verified Checkmark",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.7f)
                )
            }
        }
    }
}

