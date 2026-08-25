package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.CameraMode
import com.example.ui.theme.AmberGolden
import androidx.compose.runtime.rememberCoroutineScope
import android.net.Uri
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.material3.SnackbarHostState

@Composable
fun ControlDeck(
    cameraMode: CameraMode,
    zoomRatio: Float,
    zoomRange: ClosedFloatingPointRange<Float>?,
    lastCapturedUri: Uri?,
    isRecording: Boolean,
    snackbarHostState: SnackbarHostState,
    onSetZoomRatio: (Float) -> Unit,
    onSetCameraMode: (CameraMode) -> Unit,
    onAnnounce: (String) -> Unit,
    onGalleryClick: () -> Unit,
    onShutterClick: () -> Unit,
    onToggleCamera: () -> Unit,
    rotationAngle: Float,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black)))
            .padding(bottom = 20.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Zoom chips — uses White for selected, AmberGolden accent elsewhere
            Row(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val zoomChips = listOf(0.6f, 1.0f, 2.0f)
                zoomChips.forEach { ratio ->
                    val isSelected = zoomRatio == ratio
                    val isAvailable = zoomRange?.let { ratio in it } ?: true
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) Color.White else Color.Transparent)
                            .clickable(enabled = isAvailable) {
                                val clamped = zoomRange?.let { ratio.coerceIn(it.start, it.endInclusive) } ?: ratio
                                onSetZoomRatio(clamped)
                                if (!isAvailable) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("مستوى التقريب غير مدعوم على هذا الجهاز")
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", ratio),
                            color = when {
                                !isAvailable -> Color.Gray
                                isSelected -> Color.Black
                                else -> Color.White
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Mode selector — Phase 2: functional Panorama/Portrait/Pro
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Panorama
                    val isPanoramaSelected = cameraMode == CameraMode.PANORAMA
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onSetCameraMode(CameraMode.PANORAMA) }) {
                        Text(text = "بانوراما", color = if (isPanoramaSelected) AmberGolden else Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (isPanoramaSelected) Box(modifier = Modifier.size(5.dp).background(AmberGolden, CircleShape))
                    }
                    val isPhotoSelected = cameraMode == CameraMode.PHOTO
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onSetCameraMode(CameraMode.PHOTO) }) {
                        Text(text = "صورة", color = if (isPhotoSelected) AmberGolden else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (isPhotoSelected) Box(modifier = Modifier.size(5.dp).background(AmberGolden, CircleShape))
                    }
                    val isVideoSelected = cameraMode == CameraMode.VIDEO
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onSetCameraMode(CameraMode.VIDEO) }) {
                        Text(text = "فيديو", color = if (isVideoSelected) Color(0xFFEF4444) else Color.White.copy(alpha = 0.4f), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        if (isVideoSelected) Box(modifier = Modifier.size(5.dp).background(Color(0xFFEF4444), CircleShape))
                    }
                    val isPortraitSelected = cameraMode == CameraMode.PORTRAIT
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onSetCameraMode(CameraMode.PORTRAIT) }) {
                        Text(text = "بورتريه", color = if (isPortraitSelected) AmberGolden else Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (isPortraitSelected) Box(modifier = Modifier.size(5.dp).background(AmberGolden, CircleShape))
                    }
                    val isProSelected = cameraMode == CameraMode.PRO
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable { onSetCameraMode(CameraMode.PRO) }) {
                        Text(text = "احترافي", color = if (isProSelected) AmberGolden else Color.White.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (isProSelected) Box(modifier = Modifier.size(5.dp).background(AmberGolden, CircleShape))
                    }
                }
            }

            // Shutter row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                // Gallery — Phase 2: always opens gallery grid
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(2.dp)
                        .background(Color(0xFF1E293B))
                        .clickable { onGalleryClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (lastCapturedUri != null) {
                        AsyncImage(model = lastCapturedUri, contentDescription = "آخر لقطة ملتقطة", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
                    } else {
                        Icon(imageVector = Icons.Default.Image, contentDescription = "معرض فارغ", tint = Color.LightGray.copy(alpha = 0.6f))
                    }
                }

                // Shutter
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onShutterClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxSize().border(4.dp, Color.White, CircleShape))
                    val isVideoMode = cameraMode == CameraMode.VIDEO
                    val pulseTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by pulseTransition.animateFloat(initialValue = 1f, targetValue = 1.25f, animationSpec = infiniteRepeatable(animation = tween(1000, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "recordPulse")
                    val scaleValue = if (isRecording && isVideoMode) pulseScale else 1f
                    Box(modifier = Modifier.size(62.dp).scale(scaleValue).clip(if (isRecording && isVideoMode) RoundedCornerShape(10.dp) else CircleShape).background(color = if (isVideoMode) Color(0xFFEF4444) else Color.White))
                }

                // Flip
                IconButton(onClick = onToggleCamera, modifier = Modifier.size(56.dp).background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                    Icon(imageVector = Icons.Default.Cameraswitch, contentDescription = "التبديل بين الكاميرات", tint = Color.White, modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = rotationAngle })
                }
            }
        }
    }
}
