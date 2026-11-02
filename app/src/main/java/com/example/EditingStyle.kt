package com.example.model

import androidx.compose.ui.graphics.Color

enum class StyleLayoutType {
    SPLIT_DUAL_BANNER       // Top Image + Center Video + Bottom Image
}

enum class EditingStyle(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color,
    val badgeText: String,
    val layoutType: StyleLayoutType,
    val requiresImages: Boolean = true
) {
    DUAL_BANNER(
        id = "dual_banner",
        displayName = "Top & Bottom Image + Center Video",
        subtitle = "9:16 TikTok / Reels Split Banner Template",
        description = "Places your custom image at the top, plays your video in the center, and displays your custom image at the bottom.",
        accentColor = Color(0xFF38BDF8), // Cyan Accent
        badgeText = "9:16 TIKTOK STYLE",
        layoutType = StyleLayoutType.SPLIT_DUAL_BANNER,
        requiresImages = true
    );

    companion object {
        fun defaultSelection(): Set<EditingStyle> = setOf(DUAL_BANNER)
    }
}
