package com.example.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import androidx.compose.ui.platform.LocalContext
import com.example.model.EditingStyle
import com.example.model.StyleAssets
import com.example.model.VideoFramingMode
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldAccent
import java.io.File

@Composable
fun StyleAssetInputsCard(
    activeStyle: EditingStyle,
    styleAssets: StyleAssets,
    previewVideoFramePath: String? = null,
    onTopImageSelected: (Uri) -> Unit,
    onBottomImageSelected: (Uri) -> Unit,
    onMirrorToggled: (Boolean) -> Unit,
    onTagChanged: (String) -> Unit,
    onToggleShowTag: (Boolean) -> Unit,
    onFramingModeSelected: (VideoFramingMode) -> Unit,
    onStyleAssetsUpdated: (StyleAssets) -> Unit,
    onPreviewPlaybackClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val topPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) onTopImageSelected(uri)
    }

    val bottomPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) onBottomImageSelected(uri)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .testTag("style_assets_setup_card"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.5.dp, activeStyle.accentColor.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        tint = activeStyle.accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STYLE ASSETS & FRAMING",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = Color.White
                    )
                }
                Surface(
                    color = activeStyle.accentColor.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, activeStyle.accentColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "9:16 SPLIT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = activeStyle.accentColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Places your custom image at the top, plays your video in the center, and displays your image at the bottom.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Main Row: Live 9:16 Preview Canvas + Image Pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live 9:16 Preview Canvas
                Live916PreviewCanvas(
                    styleAssets = styleAssets,
                    previewVideoFramePath = previewVideoFramePath,
                    activeStyle = activeStyle,
                    onPreviewPlaybackClicked = onPreviewPlaybackClicked,
                    modifier = Modifier.width(130.dp)
                )

                // Controls Summary & Quick Actions
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Pick Top Image Button
                    OutlinedButton(
                        onClick = {
                            topPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("pick_top_image_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, activeStyle.accentColor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = activeStyle.accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (styleAssets.topImageUri != null) "Change Top Image" else "+ Pick Top Image",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Mirror Top Image Switch Row
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Black.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = CyanPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column {
                                    Text(
                                        text = "Mirror to Bottom",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (styleAssets.mirrorTopImage) "Same image on both" else "Different images",
                                        fontSize = 8.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            Switch(
                                checked = styleAssets.mirrorTopImage,
                                onCheckedChange = onMirrorToggled,
                                modifier = Modifier.testTag("mirror_image_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CyanPrimary
                                )
                            )
                        }
                    }

                    // If not mirrored, allow picking separate bottom image
                    if (!styleAssets.mirrorTopImage) {
                        OutlinedButton(
                            onClick = {
                                bottomPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .testTag("pick_bottom_image_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EmeraldAccent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (styleAssets.bottomImageUri != null) "Change Bottom Image" else "+ Pick Bottom Image",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Preview Button
                    if (onPreviewPlaybackClicked != null) {
                        Button(
                            onClick = onPreviewPlaybackClicked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .testTag("preview_player_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeStyle.accentColor.copy(alpha = 0.25f),
                                contentColor = activeStyle.accentColor
                            ),
                            border = BorderStroke(1.dp, activeStyle.accentColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview in 9:16 Player", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Section: Video Framing Mode
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ViewAgenda,
                    contentDescription = null,
                    tint = CyanPrimary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "CENTER VIDEO FRAMING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoFramingMode.entries.forEach { mode ->
                    val isSelected = styleAssets.framingMode == mode
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onFramingModeSelected(mode) }
                            .testTag("framing_${mode.name}"),
                        color = if (isSelected) CyanPrimary.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.35f),
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) CyanPrimary else Color.White.copy(alpha = 0.15f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = mode.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) CyanPrimary else Color.White
                            )
                            Text(
                                text = mode.subtitle.split(" (")[0],
                                fontSize = 9.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))

        }
    }
}

@Composable
private fun Live916PreviewCanvas(
    styleAssets: StyleAssets,
    previewVideoFramePath: String?,
    activeStyle: EditingStyle,
    onPreviewPlaybackClicked: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val framing = styleAssets.framingMode
    val topPath = styleAssets.topImagePath
    val bottomPath = styleAssets.effectiveBottomPath

    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black)
            .border(1.5.dp, activeStyle.accentColor, RoundedCornerShape(14.dp))
            .clickable(enabled = onPreviewPlaybackClicked != null) {
                onPreviewPlaybackClicked?.invoke()
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. Top Image Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (topPath != null && File(topPath).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(topPath))
                            .size(Size(720, 720))
                            .crossfade(false)
                            .build(),
                        contentDescription = "Top Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Top Image", fontSize = 10.sp, color = Color.Gray)
                }
            }

            // 2. Center Video Box with proper aspect ratio
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(framing.aspectRatio)
                    .clipToBounds()
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                if (previewVideoFramePath != null && File(previewVideoFramePath).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(previewVideoFramePath))
                            .size(Size(720, 720))
                            .crossfade(false)
                            .build(),
                        contentDescription = "Center Video",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = CyanPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Center Tag Badge Overlay
                if (styleAssets.showCenterTag && styleAssets.centerTagText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                        color = Color.Black.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, EmeraldAccent),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = styleAssets.centerTagText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // 3. Bottom Image Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
                    .background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                if (bottomPath != null && File(bottomPath).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(bottomPath))
                            .size(Size(720, 720))
                            .crossfade(false)
                            .build(),
                        contentDescription = "Bottom Banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text("Bottom Image", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}
