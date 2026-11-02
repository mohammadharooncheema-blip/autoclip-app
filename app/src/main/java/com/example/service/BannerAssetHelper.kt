package com.example.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import com.example.model.VideoFramingMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class BannerAssetHelper(private val context: Context) {
    suspend fun getOrCreateDefaultBanner(): File = withContext(Dispatchers.IO) {
        val bannerFile = File(context.filesDir, "default_gaming_banner.png")
        if (bannerFile.exists() && bannerFile.length() > 5000) {
            return@withContext bannerFile
        }

        val width = 1280
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val gradient = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(
                Color.parseColor("#0F172A"),
                Color.parseColor("#0284C7"),
                Color.parseColor("#38BDF8"),
                Color.parseColor("#E0F2FE")
            ),
            floatArrayOf(0f, 0.35f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        val bgPaint = Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val streakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 40
            strokeWidth = 14f
        }
        for (i in 0..12) {
            val startX = i * 140f
            canvas.drawLine(startX, 0f, startX + 400f, height.toFloat(), streakPaint)
        }

        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BAE6FD")
            alpha = 130
        }
        canvas.drawCircle(320f, height * 0.45f, 220f, glowPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 76f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.08f
        }
        canvas.drawText("WUTHERING", width - 80f, height * 0.42f, titlePaint)
        canvas.drawText("WAVES", width - 80f, height * 0.58f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7")
            textSize = 34f
            isFakeBoldText = true
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("FEATURED SHOWCASE", width - 80f, height * 0.72f, subPaint)

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            alpha = 220
        }
        canvas.drawRoundRect(RectF(60f, 60f, 440f, 130f), 24f, 24f, pillPaint)
        val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#38BDF8")
            textSize = 32f
            isFakeBoldText = true
        }
        canvas.drawText("9:16 SHORT TEMPLATE", 80f, 108f, pillTextPaint)

        FileOutputStream(bannerFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
        bannerFile
    }

    suspend fun copyImageUriToLocalFile(uri: Uri, fileNamePrefix: String): String = withContext(Dispatchers.IO) {
        val destFile = File(context.filesDir, "${fileNamePrefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destFile).use { output ->
                input.copyTo(output)
            }
        }
        destFile.absolutePath
    }

    suspend fun createComposite916Thumbnail(
        videoFrameBitmap: Bitmap?,
        topImagePath: String?,
        bottomImagePath: String?,
        centerTagText: String?,
        outputFile: File,
        framingMode: VideoFramingMode = VideoFramingMode.EXPANDED_4_3
    ) = withContext(Dispatchers.IO) {
        val width = 720
        val height = 1280
        val composite = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)

        val videoAspect = framingMode.aspectRatio
        val centerH = (width / videoAspect).toInt().coerceIn(400, height - 120)
        val topH = (height - centerH) / 2
        val centerY = topH
        val bottomY = centerY + centerH

        // 1. Draw Top Image
        topImagePath?.let { path ->
            val topBmp = BitmapFactory.decodeFile(path)
            if (topBmp != null) {
                drawCenterCropBitmap(canvas, topBmp, Rect(0, 0, width, topH))
                topBmp.recycle()
            }
        }

        // 2. Draw Center Video Frame
        if (videoFrameBitmap != null) {
            drawCenterCropBitmap(canvas, videoFrameBitmap, Rect(0, centerY, width, bottomY))
        } else {
            val placeholderPaint = Paint().apply { color = Color.parseColor("#1E293B") }
            canvas.drawRect(0f, centerY.toFloat(), width.toFloat(), bottomY.toFloat(), placeholderPaint)
        }

        // 3. Draw Bottom Image
        bottomImagePath?.let { path ->
            val btmBmp = BitmapFactory.decodeFile(path)
            if (btmBmp != null) {
                drawCenterCropBitmap(canvas, btmBmp, Rect(0, bottomY, width, height))
                btmBmp.recycle()
            }
        }

        // 4. Draw Center Tag Badge
        if (!centerTagText.isNullOrBlank()) {
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                alpha = 230
            }
            val badgeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4ADE80") // Neon Green
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#4ADE80")
                textSize = 28f
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val badgeW = (textPaint.measureText(centerTagText) + 48f).coerceAtLeast(160f)
            val badgeH = 50f
            val badgeX = (width - badgeW) / 2f
            val badgeY = centerY + 24f
            val rect = RectF(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
            canvas.drawRoundRect(rect, 14f, 14f, badgePaint)
            canvas.drawRoundRect(rect, 14f, 14f, badgeBorder)
            canvas.drawText(centerTagText, width / 2f, badgeY + 36f, textPaint)
        }

        FileOutputStream(outputFile).use { out ->
            composite.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        composite.recycle()
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
}
