package com.example.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for MediaStore queries. Phase 1.1 — isolates I/O from ViewModel/Activity.
 * All queries run on Dispatchers.IO and use modern Bundle LIMIT on API 30+.
 */
class CameraRepository(private val context: Context) {

    suspend fun getLastCapturedMedia(): Uri? = withContext(Dispatchers.IO) {
        val imagesUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val videosUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DATE_ADDED
        )

        var lastImageUri: Uri? = null
        var lastImageDate: Long = 0
        var lastVideoUri: Uri? = null
        var lastVideoDate: Long = 0

        try {
            querySingleMedia(
                uri = imagesUri,
                projection = imageProjection,
                idColumnName = MediaStore.Images.Media._ID,
                dateColumnName = MediaStore.Images.Media.DATE_ADDED
            )?.let { (uri, date) ->
                lastImageUri = uri
                lastImageDate = date
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error querying last image", e)
        }

        try {
            querySingleMedia(
                uri = videosUri,
                projection = videoProjection,
                idColumnName = MediaStore.Video.Media._ID,
                dateColumnName = MediaStore.Video.Media.DATE_ADDED
            )?.let { (uri, date) ->
                lastVideoUri = uri
                lastVideoDate = date
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Error querying last video", e)
        }

        when {
            lastImageUri != null && lastVideoUri != null ->
                if (lastImageDate >= lastVideoDate) lastImageUri else lastVideoUri
            else -> lastImageUri ?: lastVideoUri
        }
    }

    suspend fun getLastImageUriFallback(): Uri? = withContext(Dispatchers.IO) {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                return@withContext ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )
            }
        }
        null
    }

    fun isVideo(uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri)
        return mime?.startsWith("video", ignoreCase = true) == true
    }

    private fun querySingleMedia(
        uri: Uri,
        projection: Array<String>,
        idColumnName: String,
        dateColumnName: String
    ): Pair<Uri, Long>? {
        val sortColumn = dateColumnName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bundle = Bundle().apply {
                    putString(
                        android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                        "$sortColumn DESC"
                    )
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 1)
                }
                context.contentResolver.query(uri, projection, bundle, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumnName))
                        val date = cursor.getLong(cursor.getColumnIndexOrThrow(dateColumnName))
                        return Pair(ContentUris.withAppendedId(uri, id), date)
                    }
                }
            } catch (e: Exception) {
                Log.w("CameraRepository", "Bundle query failed, fallback to legacy", e)
            }
        }
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "$sortColumn DESC"
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(idColumnName))
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(dateColumnName))
                return Pair(ContentUris.withAppendedId(uri, id), date)
            }
        }
        return null
    }
}
