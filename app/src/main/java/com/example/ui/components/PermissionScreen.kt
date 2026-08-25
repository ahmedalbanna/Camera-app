package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGolden
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateSurface

// Phase 1.3: migrated from accompanist.permissions to ActivityResult; handles rationale + Settings deep link
@Composable
fun PermissionScreen(
    hasCameraPermission: Boolean,
    hasAudioPermission: Boolean,
    shouldShowRationale: Boolean,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SlateBackground)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(SlateSurface)
                    .border(3.dp, AmberGolden, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = "العدسة", tint = AmberGolden, modifier = Modifier.size(48.dp))
            }
            Text(text = "الوصول إلى الكاميرا", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                text = if (shouldShowRationale) {
                    "لقد رفضت الصلاحيات سابقاً. يرجى منحها يدوياً من الإعدادات لتمكين التصوير."
                } else {
                    "تطبيق العدسة بحاجة إلى صلاحيات الكاميرا والميكروفون لعرض البث الحي والتقاط الصور والفيديوهات وحفظها بجهازك."
                },
                color = Color.LightGray,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (shouldShowRationale) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGolden),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)
                ) {
                    Text(text = "فتح الإعدادات", color = SlateBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onRequestPermissions) {
                    Text(text = "محاولة مرة أخرى", color = AmberGolden)
                }
            } else {
                Button(
                    onClick = onRequestPermissions,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGolden),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp)
                ) {
                    Text(text = "منح الصلاحيات لبدء التصوير", color = SlateBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!hasCameraPermission || !hasAudioPermission) {
                Text(
                    text = "بدون الصلاحيات لن تتمكن من استخدام الكاميرا.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Legacy wrapper for still using accompanist — kept for tests, deprecated in Phase 1.3
@Deprecated("Use PermissionScreen with ActivityResult")
@Composable
fun LegacyPermissionPlaceholder(
    onRequest: () -> Unit
) {
    PermissionScreen(
        hasCameraPermission = false,
        hasAudioPermission = false,
        shouldShowRationale = false,
        onRequestPermissions = onRequest
    )
}
