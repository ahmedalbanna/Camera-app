package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SceneAnalyzer
import com.example.ui.theme.AmberGolden
import com.example.ui.theme.SlateSurface

@Composable
fun SceneBadge(
    sceneResult: SceneAnalyzer.SceneResult?,
    isAnalyzing: Boolean,
    onApplySuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isAnalyzing) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AmberGolden, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Text(text = "تحليل المشهد...", color = Color.White, fontSize = 11.sp)
            }
        }
        return
    }
    val result = sceneResult ?: return
    val suggested = result.suggestedMode ?: return
    val (icon, label) = when (suggested) {
        "PORTRAIT" -> Icons.Default.Face to "اقتراح: بورتريه"
        "PANORAMA" -> Icons.Default.Landscape to "اقتراح: بانوراما"
        "PRO" -> Icons.Default.Tune to "اقتراح: احترافي"
        else -> Icons.Default.AutoAwesome to "اقتراح: $suggested"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SlateSurface.copy(alpha = 0.9f))
            .border(1.dp, AmberGolden.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .clickable { onApplySuggestion(suggested) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = AmberGolden, modifier = Modifier.size(14.dp))
                Text(text = label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(6.dp).background(AmberGolden, CircleShape))
                Text(text = result.rawTopLabel ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
            }
            Text(
                text = result.labels.take(3).joinToString(" • "),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CaptureAssistCard(
    text: String?,
    modifier: Modifier = Modifier
) {
    if (text == null) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGolden, modifier = Modifier.size(14.dp))
            Text(text = text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
