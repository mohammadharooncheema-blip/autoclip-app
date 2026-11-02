package com.example.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.sin

class SampleVideoGenerator(private val context: Context) {
    suspend fun getOrCreateSampleVideo(): File = withContext(Dispatchers.IO) {
        val sampleFile = File(context.filesDir, "autoclip_demo_sample_16x9.mp4")
        if (sampleFile.exists() && sampleFile.length() > 50_000L) {
            return@withContext sampleFile
        }
        try {
            generateSyntheticVideo(sampleFile, durationSeconds = 65, width = 1280, height = 720)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        sampleFile
    }

    private fun generateSyntheticVideo(
        outputFile: File,
        durationSeconds: Int = 65,
        width: Int = 1280,
        height: Int = 720
    ) {
        val mimeType = "video/avc"
        val fps = 30
        val totalFrames = durationSeconds * fps
        val bitRate = 3_000_000
        val format = MediaFormat.createVideoFormat(mimeType, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second keyframe interval
        }
        val codec = MediaCodec.createEncoderByType(mimeType)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = codec.createInputSurface()
        codec.start()

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8") // Sky cyan
            textSize = 58f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            style = Paint.Style.FILL
        }

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F43F5E") // Coral
            style = Paint.Style.FILL
        }

        try {
            for (frame in 0 until totalFrames) {
                val currentSeconds = frame / fps
                val currentSecRemainder = (frame % fps)

                val canvas: Canvas = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    inputSurface.lockHardwareCanvas()
                } else {
                    inputSurface.lockCanvas(null)
                }

                // Render dynamic frame visuals
                val hueShift = (frame * 1.5f) % 360f
                val topColor = Color.HSVToColor(floatArrayOf(hueShift, 0.75f, 0.35f))
                val bottomColor = Color.HSVToColor(floatArrayOf((hueShift + 60f) % 360f, 0.85f, 0.15f))
                val gradient = LinearGradient(0f, 0f, 0f, height.toFloat(), topColor, bottomColor, Shader.TileMode.CLAMP)
                val bgPaint = Paint().apply { shader = gradient }
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

                // Animated pulse orb
                val orbRadius = 120f + (sin(frame * 0.1) * 35f).toFloat()
                val orbY = height * 0.38f + (sin(frame * 0.05) * 80f).toFloat()
                val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.HSVToColor(floatArrayOf((hueShift + 180f) % 360f, 0.9f, 0.95f))
                    alpha = 180
                }
                canvas.drawCircle(width / 2f, orbY, orbRadius, orbPaint)

                // Header badge
                val badgeRect = RectF(width * 0.15f, 100f, width * 0.85f, 210f)
                canvas.drawRoundRect(badgeRect, 28f, 28f, badgePaint)
                canvas.drawText("AutoClip Demo Source", width / 2f, 168f, titlePaint)

                // Subtitle topic
                val sectionIndex = (currentSeconds / 15) % 4
                val topics = listOf(
                    "HIGH ENERGY ACTION",
                    "CLUTCH MOMENT PLAY",
                    "VIRAL REACTION CLIP",
                    "PODCAST HIGHLIGHTS"
                )
                textPaint.textSize = 36f
                textPaint.color = Color.parseColor("#FDE047") // Neon yellow
                canvas.drawText(topics[sectionIndex], width / 2f, height * 0.58f, textPaint)

                // Live timer readout
                val minStr = "%02d".format(currentSeconds / 60)
                val secStr = "%02d".format(currentSeconds % 60)
                val msStr = "%02d".format((currentSecRemainder * 100) / fps)
                textPaint.textSize = 68f
                textPaint.color = Color.WHITE
                canvas.drawText("$minStr:$secStr.$msStr", width / 2f, height * 0.67f, textPaint)

                // Animated progress bar at bottom
                val progressRatio = frame.toFloat() / totalFrames
                val barRect = RectF(60f, height - 160f, 60f + (width - 120f) * progressRatio, height - 130f)
                canvas.drawRoundRect(barRect, 15f, 15f, accentPaint)

                textPaint.textSize = 28f
                textPaint.color = Color.parseColor("#94A3B8")
                canvas.drawText("Total Duration: ${durationSeconds}s | Ready for 20s/25s/30s Cuts", width / 2f, height - 80f, textPaint)

                inputSurface.unlockCanvasAndPost(canvas)

                // Drain encoder outputs
                drainEncoder(codec, muxer, bufferInfo, false) { newTrackIndex ->
                    trackIndex = newTrackIndex
                    muxerStarted = true
                }
            }

            codec.signalEndOfInputStream()
            drainEncoder(codec, muxer, bufferInfo, true) { newTrackIndex ->
                trackIndex = newTrackIndex
                muxerStarted = true
            }
        } finally {
            try {
                codec.stop()
                codec.release()
            } catch (_: Exception) {}
            try {
                if (muxerStarted) {
                    muxer.stop()
                }
                muxer.release()
            } catch (_: Exception) {}
            try {
                inputSurface.release()
            } catch (_: Exception) {}
        }
    }

    private fun drainEncoder(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        endOfStream: Boolean,
        onMuxerStart: (Int) -> Unit
    ) {
        val timeoutUs = 10_000L
        while (true) {
            val status = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val newFormat = codec.outputFormat
                val trackIndex = muxer.addTrack(newFormat)
                muxer.start()
                onMuxerStart(trackIndex)
            } else if (status >= 0) {
                val outputBuffer = codec.getOutputBuffer(status)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                    try {
                        muxer.writeSampleData(0, outputBuffer, bufferInfo)
                    } catch (_: Exception) {}
                }
                codec.releaseOutputBuffer(status, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }
        }
    }
}
