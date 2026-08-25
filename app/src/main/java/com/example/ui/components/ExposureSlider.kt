package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGolden

@Composable
fun ExposureSlider(
    exposureIndex: Int,
    exposureRange: IntRange?,
    onExposureChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentRange = exposureRange
    if (currentRange != null) {
        Box(
            modifier = modifier
                .width(42.dp)
                .height(240.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Brightness",
                    tint = AmberGolden,
                    modifier = Modifier.size(20.dp)
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val steps = (currentRange.last - currentRange.first - 1).coerceAtLeast(0)
                    Slider(
                        value = exposureIndex.toFloat().coerceIn(currentRange.first.toFloat(), currentRange.last.toFloat()),
                        onValueChange = { onExposureChange(it.toInt()) },
                        valueRange = currentRange.first.toFloat()..currentRange.last.toFloat(),
                        steps = steps,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = AmberGolden,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .graphicsLayer { rotationZ = 270f }
                            .width(130.dp)
                    )
                }
                Text(
                    text = if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    } else {
        // Disabled placeholder while camera not ready
        Box(
            modifier = modifier
                .width(42.dp)
                .height(240.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight()
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = "Brightness",
                    tint = AmberGolden.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Slider(
                        value = 0f,
                        onValueChange = {},
                        enabled = false,
                        valueRange = -4f..4f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White.copy(alpha = 0.5f),
                            activeTrackColor = AmberGolden.copy(alpha = 0.5f),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier
                            .graphicsLayer { rotationZ = 270f }
                            .width(130.dp)
                    )
                }
                Text(
                    text = if (exposureIndex >= 0) "+$exposureIndex" else "$exposureIndex",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
