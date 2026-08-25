package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGolden
import com.example.ui.theme.SlateSurface
import java.util.Locale

@Composable
fun ProControls(
    aperture: Float,
    isHdrEnabled: Boolean,
    manualFocus: Float?,
    onApertureChange: (Float) -> Unit,
    onHdrToggle: () -> Unit,
    onManualFocusChange: (Float?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Tune, contentDescription = "Pro", tint = AmberGolden, modifier = Modifier.size(16.dp))
            Text(text = "وضع المحترف", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Box(modifier = Modifier.weight(1f))
            FilterChip(
                selected = isHdrEnabled,
                onClick = onHdrToggle,
                label = { Text("HDR", fontSize = 11.sp) },
                leadingIcon = { Icon(Icons.Default.HdrOn, contentDescription = null, modifier = Modifier.size(14.dp)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = AmberGolden, selectedLabelColor = Color.Black)
            )
        }

        // Aperture (Portrait bokeh) f/1.4 - f/16
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "فتحة العدسة", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                Text(text = String.format(Locale.US, "f/%.1f", aperture), color = AmberGolden, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(
                value = aperture,
                onValueChange = onApertureChange,
                valueRange = 1.4f..16f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AmberGolden, inactiveTrackColor = Color.White.copy(alpha = 0.2f))
            )
        }

        // Manual focus 0..1, null = auto
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    Text(text = "تركيز يدوي", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }
                Text(
                    text = if (manualFocus == null) "تلقائي" else String.format(Locale.US, "%.2f", manualFocus),
                    color = if (manualFocus == null) Color.Gray else AmberGolden,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = manualFocus ?: 0.5f,
                    onValueChange = { onManualFocusChange(it) },
                    valueRange = 0f..1f,
                    enabled = true,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AmberGolden, inactiveTrackColor = Color.White.copy(alpha = 0.2f)),
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.TextButton(onClick = { onManualFocusChange(null) }, modifier = Modifier.height(36.dp)) {
                    Text(text = if (manualFocus == null) "تلقائي" else "يدوي", color = AmberGolden, fontSize = 11.sp)
                }
            }
        }
    }
}
