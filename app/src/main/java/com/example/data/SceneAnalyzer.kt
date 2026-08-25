package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.tasks.await

/**
 * Phase 3: On-device scene detection — ML Kit ImageLabeling (offline, no network).
 * Suggests CameraMode based on labels. All on-device, no PII.
 */
class SceneAnalyzer(private val context: Context) {

    data class SceneResult(
        val labels: List<String>,
        val confidence: Float,
        val suggestedMode: String?, // PHOTO, PORTRAIT, PANORAMA, PRO
        val rawTopLabel: String?
    )

    private val labeler by lazy {
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    }

    suspend fun analyze(uri: Uri): SceneResult? {
        return try {
            val bitmap = loadBitmap(uri) ?: return null
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = labeler.process(image).await()
            if (labels.isEmpty()) return null
            val top = labels.maxByOrNull { it.confidence }
            val labelTexts = labels.map { it.text }
            val suggested = when {
                labelTexts.any { it.contains("Face", true) || it.contains("Person", true) || it.contains("Portrait", true) } -> "PORTRAIT"
                labelTexts.any { it.contains("Landscape", true) || it.contains("Mountain", true) || it.contains("Beach", true) || it.contains("Sky", true) } -> "PANORAMA"
                labelTexts.any { it.contains("Document", true) || it.contains("Text", true) } -> "PRO"
                else -> null
            }
            Log.d("SceneAnalyzer", "labels=$labelTexts top=${top?.text} conf=${top?.confidence} suggested=$suggested")
            SceneResult(
                labels = labelTexts,
                confidence = top?.confidence ?: 0f,
                suggestedMode = suggested,
                rawTopLabel = top?.text
            )
        } catch (e: Exception) {
            Log.e("SceneAnalyzer", "Analyze failed for $uri", e)
            null
        }
    }

    suspend fun analyzeAndSuggest(uri: Uri): String? {
        val result = analyze(uri) ?: return null
        return result.suggestedMode
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.isMutableRequired = true
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (e: Exception) {
            Log.e("SceneAnalyzer", "loadBitmap failed", e)
            null
        }
    }

    fun close() {
        try { labeler.close() } catch (_: Exception) {}
    }
}
