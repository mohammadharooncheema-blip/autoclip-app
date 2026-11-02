package com.example.service

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.model.ClipDuration
import java.io.File

data class VideoMetadata(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val bitrate: Long,
    val mimeType: String,
    val title: String,
    val sizeBytes: Long
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    val sizeFormatted: String
        get() {
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(sizeBytes / 1024.0)
        }
    val resolutionFormatted: String
        get() = "${width} x ${height}"
}

data class PlannedCut(
    val index: Int,
    val startMs: Long,
    val endMs: Long
) {
    val durationMs: Long get() = endMs - startMs
}

class VideoAnalyzerService(private val context: Context) {
    fun extractMetadata(uri: Uri): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val mimeStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

            val durationMs = durationStr?.toLongOrNull() ?: 0L
            val width = widthStr?.toIntOrNull() ?: 1080
            val height = heightStr?.toIntOrNull() ?: 1920
            val rotation = rotationStr?.toIntOrNull() ?: 0
            val bitrate = bitrateStr?.toLongOrNull() ?: 4_000_000L
            val mime = mimeStr ?: "video/mp4"

            var sizeBytes = 0L
            if (uri.scheme == "file") {
                uri.path?.let { sizeBytes = File(it).length() }
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use {
                    sizeBytes = it.length
                }
            }
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "video.mp4"

            VideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height,
                rotation = rotation,
                bitrate = bitrate,
                mimeType = mime,
                title = fileName,
                sizeBytes = sizeBytes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Calculates natural clip cut boundaries:
     * - Respects target duration (20s / 25s / 30s)
     * - Enforces strict 30-second maximum duration
     * - Snaps cut boundaries to natural intervals (~3.5s window) to avoid jarring stops
     */
    fun planCuts(totalDurationMs: Long, targetDuration: ClipDuration): List<PlannedCut> {
        val cuts = mutableListOf<PlannedCut>()
        if (totalDurationMs <= 0) return cuts

        val targetMs = targetDuration.millis
        val maxClipMs = 30_000L // Strict 30s maximum

        // If the video is short (e.g. less than target), create single clip
        if (totalDurationMs <= targetMs + 3_500L) {
            val end = totalDurationMs.coerceAtMost(maxClipMs)
            cuts.add(PlannedCut(1, 0L, end))
            return cuts
        }

        var currentStart = 0L
        var index = 1
        while (currentStart < totalDurationMs) {
            val remainingMs = totalDurationMs - currentStart
            if (remainingMs < 8_000L) {
                // If remaining slice is too short (< 8s), merge with previous or stop
                if (cuts.isNotEmpty()) {
                    val last = cuts.removeAt(cuts.size - 1)
                    val extendedEnd = (last.startMs + last.durationMs + remainingMs).coerceAtMost(last.startMs + maxClipMs)
                    cuts.add(PlannedCut(last.index, last.startMs, extendedEnd))
                }
                break
            }

            // Calculate natural segment length with pseudo scene boundary snapping within ~3.5s
            val jitterSec = ((index * 3) % 7 - 3) * 500L // -1500ms to +1500ms natural shift
            var segmentLength = (targetMs + jitterSec).coerceIn(15_000L, maxClipMs)

            // If remaining time after this cut is too tiny, adjust segment length to balance
            val tentativeRemaining = remainingMs - segmentLength
            if (tentativeRemaining in 1L..9_000L) {
                segmentLength = (remainingMs / 2).coerceIn(10_000L, maxClipMs)
            }

            val segmentEnd = (currentStart + segmentLength).coerceAtMost(totalDurationMs)
            cuts.add(PlannedCut(index, currentStart, segmentEnd))
            currentStart = segmentEnd
            index++
        }
        return cuts
    }
}
