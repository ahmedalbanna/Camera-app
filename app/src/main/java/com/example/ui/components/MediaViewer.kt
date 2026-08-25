package com.example.ui.components

import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.CameraViewModel
import com.example.ui.theme.SlateSurface
import com.example.utils.isVideoUri

@Composable
fun MediaViewer(
    isVisible: Boolean,
    uri: Uri?,
    viewModel: CameraViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingDeleteUri by remember { mutableStateOf<Uri?>(null) }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.updateLastCapturedUri(null)
            viewModel.loadLastCapturedMedia()
            onClose()
            showDeleteConfirmDialog = false
        } else {
            viewModel.setErrorMessage("تم إلغاء الحذف")
        }
        pendingDeleteUri = null
    }

    AnimatedVisibility(
        visible = isVisible && uri != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val fileUri = uri
        if (fileUri != null) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val isVideoFile = isVideoUri(context, fileUri)

                if (isVideoFile) {
                    // Phase 2: ExoPlayer with loop + seek
                    val exoPlayer = remember(fileUri) {
                        ExoPlayer.Builder(context).build().apply {
                            setMediaItem(MediaItem.fromUri(fileUri))
                            repeatMode = ExoPlayer.REPEAT_MODE_ONE
                            prepare()
                            playWhenReady = true
                        }
                    }
                    androidx.compose.runtime.DisposableEffect(fileUri) {
                        onDispose { exoPlayer.release() }
                    }
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                                controllerShowTimeoutMs = 3000
                            }
                        },
                        update = { view ->
                            if (view.player != exoPlayer) view.player = exoPlayer
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = fileUri,
                        contentDescription = "معاينة الصورة ملء الشاشة",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                            Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
                        }
                        androidx.compose.material3.Text(text = if (isVideoFile) "مقطع فيديو" else "لقطة مصورة", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bottom bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                        .padding(24.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(
                            onClick = { showDeleteConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "حذف")
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.Text("حذف اللقطة", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onClose,
                            border = BorderStroke(1.dp, Color.White),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            androidx.compose.material3.Text("الرجوع للكاميرا", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("حذف اللقطة؟", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                text = { Text("هل أنت متأكد من رغبتك في حذف هذا الملف نهائياً من ذاكرة جهازك؟", textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth()) },
                confirmButton = {
                    Button(
                        onClick = {
                            val uriToDelete = uri
                            if (uriToDelete != null) {
                                try {
                                    val rows = context.contentResolver.delete(uriToDelete, null, null)
                                    if (rows >= 0) {
                                        viewModel.updateLastCapturedUri(null)
                                        viewModel.loadLastCapturedMedia()
                                        showDeleteConfirmDialog = false
                                        onClose()
                                    }
                                } catch (e: RecoverableSecurityException) {
                                    try {
                                        pendingDeleteUri = uriToDelete
                                        val intentSender: IntentSender = e.userAction.actionIntent.intentSender
                                        val request = IntentSenderRequest.Builder(intentSender).build()
                                        deleteLauncher.launch(request)
                                    } catch (ex: Exception) {
                                        Log.e("DeleteMedia", "Failed to launch delete consent", ex)
                                        viewModel.setErrorMessage("تعذر طلب إذن الحذف")
                                    }
                                } catch (e: SecurityException) {
                                    val cause = e.cause
                                    if (cause is RecoverableSecurityException) {
                                        try {
                                            pendingDeleteUri = uriToDelete
                                            val intentSender = cause.userAction.actionIntent.intentSender
                                            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                                        } catch (ex: Exception) {
                                            Log.e("DeleteMedia", "Failed to launch delete consent", ex)
                                        }
                                    } else {
                                        Log.e("DeleteMedia", "Security exception on delete", e)
                                        viewModel.setErrorMessage("لا يمكن حذف الملف: ليس لديك إذن")
                                    }
                                } catch (e: Exception) {
                                    Log.e("DeleteMedia", "Failed to delete", e)
                                    viewModel.setErrorMessage("فشل الحذف: ${e.message}")
                                }
                            } else {
                                showDeleteConfirmDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) { Text("نعم، حذف", color = Color.White) }
                },
                dismissButton = { TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("إلغاء", color = Color.Gray) } },
                containerColor = SlateSurface,
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
