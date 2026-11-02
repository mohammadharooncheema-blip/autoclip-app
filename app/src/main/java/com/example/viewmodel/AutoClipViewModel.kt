package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppSettings
import com.example.model.ClipDuration
import com.example.model.ClipSegment
import com.example.model.EditingStyle
import com.example.model.OutputQuality
import com.example.model.ProcessingPhase
import com.example.model.ProcessingUiState
import com.example.model.StyleAssets
import com.example.model.VideoFramingMode
import com.example.service.BannerAssetHelper
import com.example.service.BatchManager
import com.example.service.GalleryService
import com.example.service.SampleVideoGenerator
import com.example.service.VideoAnalyzerService
import com.example.service.VideoClipperService
import com.example.service.VideoMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class AutoClipViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val analyzerService = VideoAnalyzerService(context)
    private val clipperService = VideoClipperService(context)
    private val galleryService = GalleryService(context)
    private val sampleGenerator = SampleVideoGenerator(context)
    private val bannerHelper = BannerAssetHelper(context)
    private val batchManager = BatchManager(context)

    private val _selectedVideoUri = MutableStateFlow<Uri?>(null)
    val selectedVideoUri: StateFlow<Uri?> = _selectedVideoUri.asStateFlow()

    private val _videoMetadata = MutableStateFlow<VideoMetadata?>(null)
    val videoMetadata: StateFlow<VideoMetadata?> = _videoMetadata.asStateFlow()

    private val _selectedDuration = MutableStateFlow(ClipDuration.TWENTY_FIVE)
    val selectedDuration: StateFlow<ClipDuration> = _selectedDuration.asStateFlow()

    private val _selectedStyles = MutableStateFlow<Set<EditingStyle>>(EditingStyle.defaultSelection())
    val selectedStyles: StateFlow<Set<EditingStyle>> = _selectedStyles.asStateFlow()

    private val _styleAssets = MutableStateFlow(StyleAssets())
    val styleAssets: StateFlow<StyleAssets> = _styleAssets.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _processingState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val processingState: StateFlow<ProcessingUiState> = _processingState.asStateFlow()

    private val _activePlayerClip = MutableStateFlow<ClipSegment?>(null)
    val activePlayerClip: StateFlow<ClipSegment?> = _activePlayerClip.asStateFlow()

    private val _showSettingsDialog = MutableStateFlow(false)
    val showSettingsDialog: StateFlow<Boolean> = _showSettingsDialog.asStateFlow()

    private val _isLoadingSample = MutableStateFlow(false)
    val isLoadingSample: StateFlow<Boolean> = _isLoadingSample.asStateFlow()

    private val _previewVideoFramePath = MutableStateFlow<String?>(null)
    val previewVideoFramePath: StateFlow<String?> = _previewVideoFramePath.asStateFlow()

    private var processingJob: Job? = null

    init {
        val outputDir = File(context.filesDir, "clips")
        if (!outputDir.exists()) outputDir.mkdirs()
        viewModelScope.launch {
            try {
                val defaultBanner = bannerHelper.getOrCreateDefaultBanner()
                _styleAssets.value = _styleAssets.value.copy(
                    topImagePath = defaultBanner.absolutePath,
                    bottomImagePath = defaultBanner.absolutePath
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onVideoSelected(uri: Uri) {
        _selectedVideoUri.value = uri
        viewModelScope.launch {
            val meta = analyzerService.extractMetadata(uri)
            _videoMetadata.value = meta
            extractAndCachePreviewFrame(uri)
        }
    }

    fun loadSampleVideo() {
        if (_isLoadingSample.value) return
        _isLoadingSample.value = true
        viewModelScope.launch {
            try {
                val sampleFile = sampleGenerator.getOrCreateSampleVideo()
                val uri = Uri.fromFile(sampleFile)
                _selectedVideoUri.value = uri
                _videoMetadata.value = analyzerService.extractMetadata(uri)
                extractAndCachePreviewFrame(uri)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoadingSample.value = false
            }
        }
    }

    private suspend fun extractAndCachePreviewFrame(uri: Uri) = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(1_500_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
            if (bitmap != null) {
                val frameFile = File(context.cacheDir, "preview_source_frame.jpg")
                FileOutputStream(frameFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()
                _previewVideoFramePath.value = frameFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    fun previewCurrentSetup() {
        val uri = _selectedVideoUri.value ?: return
        val meta = _videoMetadata.value
        val assets = _styleAssets.value
        val currentStyle = _selectedStyles.value.firstOrNull() ?: EditingStyle.DUAL_BANNER
        viewModelScope.launch {
            try {
                val localSource = clipperService.prepareLocalSourceFile(uri)
                val previewClip = ClipSegment(
                    id = "live_preview_${System.currentTimeMillis()}",
                    segmentIndex = 1,
                    startMs = 0L,
                    endMs = (meta?.durationMs ?: 20_000L).coerceAtMost(25_000L),
                    style = currentStyle,
                    title = "Live 9:16 Preview (${currentStyle.displayName})",
                    filePath = localSource.absolutePath,
                    thumbnailPath = _previewVideoFramePath.value,
                    topImagePath = assets.topImagePath,
                    bottomImagePath = assets.effectiveBottomPath,
                    centerTagText = if (assets.showCenterTag) assets.centerTagText else null,
                    framingMode = assets.framingMode,
                    isSavedToGallery = false,
                    fileSizeFormatted = meta?.sizeFormatted ?: "Preview"
                )
                _activePlayerClip.value = previewClip
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun selectDuration(duration: ClipDuration) {
        _selectedDuration.value = duration
    }

    fun selectStyle(style: EditingStyle) {
        _selectedStyles.value = setOf(style)
    }

    fun toggleStyle(style: EditingStyle) {
        val current = _selectedStyles.value.toMutableSet()
        if (current.contains(style)) {
            if (current.size > 1) {
                current.remove(style)
            }
        } else {
            current.add(style)
        }
        _selectedStyles.value = current
    }

    fun setTopImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val localPath = bannerHelper.copyImageUriToLocalFile(uri, "user_top_banner")
                _styleAssets.value = _styleAssets.value.copy(
                    topImageUri = uri,
                    topImagePath = localPath
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setBottomImageUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val localPath = bannerHelper.copyImageUriToLocalFile(uri, "user_bottom_banner")
                _styleAssets.value = _styleAssets.value.copy(
                    bottomImageUri = uri,
                    bottomImagePath = localPath,
                    mirrorTopImage = false
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setMirrorTopImage(mirror: Boolean) {
        _styleAssets.value = _styleAssets.value.copy(mirrorTopImage = mirror)
    }

    fun setCenterTagText(tag: String) {
        _styleAssets.value = _styleAssets.value.copy(centerTagText = tag)
    }

    fun toggleShowCenterTag(show: Boolean) {
        _styleAssets.value = _styleAssets.value.copy(showCenterTag = show)
    }

    fun updateStyleAssets(assets: StyleAssets) {
        _styleAssets.value = assets
    }

    fun setFramingMode(mode: VideoFramingMode) {
        _styleAssets.value = _styleAssets.value.copy(framingMode = mode)
    }

    fun calculatePlannedClipCount(): Int {
        val meta = _videoMetadata.value ?: return 0
        val cuts = analyzerService.planCuts(meta.durationMs, _selectedDuration.value)
        return cuts.size * _selectedStyles.value.size
    }

    fun calculateSegmentCutsCount(): Int {
        val meta = _videoMetadata.value ?: return 0
        val cuts = analyzerService.planCuts(meta.durationMs, _selectedDuration.value)
        return cuts.size
    }

    fun startProcessing() {
        val uri = _selectedVideoUri.value ?: return
        val meta = _videoMetadata.value ?: return
        val styles = _selectedStyles.value.toList()
        if (styles.isEmpty()) return

        processingJob?.cancel()
        processingJob = viewModelScope.launch {
            try {
                val batchName = batchManager.createNextBatch(_appSettings.value.albumName)
                val batchDir = File(File(context.filesDir, "clips"), batchName)
                if (!batchDir.exists()) batchDir.mkdirs()

                if (_styleAssets.value.topImagePath == null) {
                    val defaultBanner = bannerHelper.getOrCreateDefaultBanner()
                    _styleAssets.value = _styleAssets.value.copy(
                        topImagePath = defaultBanner.absolutePath,
                        bottomImagePath = defaultBanner.absolutePath
                    )
                }
                val currentAssets = _styleAssets.value

                _processingState.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.ANALYZING,
                    progress = 0.08f,
                    statusMessage = "Analyzing ${meta.title} (${meta.resolutionFormatted}, ${meta.durationFormatted})",
                    batchName = batchName
                )
                delay(600)

                val localSource = clipperService.prepareLocalSourceFile(uri)

                val cuts = analyzerService.planCuts(meta.durationMs, _selectedDuration.value)
                val totalClips = cuts.size * styles.size
                _processingState.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.FINDING_CLIPS,
                    progress = 0.22f,
                    totalClips = totalClips,
                    statusMessage = "Found ${cuts.size} natural segments for $batchName (Clip_001 to Clip_%03d)".format(cuts.size),
                    batchName = batchName
                )
                delay(700)

                _processingState.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.APPLYING_STYLES,
                    progress = 0.35f,
                    totalClips = totalClips,
                    statusMessage = "Applying 9:16 Top & Bottom Image template to $batchName",
                    batchName = batchName
                )
                delay(600)

                val generatedClips = mutableListOf<ClipSegment>()
                var clipCounter = 0
                for (cut in cuts) {
                    for (style in styles) {
                        clipCounter++
                        val baseProg = 0.35f + 0.45f * (clipCounter.toFloat() / totalClips)
                        _processingState.value = ProcessingUiState.InProgress(
                            phase = ProcessingPhase.RENDERING,
                            progress = baseProg,
                            currentClipIndex = clipCounter,
                            totalClips = totalClips,
                            currentStyleName = style.displayName,
                            statusMessage = "Rendering $batchName / Clip_%03d.mp4 ($clipCounter of $totalClips)".format(cut.index),
                            batchName = batchName
                        )
                        val segment = clipperService.clipSegment(
                            sourceFile = localSource,
                            segmentIndex = cut.index,
                            startMs = cut.startMs,
                            endMs = cut.endMs,
                            style = style,
                            outputDir = batchDir,
                            styleAssets = currentAssets,
                            quality = _appSettings.value.quality,
                            batchName = batchName
                        )
                        generatedClips.add(segment)
                    }
                }

                var savedCount = 0
                val finalClips = mutableListOf<ClipSegment>()
                val autoSave = _appSettings.value.autoSaveGallery
                _processingState.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.SAVING_GALLERY,
                    progress = 0.90f,
                    totalClips = totalClips,
                    statusMessage = if (autoSave) "Saving clips into Movies/${_appSettings.value.albumName}/$batchName/..." else "Finalizing clips...",
                    batchName = batchName
                )
                for ((idx, clip) in generatedClips.withIndex()) {
                    if (autoSave) {
                        val file = File(clip.filePath)
                        val galleryUri = galleryService.saveVideoToGallery(
                            sourceFile = file,
                            displayName = clip.clipFileName.ifEmpty { file.name },
                            albumName = _appSettings.value.albumName,
                            subfolder = batchName
                        )
                        val updated = clip.copy(
                            galleryUriString = galleryUri?.toString(),
                            isSavedToGallery = galleryUri != null
                        )
                        if (galleryUri != null) savedCount++
                        finalClips.add(updated)
                    } else {
                        finalClips.add(clip)
                    }
                    val saveProg = 0.90f + 0.10f * ((idx + 1).toFloat() / generatedClips.size)
                    _processingState.value = ProcessingUiState.InProgress(
                        phase = ProcessingPhase.SAVING_GALLERY,
                        progress = saveProg,
                        totalClips = totalClips,
                        statusMessage = "Saved ${idx + 1} of ${generatedClips.size} clips to Gallery ($batchName)",
                        batchName = batchName
                    )
                }
                delay(300)
                _processingState.value = ProcessingUiState.Completed(
                    clips = finalClips,
                    savedCount = if (autoSave) savedCount else finalClips.size,
                    durationSeconds = meta.durationMs / 1000,
                    batchName = batchName
                )
            } catch (e: Exception) {
                e.printStackTrace()
                _processingState.value = ProcessingUiState.Error("Processing failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun cancelProcessing() {
        processingJob?.cancel()
        _processingState.value = ProcessingUiState.Idle
    }

    fun resetToMainScreen() {
        processingJob?.cancel()
        _processingState.value = ProcessingUiState.Idle
    }

    fun openPlayerModal(clip: ClipSegment) {
        _activePlayerClip.value = clip
    }

    fun closePlayerModal() {
        _activePlayerClip.value = null
    }

    fun openGallery() {
        galleryService.openGallery()
    }

    fun toggleSettingsDialog(show: Boolean) {
        _showSettingsDialog.value = show
    }

    fun updateSettings(
        quality: OutputQuality = _appSettings.value.quality,
        autoSave: Boolean = _appSettings.value.autoSaveGallery,
        captions: Boolean = _appSettings.value.captionsEnabled
    ) {
        _appSettings.value = _appSettings.value.copy(
            quality = quality,
            autoSaveGallery = autoSave,
            captionsEnabled = captions
        )
    }
}
