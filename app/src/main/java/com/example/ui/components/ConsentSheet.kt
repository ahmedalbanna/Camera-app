package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGolden
import com.example.ui.theme.SlateBackground
import com.example.ui.theme.SlateSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsentSheet(
    isVisible: Boolean,
    aiConsent: Boolean,
    geminiEnabled: Boolean,
    smartOrganize: Boolean,
    captureAssist: Boolean,
    hasValidKey: Boolean,
    onConsentChange: (Boolean) -> Unit,
    onGeminiChange: (Boolean) -> Unit,
    onSmartOrganizeChange: (Boolean) -> Unit,
    onCaptureAssistChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SlateBackground,
        contentColor = Color.White,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(SlateSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.PrivacyTip, contentDescription = null, tint = AmberGolden, modifier = Modifier.size(22.dp))
            }
            Text(text = "الذكاء الاصطناعي — اختياري", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "جميع الميزات الذكية متوقفة افتراضياً. عند التفعيل، يعمل التحليل محلياً أولاً (ML Kit) بدون إنترنت. Gemini (السحابة) يعمل فقط عند تفعيله + وجود مفتاح صالح + اتصال Wi-Fi، مع تسجيل كل استدعاء وعدم إرسال أي بيانات شخصية بدون موافقة.",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 12.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Start
            )

            ConsentRow(
                title = "تفعيل المعالجة الذكية",
                desc = "السماح بالتحليل المحلي وتوليد الاقتراحات",
                checked = aiConsent,
                onChecked = onConsentChange
            )
            ConsentRow(
                title = "Gemini السحابي (Wi-Fi فقط)",
                desc = if (hasValidKey) "يتطلب مفتاح GEMINI_API_KEY صالح" else "المفتاح غير مُكوّن — سيعمل المحلي فقط",
                checked = geminiEnabled,
                enabled = aiConsent && hasValidKey,
                onChecked = onGeminiChange
            )
            ConsentRow(
                title = "التنظيم الذكي",
                desc = "وسوم تلقائية للمعرض (محلي أولاً)",
                checked = smartOrganize,
                enabled = aiConsent,
                onChecked = onSmartOrganizeChange
            )
            ConsentRow(
                title = "مساعد التكوين",
                desc = "اقتراح الشبكة والأفق قبل الالتقاط",
                checked = captureAssist,
                enabled = aiConsent,
                onChecked = onCaptureAssistChange
            )

            if (!hasValidKey) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Text(
                            text = "للتفعيل السحابي: أضف GEMINI_API_KEY في .env (انظر .env.example) أو عبر Secrets في AI Studio. العمل المحلي لا يحتاج مفتاح.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("إغلاق", color = Color.White)
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberGolden),
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("حفظ", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ConsentRow(
    title: String,
    desc: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, color = if (enabled) Color.White else Color.Gray, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(text = desc, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 14.sp)
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AmberGolden,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0xFF1E293B)
            )
        )
    }
}
