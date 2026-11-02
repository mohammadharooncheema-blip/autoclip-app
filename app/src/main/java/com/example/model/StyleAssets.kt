package com.example.model

import android.net.Uri

enum class VideoFramingMode(val label: String, val subtitle: String, val aspectRatio: Float) {
    EXPANDED_4_3("4:3 Expanded", "Bigger & taller video (No black gaps)", 4f / 3f),
    SQUARE_1_1("1:1 Square", "Maximum height for mobile feeds", 1f / 1f),
    FIT_16_9("16:9 Standard", "Full width (Seamless banners)", 16f / 9f)
}

data class StyleAssets(
    val topImageUri: Uri? = null,
    val topImagePath: String? = null,
    val bottomImageUri: Uri? = null,
    val bottomImagePath: String? = null,
    val mirrorTopImage: Boolean = true,
    val centerTagText: String = "BEST MOMENT",
    // Center Tag Badge UI was removed: badge stays off, no toggle is shown.
    val showCenterTag: Boolean = false,
    val framingMode: VideoFramingMode = VideoFramingMode.EXPANDED_4_3
) {
    val effectiveBottomPath: String?
        get() = if (mirrorTopImage) topImagePath else (bottomImagePath ?: topImagePath)
}
