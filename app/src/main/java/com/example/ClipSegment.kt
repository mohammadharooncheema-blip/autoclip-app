package com.example.model

import java.io.File

data class ClipSegment(
    val id: String,
    val segmentIndex: Int,
    val startMs: Long,
    val endMs: Long,
    val style: EditingStyle,
    val title: String,
    val filePath: String,
    val thumbnailPath: String? = null,
    val topImagePath: String? = null,
    val bottomImagePath: String? = null,
    val centerTagText: String? = null,
    val framingMode: VideoFramingMode = VideoFramingMode.EXPANDED_4_3,
    val galleryUriString: String? = null,
    val isSavedToGallery: Boolean = false,
    val fileSizeFormatted: String = "",
    val batchName: String = "",
    val clipFileName: String = "Clip_%03d.mp4".format(segmentIndex)
) {
    val durationSeconds: Int get() = ((endMs - startMs) / 1000).toInt().coerceAtLeast(1)
    val timeRangeFormatted: String
        get() {
            val startSec = startMs / 1000
            val endSec = endMs / 1000
            val startMinStr = "%02d:%02d".format(startSec / 60, startSec % 60)
            val endMinStr = "%02d:%02d".format(endSec / 60, endSec % 60)
            return "$startMinStr - $endMinStr"
        }
    val fileExists: Boolean get() = File(filePath).exists()
}
