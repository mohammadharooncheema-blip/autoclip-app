package com.example.model

enum class ProcessingPhase(val stepNumber: Int, val title: String, val detail: String) {
    ANALYZING(1, "Analyzing video...", "Probing stream metadata, bitrate & audio track"),
    TRANSCRIBING(2, "Transcribing audio...", "Generating synchronized timestamps & speech transcript"),
    FINDING_CLIPS(3, "Finding clips...", "Snapping cut boundaries to 20s/25s/30s intervals"),
    APPLYING_STYLES(4, "Applying layout...", "Configuring 9:16 vertical 20/60/20 template & subtitle preset"),
    RENDERING(5, "Rendering MP4...", "Burning subtitles into 1080x1920 video pixels with gameplay audio"),
    SAVING_GALLERY(6, "Saving to Gallery...", "Exporting MP4 files into Movies/AutoClip/ album")
}

sealed class ProcessingUiState {
    data object Idle : ProcessingUiState()
    data class InProgress(
        val phase: ProcessingPhase,
        val progress: Float, // 0.0f to 1.0f
        val currentClipIndex: Int = 0,
        val totalClips: Int = 0,
        val currentStyleName: String = "",
        val statusMessage: String = "",
        val batchName: String = ""
    ) : ProcessingUiState()

    data class Completed(
        val clips: List<ClipSegment>,
        val savedCount: Int,
        val durationSeconds: Long,
        val batchName: String = ""
    ) : ProcessingUiState()

    data class Error(val message: String) : ProcessingUiState()
}
