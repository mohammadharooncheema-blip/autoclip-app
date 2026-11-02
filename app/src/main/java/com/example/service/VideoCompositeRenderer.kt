package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.example.model.StyleAssets
import com.example.model.VideoFramingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware-accelerated Video Composite Renderer.
 * Permanently renders the original 9:16 vertical layout (Top Image + Center Video + Bottom Image)
 * directly into a crisp MP4 file using MediaCodec, OpenGL ES 2.0, and MediaMuxer.
 */
class VideoCompositeRenderer {
    companion object {
        private const val TAG = "VideoCompositeRenderer"
        private const val TIMEOUT_US = 10_000L
        private const val DEFAULT_FPS = 30
    }

    suspend fun renderCompositeClip(
        context: Context? = null,
        sourceFile: File,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        styleAssets: StyleAssets?,
        outWidth: Int = 1080,
        outHeight: Int = 1920,
        bitRate: Int = 8_000_000,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) {
            throw IllegalArgumentException("Source video file does not exist: ${sourceFile.absolutePath}")
        }
        if (outputFile.exists()) {
            outputFile.delete()
        }

        val videoExtractor = MediaExtractor()
        var audioExtractor: MediaExtractor? = null
        var encoder: MediaCodec? = null
        var decoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var eglCore: EglCore? = null
        var windowSurface: WindowSurface? = null
        var handlerThread: HandlerThread? = null

        try {
            videoExtractor.setDataSource(sourceFile.absolutePath)

            // Identify video and audio tracks
            var videoTrackIndex = -1
            var videoFormat: MediaFormat? = null
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/") && videoTrackIndex == -1) {
                    videoTrackIndex = i
                    videoFormat = format
                } else if (mime.startsWith("audio/") && audioTrackIndex == -1) {
                    audioTrackIndex = i
                    audioFormat = format
                }
            }

            if (videoTrackIndex == -1 || videoFormat == null) {
                throw IllegalStateException("No video track found in ${sourceFile.name}")
            }

            videoExtractor.selectTrack(videoTrackIndex)
            val srcVideoW = videoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcVideoH = videoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            val videoMime = videoFormat.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            val targetFps = if (videoFormat.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE).coerceIn(24, 60)
            } else DEFAULT_FPS

            if (audioTrackIndex != -1 && audioFormat != null) {
                audioExtractor = MediaExtractor().apply {
                    setDataSource(sourceFile.absolutePath)
                    selectTrack(audioTrackIndex)
                }
            }

            // 1. Setup MediaCodec Encoder (video/avc, 9:16 vertical outWidth x outHeight)
            val encoderFormat = MediaFormat.createVideoFormat("video/avc", outWidth, outHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, targetFps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            encoder = MediaCodec.createEncoderByType("video/avc").apply {
                configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            val encoderInputSurface = encoder.createInputSurface()
            encoder.start()

            // 2. Setup EGL on Encoder Input Surface
            eglCore = EglCore()
            windowSurface = WindowSurface(eglCore, encoderInputSurface)
            windowSurface.makeCurrent()

            // 3. Setup Texture Renderer
            val textureRenderer = TextureRenderer()

            // Layout geometry for Top Image + Center Video + Bottom Image
            val framing = styleAssets?.framingMode ?: VideoFramingMode.EXPANDED_4_3
            val centerVideoAspect = framing.aspectRatio
            val centerH = (outWidth / centerVideoAspect).toInt().coerceIn(360, outHeight - 160)
            val topH = (outHeight - centerH) / 2
            val centerY = topH
            val bottomY = centerY + centerH

            // Create Overlay Bitmap for Top Image + Bottom Image + Center Tag Badge
            val overlayBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            val overlayCanvas = Canvas(overlayBitmap)
            drawDualBannerOverlay(
                canvas = overlayCanvas,
                width = outWidth,
                height = outHeight,
                topH = topH,
                bottomY = bottomY,
                centerY = centerY,
                styleAssets = styleAssets
            )
            textureRenderer.loadOverlayTexture(overlayBitmap)

            // 4. Setup SurfaceTexture & MediaCodec Decoder
            val surfaceTexture = SurfaceTexture(textureRenderer.oesTextureId).apply {
                setDefaultBufferSize(srcVideoW, srcVideoH)
            }
            val frameSync = Object()
            var frameAvailable = false
            handlerThread = HandlerThread("GLFrameNotifyThread").apply { start() }
            val notifyHandler = Handler(handlerThread.looper)

            surfaceTexture.setOnFrameAvailableListener({
                synchronized(frameSync) {
                    frameAvailable = true
                    frameSync.notifyAll()
                }
            }, notifyHandler)

            val decoderSurface = Surface(surfaceTexture)
            decoder = MediaCodec.createDecoderByType(videoMime).apply {
                configure(videoFormat, decoderSurface, null, 0)
                start()
            }

            // 5. Setup MediaMuxer
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var muxerVideoTrack = -1
            var muxerAudioTrack = -1
            var muxerStarted = false

            val startUs = startMs * 1000L
            val endUs = endMs * 1000L
            val totalDurationUs = (endUs - startUs).coerceAtLeast(1_000_000L)

            videoExtractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            audioExtractor?.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val decoderBufferInfo = MediaCodec.BufferInfo()
            val encoderBufferInfo = MediaCodec.BufferInfo()
            val stMatrix = FloatArray(16)
            val audioBuffer = ByteBuffer.allocateDirect(1024 * 512)

            var extractorDone = false
            var decoderDone = false
            var encoderDone = false
            var frameCount = 0L

            // 6. Transcoding & Frame Composite Loop
            while (!encoderDone) {
                if (!extractorDone) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = videoExtractor.readSampleData(inputBuffer, 0)
                        val sampleTimeUs = videoExtractor.sampleTime
                        if (sampleSize < 0 || sampleTimeUs > endUs) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                            videoExtractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIndex = decoder.dequeueOutputBuffer(decoderBufferInfo, TIMEOUT_US)
                    if (outIndex >= 0) {
                        if (decoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            decoderDone = true
                            encoder.signalEndOfInputStream()
                        }

                        val sampleTimeUs = decoderBufferInfo.presentationTimeUs
                        val renderThisFrame = sampleTimeUs in startUs..endUs && !decoderDone
                        decoder.releaseOutputBuffer(outIndex, renderThisFrame)

                        if (renderThisFrame) {
                            synchronized(frameSync) {
                                while (!frameAvailable) {
                                    frameSync.wait(300)
                                }
                                frameAvailable = false
                            }

                            surfaceTexture.updateTexImage()
                            surfaceTexture.getTransformMatrix(stMatrix)

                            // Composite Top Image + Center Video + Bottom Image + Center Tag
                            textureRenderer.drawDualBannerCompositeFrame(
                                stMatrix = stMatrix,
                                srcVideoW = srcVideoW,
                                srcVideoH = srcVideoH,
                                outW = outWidth,
                                outH = outHeight,
                                centerY = centerY,
                                centerH = centerH
                            )

                            val ptsNs = ((sampleTimeUs - startUs).coerceAtLeast(0L)) * 1000L
                            windowSurface.setPresentationTime(ptsNs)
                            windowSurface.swapBuffers()
                            frameCount++

                            val prog = ((sampleTimeUs - startUs).toFloat() / totalDurationUs).coerceIn(0f, 1f)
                            onProgress?.invoke(prog)
                        }
                    }
                }

                while (true) {
                    val encIndex = encoder.dequeueOutputBuffer(encoderBufferInfo, TIMEOUT_US)
                    if (encIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!muxerStarted) {
                            muxerVideoTrack = muxer.addTrack(encoder.outputFormat)
                            if (audioExtractor != null && audioFormat != null) {
                                muxerAudioTrack = muxer.addTrack(audioFormat)
                            }
                            muxer.start()
                            muxerStarted = true
                        }
                    } else if (encIndex >= 0) {
                        val encodedBuffer = encoder.getOutputBuffer(encIndex)!!
                        if (encoderBufferInfo.size > 0 && muxerStarted) {
                            encodedBuffer.position(encoderBufferInfo.offset)
                            encodedBuffer.limit(encoderBufferInfo.offset + encoderBufferInfo.size)
                            muxer.writeSampleData(muxerVideoTrack, encodedBuffer, encoderBufferInfo)
                        }
                        encoder.releaseOutputBuffer(encIndex, false)
                        if (encoderBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            encoderDone = true
                            break
                        }
                    }
                }
            }

            // Mux Audio Track (Preserves original gameplay audio)
            if (muxerStarted && audioExtractor != null && muxerAudioTrack >= 0) {
                val audioBufferInfo = MediaCodec.BufferInfo()
                while (true) {
                    val sampleTimeUs = audioExtractor.sampleTime
                    if (sampleTimeUs < 0 || sampleTimeUs > endUs) break
                    if (sampleTimeUs >= startUs) {
                        val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                        if (sampleSize > 0) {
                            audioBufferInfo.offset = 0
                            audioBufferInfo.size = sampleSize
                            audioBufferInfo.presentationTimeUs = (sampleTimeUs - startUs).coerceAtLeast(0L)
                            audioBufferInfo.flags = audioExtractor.sampleFlags
                            muxer.writeSampleData(muxerAudioTrack, audioBuffer, audioBufferInfo)
                        }
                    }
                    if (!audioExtractor.advance()) break
                }
            }

            overlayBitmap.recycle()
            Log.i(TAG, "Successfully rendered 9:16 Dual Banner MP4: ${outputFile.name} ($frameCount frames)")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering 9:16 Dual Banner clip: ${e.message}", e)
            throw e
        } finally {
            try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
            try { decoder?.stop(); decoder?.release() } catch (_: Exception) {}
            try { if (muxer != null) { muxer.stop(); muxer.release() } } catch (_: Exception) {}
            try { videoExtractor.release() } catch (_: Exception) {}
            try { audioExtractor?.release() } catch (_: Exception) {}
            try { windowSurface?.release() } catch (_: Exception) {}
            try { eglCore?.release() } catch (_: Exception) {}
            try { handlerThread?.quitSafely() } catch (_: Exception) {}
        }
    }

    private fun drawDualBannerOverlay(
        canvas: Canvas,
        width: Int,
        height: Int,
        topH: Int,
        bottomY: Int,
        centerY: Int,
        styleAssets: StyleAssets?
    ) {
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

        // 1. Draw Top Image
        val topPath = styleAssets?.topImagePath
        if (topPath != null && File(topPath).exists()) {
            val topBmp = BitmapFactory.decodeFile(topPath)
            if (topBmp != null) {
                drawCenterCropBitmap(canvas, topBmp, Rect(0, 0, width, topH))
                topBmp.recycle()
            }
        } else {
            val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
            canvas.drawRect(0f, 0f, width.toFloat(), topH.toFloat(), bgPaint)
        }

        // 2. Draw Bottom Image
        val bottomPath = styleAssets?.effectiveBottomPath
        if (bottomPath != null && File(bottomPath).exists()) {
            val btmBmp = BitmapFactory.decodeFile(bottomPath)
            if (btmBmp != null) {
                drawCenterCropBitmap(canvas, btmBmp, Rect(0, bottomY, width, height))
                btmBmp.recycle()
            }
        } else {
            val bgPaint = Paint().apply { color = Color.parseColor("#0F172A") }
            canvas.drawRect(0f, bottomY.toFloat(), width.toFloat(), height.toFloat(), bgPaint)
        }

        // Subtle divider accents between video and images
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#38BDF8")
            strokeWidth = 3f
            alpha = 140
        }
        canvas.drawLine(0f, topH.toFloat(), width.toFloat(), topH.toFloat(), dividerPaint)
        canvas.drawLine(0f, bottomY.toFloat(), width.toFloat(), bottomY.toFloat(), dividerPaint)

        // 3. Draw Center Tag Badge
        val tagText = if (styleAssets?.showCenterTag == true) styleAssets.centerTagText else null
        if (!tagText.isNullOrBlank()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = 230
            }
            val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4ADE80") // Neon Green
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4ADE80")
                textSize = 34f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val badgeW = (textPaint.measureText(tagText) + 54f).coerceAtLeast(180f)
            val badgeH = 58f
            val badgeX = (width - badgeW) / 2f
            val badgeY = centerY + 24f
            val rect = RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
            canvas.drawRoundRect(rect, 16f, 16f, badgePaint)
            canvas.drawRoundRect(rect, 16f, 16f, badgeBorder)
            canvas.drawText(tagText, width / 2f, badgeY + 41f, textPaint)
        }
    }

    private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap, destRect: Rect) {
        val srcW = bitmap.width.toFloat()
        val srcH = bitmap.height.toFloat()
        val dstW = (destRect.right - destRect.left).toFloat()
        val dstH = (destRect.bottom - destRect.top).toFloat()
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return

        val scale = maxOf(dstW / srcW, dstH / srcH)
        val srcCropW = dstW / scale
        val srcCropH = dstH / scale
        val srcX = (srcW - srcCropW) / 2f
        val srcY = (srcH - srcCropH) / 2f

        val srcRect = Rect(
            srcX.toInt().coerceAtLeast(0),
            srcY.toInt().coerceAtLeast(0),
            (srcX + srcCropW).toInt().coerceAtMost(bitmap.width),
            (srcY + srcCropH).toInt().coerceAtMost(bitmap.height)
        )
        canvas.drawBitmap(bitmap, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    // --- EGL Core ---
    private class EglCore {
        val eglDisplay: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val eglConfig: EGLConfig
        val eglContext: EGLContext

        init {
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
                throw RuntimeException("unable to initialize EGL14")
            }

            val attribList = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0) || numConfigs[0] == 0) {
                throw RuntimeException("unable to find suitable EGLConfig")
            }
            eglConfig = configs[0]!!

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            if (eglContext == EGL14.EGL_NO_CONTEXT) {
                throw RuntimeException("failed to create EGL context")
            }
        }

        fun release() {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eglDisplay)
            }
        }
    }

    // --- WindowSurface ---
    private class WindowSurface(private val eglCore: EglCore, surface: Surface) {
        val eglSurface: EGLSurface

        init {
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eglSurface = EGL14.eglCreateWindowSurface(eglCore.eglDisplay, eglCore.eglConfig, surface, surfaceAttribs, 0)
            if (eglSurface == EGL14.EGL_NO_SURFACE) {
                throw RuntimeException("surface was null")
            }
        }

        fun makeCurrent() {
            if (!EGL14.eglMakeCurrent(eglCore.eglDisplay, eglSurface, eglSurface, eglCore.eglContext)) {
                throw RuntimeException("eglMakeCurrent failed")
            }
        }

        fun swapBuffers(): Boolean {
            return EGL14.eglSwapBuffers(eglCore.eglDisplay, eglSurface)
        }

        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eglCore.eglDisplay, eglSurface, nsecs)
        }

        fun release() {
            EGL14.eglDestroySurface(eglCore.eglDisplay, eglSurface)
        }
    }

    // --- Texture Renderer ---
    private class TextureRenderer {
        val oesTextureId: Int
        var overlayTextureId: Int = 0

        private val program2D: Int
        private val aPos2D: Int
        private val aTex2D: Int
        private val uTex2D: Int

        private val programOES: Int
        private val aPosOES: Int
        private val aTexOES: Int
        private val uSTMatrixOES: Int
        private val uTexOES: Int

        private val fullScreenBuf: FloatBuffer
        private val fullTexBuf: FloatBuffer

        init {
            fullScreenBuf = createFloatBuffer(floatArrayOf(
                -1.0f,  1.0f, // Top-Left
                -1.0f, -1.0f, // Bottom-Left
                 1.0f,  1.0f, // Top-Right
                 1.0f, -1.0f  // Bottom-Right
            ))
            fullTexBuf = createFloatBuffer(floatArrayOf(
                0.0f, 0.0f, // Top-Left
                0.0f, 1.0f, // Bottom-Left
                1.0f, 0.0f, // Top-Right
                1.0f, 1.0f  // Bottom-Right
            ))

            program2D = createProgram(VERTEX_SHADER_2D, FRAGMENT_SHADER_2D)
            aPos2D = GLES20.glGetAttribLocation(program2D, "aPosition")
            aTex2D = GLES20.glGetAttribLocation(program2D, "aTexCoord")
            uTex2D = GLES20.glGetUniformLocation(program2D, "uTexture")

            programOES = createProgram(VERTEX_SHADER_OES, FRAGMENT_SHADER_OES)
            aPosOES = GLES20.glGetAttribLocation(programOES, "aPosition")
            aTexOES = GLES20.glGetAttribLocation(programOES, "aTexCoord")
            uSTMatrixOES = GLES20.glGetUniformLocation(programOES, "uSTMatrix")
            uTexOES = GLES20.glGetUniformLocation(programOES, "uTexture")

            val texIds = IntArray(1)
            GLES20.glGenTextures(1, texIds, 0)
            oesTextureId = texIds[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }

        fun loadOverlayTexture(bitmap: Bitmap) {
            if (overlayTextureId == 0) {
                val texIds = IntArray(1)
                GLES20.glGenTextures(1, texIds, 0)
                overlayTextureId = texIds[0]
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        }

        fun drawDualBannerCompositeFrame(
            stMatrix: FloatArray,
            srcVideoW: Int,
            srcVideoH: Int,
            outW: Int,
            outH: Int,
            centerY: Int,
            centerH: Int
        ) {
            GLES20.glViewport(0, 0, outW, outH)
            GLES20.glClearColor(0.06f, 0.09f, 0.16f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // Center Video positioning in NDC [-1, 1]
            val bottomY = centerY + centerH
            val ndcLeft = -1.0f
            val ndcRight = 1.0f
            val ndcTop = 1.0f - 2.0f * (centerY.toFloat() / outH.toFloat())
            val ndcBottom = 1.0f - 2.0f * (bottomY.toFloat() / outH.toFloat())

            val centerPosBuf = createFloatBuffer(floatArrayOf(
                ndcLeft,  ndcTop,
                ndcLeft,  ndcBottom,
                ndcRight, ndcTop,
                ndcRight, ndcBottom
            ))

            // Aspect ratio preservation with center crop to center slot
            val srcAspect = srcVideoW.toFloat() / srcVideoH.toFloat()
            val targetAspect = outW.toFloat() / centerH.toFloat()
            val cUMin: Float
            val cUMax: Float
            val cVMin: Float
            val cVMax: Float

            if (srcAspect > targetAspect) {
                val scale = targetAspect / srcAspect
                cUMin = (1.0f - scale) / 2.0f
                cUMax = 1.0f - cUMin
                cVMin = 0.0f
                cVMax = 1.0f
            } else {
                val scale = srcAspect / targetAspect
                cUMin = 0.0f
                cUMax = 1.0f
                cVMin = (1.0f - scale) / 2.0f
                cVMax = 1.0f - cVMin
            }

            val centerTexBuf = createFloatBuffer(floatArrayOf(
                cUMin, cVMax,
                cUMin, cVMin,
                cUMax, cVMax,
                cUMax, cVMin
            ))

            // Draw center video frame
            GLES20.glUseProgram(programOES)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(uTexOES, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixOES, 1, false, stMatrix, 0)
            GLES20.glEnableVertexAttribArray(aPosOES)
            GLES20.glVertexAttribPointer(aPosOES, 2, GLES20.GL_FLOAT, false, 8, centerPosBuf)
            GLES20.glEnableVertexAttribArray(aTexOES)
            GLES20.glVertexAttribPointer(aTexOES, 2, GLES20.GL_FLOAT, false, 8, centerTexBuf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPosOES)
            GLES20.glDisableVertexAttribArray(aTexOES)

            // Blend Top & Bottom Image + Center Tag overlay texture
            if (overlayTextureId > 0) {
                GLES20.glEnable(GLES20.GL_BLEND)
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
                GLES20.glUseProgram(program2D)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
                GLES20.glUniform1i(uTex2D, 0)
                GLES20.glEnableVertexAttribArray(aPos2D)
                GLES20.glVertexAttribPointer(aPos2D, 2, GLES20.GL_FLOAT, false, 8, fullScreenBuf)
                GLES20.glEnableVertexAttribArray(aTex2D)
                GLES20.glVertexAttribPointer(aTex2D, 2, GLES20.GL_FLOAT, false, 8, fullTexBuf)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                GLES20.glDisableVertexAttribArray(aPos2D)
                GLES20.glDisableVertexAttribArray(aTex2D)
                GLES20.glDisable(GLES20.GL_BLEND)
            }
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            val program = GLES20.glCreateProgram()
            if (program == 0) throw RuntimeException("Could not create GL program")
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                val info = GLES20.glGetProgramInfoLog(program)
                GLES20.glDeleteProgram(program)
                throw RuntimeException("Could not link program: $info")
            }
            return program
        }

        private fun loadShader(shaderType: Int, source: String): Int {
            val shader = GLES20.glCreateShader(shaderType)
            if (shader == 0) throw RuntimeException("Could not create shader type $shaderType")
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw RuntimeException("Could not compile shader $shaderType: $info")
            }
            return shader
        }

        companion object {
            private const val VERTEX_SHADER_2D = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = aTexCoord;
                }
            """
            private const val FRAGMENT_SHADER_2D = """
                precision mediump float;
                uniform sampler2D uTexture;
                varying vec2 vTexCoord;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """
            private const val VERTEX_SHADER_OES = """
                attribute vec4 aPosition;
                attribute vec2 aTexCoord;
                uniform mat4 uSTMatrix;
                varying vec2 vTexCoord;
                void main() {
                    gl_Position = aPosition;
                    vTexCoord = (uSTMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
                }
            """
            private const val FRAGMENT_SHADER_OES = """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                uniform samplerExternalOES uTexture;
                varying vec2 vTexCoord;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """

            fun createFloatBuffer(coords: FloatArray): FloatBuffer {
                return ByteBuffer.allocateDirect(coords.size * 4).run {
                    order(ByteOrder.nativeOrder())
                    asFloatBuffer().apply {
                        put(coords)
                        position(0)
                    }
                }
            }
        }
    }
}
