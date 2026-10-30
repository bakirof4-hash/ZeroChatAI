package com.zerochat.ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerochat.ai.ui.theme.*

@Composable
fun CodeInputField(
    code: String,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    onComplete: () -> Unit = {}
) {
    BasicTextField(
        value = code,
        onValueChange = { input ->
            val filtered = input.filter { it.isDigit() }
            if (filtered.length <= length) {
                onCodeChange(filtered)
                if (filtered.length == length) {
                    onComplete()
                }
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        decorationBox = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier.fillMaxWidth()
            ) {
                repeat(length) { index ->
                    val char = code.getOrNull(index)?.toString() ?: ""
                    val isCurrent = index == code.length
                    val isFilled = index < code.length

                    val borderColor = when {
                        isError -> StatusError
                        isCurrent -> NeonBlue
                        isFilled -> ElectricBlue
                        else -> CyberDarkBorder
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberInputBackground)
                            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
                    ) {
                        Text(
                            text = char,
                            color = if (isFilled) NeonBlue else TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    )
}

