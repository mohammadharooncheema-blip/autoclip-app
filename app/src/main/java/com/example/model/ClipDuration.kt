package com.example.model

enum class ClipDuration(val seconds: Int, val label: String, val subtitle: String) {
    TWENTY(20, "20 SEC", "Fast-paced shorts & stories"),
    TWENTY_FIVE(25, "25 SEC", "Optimal for TikTok & Reels"),
    THIRTY(30, "30 SEC", "Max standard short form");

    val millis: Long get() = seconds * 1000L
}
