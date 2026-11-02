package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.model.ClipSegment
import com.example.model.VideoFramingMode
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlayerModal(
    clip: ClipSegment,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(clip.durationSeconds * 1000) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }

    val isRenderedClip = !clip.id.startsWith("live_preview_")

    LaunchedEffect(mediaPlayerRef, isPlaying) {
        while (true) {
            mediaPlayerRef?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        currentPositionMs = mp.currentPosition
                        if (mp.duration > 0) durationMs = mp.duration
                    }
                } catch (_: Exception) {}
            }
            delay(200)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("video_player_modal"),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF070B14),
            border = BorderStroke(1.5.dp, clip.style.accentColor.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = clip.style.accentColor,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (isRenderedClip) "RENDERED 9:16 MP4" else clip.style.displayName.uppercase(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1080 x 1920 (9:16)",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Text(
                            text = clip.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("close_player_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Complete 9:16 Vertical Player Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                ) {
                    if (isRenderedClip) {
                        // Rendered MP4 Video
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    mediaPlayerRef?.let { mp ->
                                        try {
                                            if (mp.isPlaying) {
                                                mp.pause()
                                                isPlaying = false
                                            } else {
                                                mp.start()
                                                isPlaying = true
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                        ) {
                            if (clip.thumbnailPath != null && File(clip.thumbnailPath).exists()) {
                                AsyncImage(
                                    model = File(clip.thumbnailPath),
                                    contentDescription = "Rendered Video Thumbnail",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            AndroidView(
                                factory = { ctx ->
                                    TextureView(ctx).apply {
                                        textureViewRef = this
                                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                            override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                                val surface = Surface(surfaceTexture)
                                                try {
                                                    val mp = MediaPlayer().apply {
                                                        setSurface(surface)
                                                        val videoFile = File(clip.filePath)
                                                        setDataSource(ctx, Uri.fromFile(videoFile))
                                                        isLooping = true
                                                        setOnPreparedListener { player ->
                                                            player.start()
                                                            isPlaying = true
                                                            if (duration > 0) durationMs = duration
                                                        }
                                                        prepareAsync()
                                                    }
                                                    mediaPlayerRef = mp
                                                } catch (e: Exception) {
                                                    e.printStackTrace()
                                                }
                                            }
                                            override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                                            override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                                try {
                                                    mediaPlayerRef?.release()
                                                    mediaPlayerRef = null
                                                } catch (_: Exception) {}
                                                return true
                                            }
                                            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        // Unrendered live preview modal: Top Image + Center Video + Bottom Image
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top Image Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clipToBounds()
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (clip.topImagePath != null && File(clip.topImagePath).exists()) {
                                    AsyncImage(
                                        model = File(clip.topImagePath),
                                        contentDescription = "Top Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("Top Banner", color = Color.Gray, fontSize = 12.sp)
                                }
                            }

                            // Center Video Section
                            val aspect = clip.framingMode.aspectRatio
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(aspect)
                                    .clipToBounds()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        TextureView(ctx).apply {
                                            val tv = this
                                            textureViewRef = this
                                            fun doTransform(vW: Int, vH: Int) {
                                                val w = if (tv.width > 0) tv.width else width
                                                val h = if (tv.height > 0) tv.height else height
                                                if (w > 0 && h > 0 && vW > 0 && vH > 0) {
                                                    applyCenterCropTransform(tv, w, h, vW, vH)
                                                }
                                            }
                                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                                                    val surface = Surface(surfaceTexture)
                                                    try {
                                                        val mp = MediaPlayer().apply {
                                                            setSurface(surface)
                                                            val videoFile = File(clip.filePath)
                                                            setDataSource(ctx, Uri.fromFile(videoFile))
                                                            isLooping = true
                                                            setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                                                if (vWidth > 0 && vHeight > 0) {
                                                                    surfaceTexture.setDefaultBufferSize(vWidth, vHeight)
                                                                    doTransform(vWidth, vHeight)
                                                                }
                                                            }
                                                            setOnPreparedListener { player ->
                                                                player.start()
                                                                isPlaying = true
                                                                if (duration > 0) durationMs = duration
                                                            }
                                                            prepareAsync()
                                                        }
                                                        mediaPlayerRef = mp
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }
                                                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {}
                                                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                                                    try {
                                                        mediaPlayerRef?.release()
                                                        mediaPlayerRef = null
                                                    } catch (_: Exception) {}
                                                    return true
                                                }
                                                override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Center Tag Badge
                                if (!clip.centerTagText.isNullOrBlank()) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 8.dp),
                                        color = Color.Black.copy(alpha = 0.85f),
                                        border = BorderStroke(1.2.dp, EmeraldAccent),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = clip.centerTagText,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldAccent,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // Bottom Image Section
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clipToBounds()
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (clip.bottomImagePath != null && File(clip.bottomImagePath).exists()) {
                                    AsyncImage(
                                        model = File(clip.bottomImagePath),
                                        contentDescription = "Bottom Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Text("Bottom Banner", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                // Playback Controls Row
                Column(modifier = Modifier.fillMaxWidth()) {
                    val progressRatio = if (durationMs > 0) {
                        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                    } else 0f
                    Slider(
                        value = progressRatio,
                        onValueChange = { newRatio ->
                            mediaPlayerRef?.let { mp ->
                                try {
                                    val target = (newRatio * durationMs).toInt()
                                    mp.seekTo(target)
                                    currentPositionMs = target
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(22.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = clip.style.accentColor,
                            activeTrackColor = clip.style.accentColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "%02d:%02d".format((currentPositionMs / 1000) / 60, (currentPositionMs / 1000) % 60),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "%02d:%02d".format((durationMs / 1000) / 60, (durationMs / 1000) % 60),
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                mediaPlayerRef?.let { mp ->
                                    try {
                                        if (mp.isPlaying) {
                                            mp.pause()
                                            isPlaying = false
                                        } else {
                                            mp.start()
                                            isPlaying = true
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(clip.style.accentColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                mediaPlayerRef?.let { mp ->
                                    try {
                                        mp.seekTo(0)
                                        mp.start()
                                        isPlaying = true
                                    } catch (_: Exception) {}
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Replay",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Button(
                            onClick = { shareClipModal(context, clip) },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CyanPrimary,
                                contentColor = Color(0xFF0F172A)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SHARE CLIP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
                mediaPlayerRef = null
            } catch (_: Exception) {}
        }
    }
}

private fun applyCenterCropTransform(textureView: TextureView, viewWidth: Int, viewHeight: Int, videoWidth: Int, videoHeight: Int) {
    if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return
    val viewAspect = viewWidth.toFloat() / viewHeight.toFloat()
    val videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
    val scaleX: Float
    val scaleY: Float
    if (videoAspect > viewAspect) {
        scaleX = videoAspect / viewAspect
        scaleY = 1.0f
    } else {
        scaleX = 1.0f
        scaleY = viewAspect / videoAspect
    }
    val matrix = Matrix()
    val pivotX = viewWidth / 2f
    val pivotY = viewHeight / 2f
    matrix.setScale(scaleX, scaleY, pivotX, pivotY)
    textureView.setTransform(matrix)
}

private fun shareClipModal(context: Context, clip: ClipSegment) {
    val file = File(clip.filePath)
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${clip.title}"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
