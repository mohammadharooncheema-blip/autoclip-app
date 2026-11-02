package com.example.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.model.AppSettings
import com.example.model.ClipSegment
import com.example.model.EditingStyle
import com.example.model.ProcessingPhase
import com.example.model.ProcessingUiState
import com.example.model.StyleAssets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-scope clipping engine.
 *
 * This deliberately does NOT live in a ViewModel: a ViewModel is cleared
 * when its Activity is destroyed, which is exactly what happens when the
 * user leaves the app while a long render is running. This engine runs on
 * its own application scope, and [ClipProcessingService] keeps it alive as
 * a foreground service, so clipping keeps going in the background and the
 * user can come back to the same progress screen.
 *
 * Save-as-you-go: every clip is saved to the Gallery immediately after it
 * finishes rendering, instead of waiting until all clips are done. If the
 * app is killed halfway, the clips already rendered are already saved.
 */
object ProcessingEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Idle)
    val state: StateFlow<ProcessingUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun isRunning(): Boolean = job?.isActive == true

    fun start(
        appContext: Context,
        sourceUri: Uri,
        meta: VideoMetadata,
        duration: com.example.model.ClipDuration,
        styles: List<EditingStyle>,
        assets: StyleAssets,
        settings: AppSettings
    ) {
        if (job?.isActive == true) return
        val ctx = appContext.applicationContext
        try {
            val intent = Intent(ctx, ClipProcessingService::class.java).apply {
                action = ClipProcessingService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        } catch (_: Exception) {
        }

        job = scope.launch {
            try {
                val analyzer = VideoAnalyzerService(ctx)
                val clipper = VideoClipperService(ctx)
                val gallery = GalleryService(ctx)
                val bannerHelper = BannerAssetHelper(ctx)
                val batchManager = BatchManager(ctx)

                val batchName = batchManager.createNextBatch(settings.albumName)
                val batchDir = File(File(ctx.filesDir, "clips"), batchName)
                if (!batchDir.exists()) batchDir.mkdirs()

                var currentAssets = assets
                if (currentAssets.topImagePath == null) {
                    val defaultBanner = bannerHelper.getOrCreateDefaultBanner()
                    currentAssets = currentAssets.copy(
                        topImagePath = defaultBanner.absolutePath,
                        bottomImagePath = defaultBanner.absolutePath
                    )
                }

                _state.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.ANALYZING,
                    progress = 0.08f,
                    statusMessage = "Analyzing ${meta.title} (${meta.resolutionFormatted}, ${meta.durationFormatted})",
                    batchName = batchName
                )
                delay(600)

                val localSource = clipper.prepareLocalSourceFile(sourceUri)
                val cuts = analyzer.planCuts(meta.durationMs, duration)
                val totalClips = cuts.size * styles.size
                _state.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.FINDING_CLIPS,
                    progress = 0.22f,
                    totalClips = totalClips,
                    statusMessage = "Found ${cuts.size} natural segments for $batchName (Clip_001 to Clip_%03d)".format(cuts.size),
                    batchName = batchName
                )
                delay(700)

                _state.value = ProcessingUiState.InProgress(
                    phase = ProcessingPhase.APPLYING_STYLES,
                    progress = 0.35f,
                    totalClips = totalClips,
                    statusMessage = "Applying 9:16 Top & Bottom Image template to $batchName",
                    batchName = batchName
                )
                delay(600)

                val finalClips = mutableListOf<ClipSegment>()
                var savedCount = 0
                var clipCounter = 0
                val autoSave = settings.autoSaveGallery
                for (cut in cuts) {
                    for (style in styles) {
                        clipCounter++
                        val baseProg = 0.35f + 0.60f * (clipCounter.toFloat() / totalClips)
                        _state.value = ProcessingUiState.InProgress(
                            phase = ProcessingPhase.RENDERING,
                            progress = baseProg,
                            currentClipIndex = clipCounter,
                            totalClips = totalClips,
                            currentStyleName = style.displayName,
                            statusMessage = "Rendering $batchName / Clip_%03d.mp4 ($clipCounter of $totalClips)".format(cut.index),
                            batchName = batchName
                        )
                        val segment = clipper.clipSegment(
                            sourceFile = localSource,
                            segmentIndex = cut.index,
                            startMs = cut.startMs,
                            endMs = cut.endMs,
                            style = style,
                            outputDir = batchDir,
                            styleAssets = currentAssets,
                            quality = settings.quality,
                            batchName = batchName
                        )
                        // SAVE AS YOU GO: do not wait for all clips.
                        // This clip is saved to the Gallery right now, so
                        // it is safe even if the rest of the batch is
                        // cancelled or the app is killed later.
                        if (autoSave) {
                            val file = File(segment.filePath)
                            val galleryUri = gallery.saveVideoToGallery(
                                sourceFile = file,
                                displayName = segment.clipFileName.ifEmpty { file.name },
                                albumName = settings.albumName,
                                subfolder = batchName
                            )
                            val updated = segment.copy(
                                galleryUriString = galleryUri?.toString(),
                                isSavedToGallery = galleryUri != null
                            )
                            if (galleryUri != null) savedCount++
                            finalClips.add(updated)
                            _state.value = ProcessingUiState.InProgress(
                                phase = ProcessingPhase.SAVING_GALLERY,
                                progress = baseProg,
                                currentClipIndex = clipCounter,
                                totalClips = totalClips,
                                currentStyleName = style.displayName,
                                statusMessage = if (galleryUri != null) {
                                    "Saved Clip_%03d to Gallery ($savedCount of $totalClips saved)".format(cut.index)
                                } else {
                                    "Clip_%03d rendered, Gallery save failed, continuing".format(cut.index)
                                },
                                batchName = batchName
                            )
                        } else {
                            finalClips.add(segment)
                        }
                    }
                }
                delay(300)
                _state.value = ProcessingUiState.Completed(
                    clips = finalClips,
                    savedCount = if (autoSave) savedCount else finalClips.size,
                    durationSeconds = meta.durationMs / 1000,
                    batchName = batchName
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = ProcessingUiState.Idle
                } else {
                    e.printStackTrace()
                    _state.value = ProcessingUiState.Error("Processing failed: ${e.localizedMessage ?: "Unknown error"}")
                }
            } finally {
                stopService(ctx)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.value = ProcessingUiState.Idle
    }

    fun resetToIdle() {
        if (job?.isActive != true) {
            _state.value = ProcessingUiState.Idle
        }
    }

    private fun stopService(ctx: Context) {
        try {
            ctx.stopService(Intent(ctx, ClipProcessingService::class.java))
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
