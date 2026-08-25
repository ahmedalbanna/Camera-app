package com.example.utils

import android.content.Context
import android.net.Uri

fun isVideoUri(context: Context, uri: Uri): Boolean {
    val mime = context.contentResolver.getType(uri)
    return mime?.startsWith("video", ignoreCase = true) == true
}
