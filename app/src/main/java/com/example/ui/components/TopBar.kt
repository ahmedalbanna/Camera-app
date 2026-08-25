package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CustomFlashMode
import com.example.ui.theme.AmberGolden
import com.example.ui.theme.SlateSurface
import com.example.utils.formatDuration

@Composable
fun TopBar(
    flashMode: CustomFlashMode,
    isFlashMenuVisible: Boolean,
    isRecording: Boolean,
    recordingDuration: Int,
    showGridLines: Boolean,
    onToggleFlashMenu: () -> Unit,
    onCloseFlashMenu: () -> Unit,
    onSetFlashMode: (CustomFlashMode) -> Unit,
    onToggleGrid: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                )
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Flash
            Box {
                IconButton(
                    onClick = onToggleFlashMenu,
                    modifier = Modifier
                        .testTag("flash_toggle_trigger")
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = when (flashMode) {
                            CustomFlashMode.AUTO -> Icons.Default.FlashAuto
                            CustomFlashMode.ON -> Icons.Default.FlashOn
                            CustomFlashMode.OFF -> Icons.Default.FlashOff
                        },
                        contentDescription = "Flash mode selector",
                        tint = if (flashMode == CustomFlashMode.OFF) Color.White else AmberGolden
                    )
                }
                DropdownMenu(
                    expanded = isFlashMenuVisible,
                    onDismissRequest = onCloseFlashMenu,
                    modifier = Modifier.background(SlateSurface.copy(alpha = 0.95f))
                ) {
                    DropdownMenuItem(
                        text = { Text("⚡ تلقائي (Auto)", color = Color.White, textAlign = TextAlign.Right) },
                        onClick = { onSetFlashMode(CustomFlashMode.AUTO) }
                    )
                    DropdownMenuItem(
                        text = { Text("⚡ تشغيل (On)", color = AmberGolden, textAlign = TextAlign.Right) },
                        onClick = { onSetFlashMode(CustomFlashMode.ON) }
                    )
                    DropdownMenuItem(
                        text = { Text("⚡ إيقاف (Off)", color = Color.LightGray, textAlign = TextAlign.Right) },
                        onClick = { onSetFlashMode(CustomFlashMode.OFF) }
                    )
                }
            }

            // Middle: recording pill — uses theme red/gray
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                val blinkTransition = rememberInfiniteTransition(label = "blink")
                val blinkAlpha by blinkTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, easing = LinearEasing),
                        repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                    ),
                    label = "blinkAlpha"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = Color.Red.copy(alpha = blinkAlpha),
                                    shape = CircleShape
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(Color.Gray.copy(alpha = 0.7f), CircleShape)
                        )
                    }
                    Text(
                        text = formatDuration(recordingDuration),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Right: Grid toggle — uses AmberGolden when active
            IconButton(
                onClick = onToggleGrid,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(
                    imageVector = if (showGridLines) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "Toggle Grid Layout",
                    tint = if (showGridLines) AmberGolden else Color.White
                )
            }
        }
    }
}
