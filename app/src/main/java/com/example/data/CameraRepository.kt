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

    // Phase 2: Paged gallery via MediaStore.Files (images + videos)
    data class GalleryItem(val uri: Uri, val isVideo: Boolean, val dateAdded: Long)

    suspend fun getGalleryPage(page: Int, pageSize: Int = 30): List<GalleryItem> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
        val result = mutableListOf<GalleryItem>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bundle = Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, pageSize)
                    putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, page * pageSize)
                }
                context.contentResolver.query(collection, projection, bundle, null)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val type = cursor.getInt(typeCol)
                        val date = cursor.getLong(dateCol)
                        val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val uri = ContentUris.withAppendedId(collection, id)
                        // For Files collection, we need to use Images/Video uri for actual access? Files uri works for query but for display use proper
                        // Reconstruct proper uri via Images/Video
                        val properUri = if (isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        result.add(GalleryItem(properUri, isVideo, date))
                    }
                }
            } else {
                // Legacy: query without offset/limit, manually slice
                context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val offset = page * pageSize
                    var index = 0
                    while (cursor.moveToNext() && result.size < pageSize) {
                        if (index++ < offset) continue
                        val id = cursor.getLong(idCol)
                        val type = cursor.getInt(typeCol)
                        val date = cursor.getLong(dateCol)
                        val isVideo = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                        val properUri = if (isVideo) ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                        else ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        result.add(GalleryItem(properUri, isVideo, date))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraRepository", "Gallery query failed", e)
        }
        result
    }

    companion object {
        const val RELATIVE_PATH = "DCIM/Al-Adasa" // Phase 2: branded folder, was DCIM/Camera
        const val ALTERNATE_PATH = "DCIM/Camera"
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
