package com.example.model

enum class OutputQuality(
    val label: String,
    val resolution: String,
    val bitRate: Int,
    val width: Int = 1080,
    val height: Int = 1920
) {
    HIGH("High Quality", "1080 x 1920 (FHD)", 8_000_000, 1080, 1920),
    STANDARD("Standard", "720 x 1280 (HD)", 4_000_000, 720, 1280),
    FAST("Fast Export", "540 x 960 (SD)", 2_000_000, 540, 960)
}

data class AppSettings(
    val quality: OutputQuality = OutputQuality.HIGH,
    val autoSaveGallery: Boolean = true,
    val captionsEnabled: Boolean = true,
    val albumName: String = "AutoClip"
)
