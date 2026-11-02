package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.EditingStyle
import com.example.model.ProcessingUiState
import com.example.ui.components.ClipLengthSelector
import com.example.ui.components.ProcessingView
import com.example.ui.components.ResultsView
import com.example.ui.components.SettingsDialog
import com.example.ui.components.StyleAssetInputsCard
import com.example.ui.components.StyleSelectionSection
import com.example.ui.components.VideoInputCard
import com.example.ui.components.VideoPlayerModal
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.VioletAccent
import com.example.viewmodel.AutoClipViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoClipMainScreen(
    viewModel: AutoClipViewModel,
    modifier: Modifier = Modifier
) {
    val selectedVideoUri by viewModel.selectedVideoUri.collectAsState()
    val videoMetadata by viewModel.videoMetadata.collectAsState()
    val selectedDuration by viewModel.selectedDuration.collectAsState()
    val selectedStyles by viewModel.selectedStyles.collectAsState()
    val styleAssets by viewModel.styleAssets.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val processingState by viewModel.processingState.collectAsState()
    val activePlayerClip by viewModel.activePlayerClip.collectAsState()
    val showSettingsDialog by viewModel.showSettingsDialog.collectAsState()
    val isLoadingSample by viewModel.isLoadingSample.collectAsState()
    val previewVideoFramePath by viewModel.previewVideoFramePath.collectAsState()
    val previewCompositePath by viewModel.previewCompositePath.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = processingState !is ProcessingUiState.Idle) {
        viewModel.resetToMainScreen()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(CyanPrimary, VioletAccent)),
                                    RoundedCornerShape(9.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = "AutoClip Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AutoClip",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.5.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Turn 1 video into 9:16 short clips",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleSettingsDialog(true) },
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = processingState) {
                is ProcessingUiState.InProgress -> {
                    ProcessingView(
                        state = state,
                        onCancelClicked = { viewModel.cancelProcessing() }
                    )
                }
                is ProcessingUiState.Completed -> {
                    ResultsView(
                        clips = state.clips,
                        savedCount = state.savedCount,
                        batchName = state.batchName,
                        onClipClicked = { clip -> viewModel.openPlayerModal(clip) },
                        onOpenGalleryClicked = { viewModel.openGallery() },
                        onCreateMoreClicked = { viewModel.resetToMainScreen() }
                    )
                }
                is ProcessingUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Error Occurred",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = RoseAccent
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.resetToMainScreen() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary)
                        ) {
                            Text("Try Again", color = Color.Black)
                        }
                    }
                }
                ProcessingUiState.Idle -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Section 1: Video Input
                            item(key = "video_input") {
                                VideoInputCard(
                                    videoUri = selectedVideoUri,
                                    metadata = videoMetadata,
                                    isLoadingSample = isLoadingSample,
                                    onVideoSelected = { uri -> viewModel.onVideoSelected(uri) },
                                    onTrySampleClicked = { viewModel.loadSampleVideo() }
                                )
                            }
                            // Section 2: Clip Length Selector
                            item(key = "clip_length") {
                                ClipLengthSelector(
                                    selectedDuration = selectedDuration,
                                    onDurationSelected = { dur -> viewModel.selectDuration(dur) }
                                )
                            }
                            // Section 3: Editing Styles
                            item(key = "style_selection") {
                                StyleSelectionSection(
                                    selectedStyles = selectedStyles,
                                    onToggleStyle = { style -> viewModel.toggleStyle(style) }
                                )
                            }
                            // Section 4: Style Assets & Framing Customizer
                            item(key = "style_assets") {
                                val activeStyle = selectedStyles.firstOrNull() ?: EditingStyle.DUAL_BANNER
                                StyleAssetInputsCard(
                                    activeStyle = activeStyle,
                                    styleAssets = styleAssets,
                                    previewVideoFramePath = previewVideoFramePath,
                                    previewCompositePath = previewCompositePath,
                                    onTopImageSelected = { uri -> viewModel.setTopImageUri(uri) },
                                    onBottomImageSelected = { uri -> viewModel.setBottomImageUri(uri) },
                                    onMirrorToggled = { mirror -> viewModel.setMirrorTopImage(mirror) },
                                    onTagChanged = { tag -> viewModel.setCenterTagText(tag) },
                                    onToggleShowTag = { show -> viewModel.toggleShowCenterTag(show) },
                                    onFramingModeSelected = { mode -> viewModel.setFramingMode(mode) },
                                    onStyleAssetsUpdated = { assets -> viewModel.updateStyleAssets(assets) },
                                    onPreviewPlaybackClicked = if (selectedVideoUri != null) { { viewModel.previewCurrentSetup() } } else null
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }

                        // Bottom Action Bar
                        val totalClipsToCreate = viewModel.calculatePlannedClipCount()
                        val cutsCount = viewModel.calculateSegmentCutsCount()
                        val isCreateEnabled = selectedVideoUri != null && selectedStyles.isNotEmpty()
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                            tonalElevation = 4.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startProcessing() },
                                    enabled = isCreateEnabled,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("create_clips_button"),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CyanPrimary,
                                        contentColor = Color(0xFF0F172A),
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        disabledContentColor = Color.Gray
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (totalClipsToCreate > 0) {
                                            "CREATE $totalClipsToCreate CLIPS ($cutsCount cuts)"
                                        } else {
                                            "CREATE CLIPS"
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                if (!isCreateEnabled) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (selectedVideoUri == null) "Select or try a sample video to begin" else "Ready to create clips",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Video Player Modal
            activePlayerClip?.let { clip ->
                VideoPlayerModal(
                    clip = clip,
                    onDismiss = { viewModel.closePlayerModal() }
                )
            }

            // Settings Dialog
            if (showSettingsDialog) {
                SettingsDialog(
                    settings = appSettings,
                    onSettingsChanged = { newSettings ->
                        viewModel.updateSettings(
                            quality = newSettings.quality,
                            autoSave = newSettings.autoSaveGallery,
                            captions = newSettings.captionsEnabled
                        )
                    },
                    onDismiss = { viewModel.toggleSettingsDialog(false) }
                )
            }
        }
    }
}
