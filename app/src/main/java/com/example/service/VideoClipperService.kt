package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.example.model.ClipSegment
import com.example.model.EditingStyle
import com.example.model.OutputQuality
import com.example.model.StyleAssets
import com.example.model.VideoFramingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoClipperService(private val context: Context) {
    private val bannerHelper = BannerAssetHelper(context)
    private val compositeRenderer = VideoCompositeRenderer()

    suspend fun prepareLocalSourceFile(sourceUri: Uri): File = withContext(Dispatchers.IO) {
        if (sourceUri.scheme == "file" && sourceUri.path != null) {
            val file = File(sourceUri.path!!)
            if (file.exists()) return@withContext file
        }
        val cacheFile = File(context.cacheDir, "source_input_${System.currentTimeMillis()}.mp4")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        }
        cacheFile
    }

    suspend fun clipSegment(
        sourceFile: File,
        segmentIndex: Int,
        startMs: Long,
        endMs: Long,
        style: EditingStyle,
        outputDir: File,
        styleAssets: StyleAssets? = null,
        quality: OutputQuality = OutputQuality.HIGH,
        batchName: String = "Batch_001"
    ): ClipSegment = withContext(Dispatchers.IO) {
        // Enforce sequential naming: Clip_001.mp4, Clip_002.mp4, ...
        val clipFileName = "Clip_%03d.mp4".format(segmentIndex)
        val outputFile = File(outputDir, clipFileName)
        val topPath = styleAssets?.topImagePath
        val bottomPath = styleAssets?.effectiveBottomPath
        val tagText = if (styleAssets?.showCenterTag == true) styleAssets.centerTagText else null
        val framing = styleAssets?.framingMode ?: VideoFramingMode.EXPANDED_4_3

        var renderSuccess = false
        var failureCause: Throwable? = null

        // Render full 9:16 composite MP4
        try {
            renderSuccess = compositeRenderer.renderCompositeClip(
                context = context,
                sourceFile = sourceFile,
                outputFile = outputFile,
                startMs = startMs,
                endMs = endMs,
                styleAssets = styleAssets,
                outWidth = quality.width,
                outHeight = quality.height,
                bitRate = quality.bitRate
            )
        } catch (e: Exception) {
            Log.e("VideoClipperService", "Composite rendering failed: ${e.message}", e)
            failureCause = e
            renderSuccess = false
        }

        // Verification
        if (!renderSuccess || !outputFile.exists() || outputFile.length() == 0L) {
            val errMsg = "Failed to render composite MP4 for clip #$segmentIndex [${style.displayName}]: ${failureCause?.localizedMessage ?: "Unknown encoder error"}"
            Log.e("VideoClipperService", errMsg)
            throw IllegalStateException(errMsg, failureCause)
        }

        // Extract frame bitmap for thumbnail
        val frameBitmap = extractFrameBitmap(sourceFile, startMs + 1000L)

        // Generate matching thumbnail
        val thumbFileName = "thumb_Clip_%03d.jpg".format(segmentIndex)
        val thumbFile = File(outputDir, thumbFileName)
        if (style.requiresImages && topPath != null) {
            bannerHelper.createComposite916Thumbnail(
                videoFrameBitmap = frameBitmap,
                topImagePath = topPath,
                bottomImagePath = bottomPath,
                centerTagText = tagText,
                outputFile = thumbFile,
                framingMode = framing
            )
        } else {
            if (frameBitmap != null) {
                FileOutputStream(thumbFile).use { out ->
                    frameBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        }
        frameBitmap?.recycle()

        val sizeFormatted = if (outputFile.exists()) {
            val mb = outputFile.length() / (1024.0 * 1024.0)
            if (mb >= 1.0) "%.1f MB".format(mb) else "%.1f KB".format(outputFile.length() / 1024.0)
        } else "0 MB"

        ClipSegment(
            id = "${batchName}_Clip_%03d_${System.currentTimeMillis()}".format(segmentIndex),
            segmentIndex = segmentIndex,
            startMs = startMs,
            endMs = endMs,
            style = style,
            title = "$batchName / $clipFileName",
            filePath = outputFile.absolutePath,
            thumbnailPath = if (thumbFile.exists()) thumbFile.absolutePath else null,
            topImagePath = topPath,
            bottomImagePath = bottomPath,
            centerTagText = tagText,
            framingMode = framing,
            isSavedToGallery = false,
            fileSizeFormatted = sizeFormatted,
            batchName = batchName,
            clipFileName = clipFileName
        )
    }

    private fun extractFrameBitmap(sourceFile: File, timestampMs: Long): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(sourceFile.absolutePath)
            val targetUs = timestampMs * 1000L
            retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.getFrameAtTime(0L)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }
}
