package com.example.service

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

/**
 * Manages persistent batch numbering and organization.
 * Ensures each clipping session receives a dedicated sequential folder (Batch_001, Batch_002, ...)
 * and that clips inside are numbered sequentially (Clip_001.mp4, Clip_002.mp4, ...).
 * Numbering persists across app restarts and inspects existing folders to prevent collisions.
 */
class BatchManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("autoclip_batch_prefs", Context.MODE_PRIVATE)

    /**
     * Generates and commits the next persistent unique batch name (e.g. "Batch_001", "Batch_002").
     */
    fun createNextBatch(albumName: String = "AutoClip"): String {
        val lastSavedIndex = prefs.getInt(KEY_LAST_BATCH_INDEX, 0)
        val diskMaxIndex = detectMaxExistingBatchIndex(albumName)
        val nextIndex = maxOf(lastSavedIndex + 1, diskMaxIndex + 1, 1)
        prefs.edit().putInt(KEY_LAST_BATCH_INDEX, nextIndex).apply()
        val batchName = formatBatchName(nextIndex)
        Log.i(TAG, "Generated next persistent batch: $batchName (index: $nextIndex)")
        return batchName
    }

    /**
     * Gets the latest/current batch name without incrementing.
     */
    fun getCurrentBatchName(): String {
        val lastIndex = prefs.getInt(KEY_LAST_BATCH_INDEX, 1).coerceAtLeast(1)
        return formatBatchName(lastIndex)
    }

    /**
     * Formats integer index to "Batch_001", "Batch_002", etc.
     */
    fun formatBatchName(index: Int): String {
        return "Batch_%03d".format(index)
    }

    /**
     * Formats clip sequential filename: "Clip_001.mp4", "Clip_002.mp4", etc.
     */
    fun formatClipFileName(sequenceNumber: Int): String {
        return "Clip_%03d.mp4".format(sequenceNumber)
    }

    /**
     * Scans local clips directory and device Movies album to find the highest existing batch number.
     */
    fun detectMaxExistingBatchIndex(albumName: String = "AutoClip"): Int {
        var maxIndex = 0
        // 1. Check app internal storage: context.filesDir/clips/
        val localClipsDir = File(context.filesDir, "clips")
        if (localClipsDir.exists() && localClipsDir.isDirectory) {
            localClipsDir.listFiles()?.forEach { file ->
                if (file.isDirectory && file.name.startsWith("Batch_")) {
                    val num = file.name.substringAfter("Batch_").toIntOrNull()
                    if (num != null && num > maxIndex) maxIndex = num
                }
            }
        }
        // 2. Check device public Movies storage: Movies/<albumName>/
        try {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), albumName)
            if (publicDir.exists() && publicDir.isDirectory) {
                publicDir.listFiles()?.forEach { file ->
                    if (file.isDirectory && file.name.startsWith("Batch_")) {
                        val num = file.name.substringAfter("Batch_").toIntOrNull()
                        if (num != null && num > maxIndex) maxIndex = num
                    }
                }
            }
        } catch (_: Exception) {}
        return maxIndex
    }

    companion object {
        private const val TAG = "BatchManager"
        private const val KEY_LAST_BATCH_INDEX = "key_last_batch_index"
    }
}
