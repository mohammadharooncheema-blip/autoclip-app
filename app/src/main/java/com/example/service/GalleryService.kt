package com.example.service

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileInputStream

class GalleryService(private val context: Context) {
    companion object {
        private const val TAG = "GalleryService"
    }

    /**
     * Saves a rendered MP4 clip to the phone's Gallery into a dedicated batch folder.
     * E.g. Movies/AutoClip/Batch_001/Clip_001.mp4
     */
    fun saveVideoToGallery(
        sourceFile: File,
        displayName: String,
        albumName: String = "AutoClip",
        subfolder: String? = null
    ): Uri? {
        if (!sourceFile.exists()) {
            Log.e(TAG, "Source file does not exist: ${sourceFile.absolutePath}")
            return null
        }
        try {
            val contentResolver = context.contentResolver
            val folderPath = if (!subfolder.isNullOrEmpty()) {
                "${Environment.DIRECTORY_MOVIES}/$albumName/$subfolder"
            } else {
                "${Environment.DIRECTORY_MOVIES}/$albumName"
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.TITLE, displayName.substringBeforeLast('.'))
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, folderPath)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = contentResolver.insert(collectionUri, values) ?: return null

            contentResolver.openOutputStream(itemUri)?.use { outStream ->
                FileInputStream(sourceFile).use { inStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                contentResolver.update(itemUri, values, null, null)
            } else {
                // Pre-Android Q external files fallback
                try {
                    val legacyDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "$albumName/${subfolder ?: ""}")
                    if (!legacyDir.exists()) legacyDir.mkdirs()
                    val legacyFile = File(legacyDir, displayName)
                    sourceFile.copyTo(legacyFile, overwrite = true)
                } catch (_: Exception) {}
            }

            // Broadcast so all Gallery apps immediately index the new clip in its batch folder
            MediaScannerConnection.scanFile(
                context,
                arrayOf(sourceFile.absolutePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                Log.i(TAG, "Scanned clip into Gallery: $path -> $uri")
            }

            Log.i(TAG, "Successfully exported clip to Gallery: $folderPath/$displayName")
            return itemUri
        } catch (e: Exception) {
            Log.e(TAG, "Error saving clip to Gallery: ${e.message}", e)
            return null
        }
    }

    fun openGallery() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    type = "video/*"
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Saved to Movies/AutoClip album", Toast.LENGTH_LONG).show()
            }
        }
    }
}
