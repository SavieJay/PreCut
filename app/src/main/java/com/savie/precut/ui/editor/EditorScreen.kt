package com.savie.precut.ui.editor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.components.projectDisplayName
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.formatSeconds
import com.savie.precut.ui.components.ObserveAsEvents
import com.savie.precut.ui.editor.crop.CropTopBar
import com.savie.precut.ui.editor.preview.FullScreenPreview
import com.savie.precut.ui.editor.template.SaveTemplateSheet
import com.savie.precut.ui.components.rememberCameraLaunchers
import kotlinx.coroutines.launch
import com.savie.precut.ui.editor.captions.CaptionsHost
import com.savie.precut.ui.editor.captions.TranslateSheet
import com.savie.precut.ui.editor.aiedit.AiEditHost
import com.savie.precut.ui.editor.coach.CoachMarksOverlay
import com.savie.precut.ui.editor.coach.HookCoachSheet
import com.savie.precut.ui.editor.export.FormatsSheet
import com.savie.precut.ui.editor.export.ShortsActions
import com.savie.precut.ui.editor.export.ShortsSheet
import com.savie.precut.ui.editor.coach.CoachTargets
import com.savie.precut.ui.editor.coach.LocalCoachTargets
import com.savie.precut.ui.editor.export.EditorExportHost
import com.savie.precut.ui.editor.export.rememberExportNavigator

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onExport: (projectId: String, settings: String, kind: String) -> Unit,
    /** Opens the paywall for a Pro feature (its `ProFeature` name). */
    onOpenPaywall: (feature: String) -> Unit = {},
    /** Opens another project (a short made from this one). */
    onOpenProject: (projectId: String) -> Unit = {},
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Kept as a State and read only where needed: the playhead changes every frame while
    // playing or scrubbing, and the whole editor must not recompose with it.
    val previewState = viewModel.previewState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val canSplit by viewModel.canSplit.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Save as template's sheet; it closes when the template is saved (an event).
    var templateSheet by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onScreenStopped() }

    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia()) { uris ->
        viewModel.onMediaPicked(uris.map(Uri::toString))
    }
    // OpenDocument (not GetContent) so the music Uri can be kept across restarts.
    val musicPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.audio.onMusicPicked(uri?.toString())
    }
    val replacementPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.onReplacementPicked(uri?.toString())
    }
    val coverPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.cover.onImagePicked(uri?.toString())
    }
    val canvasPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.frame.onCanvasImagePicked(uri?.toString())
    }
    val overlayPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        viewModel.overlay.onPicked(uri?.toString())
    }
    val camera = rememberCameraLaunchers(viewModel::onCaptureResult)
    // `.cube` files have no standard MIME type, so any file can be picked and is validated.
    val fontPicker = rememberLauncherForActivityResult(OpenDocument()) { uri -> viewModel.textStyle.onFontPicked(uri?.toString()) }
    val stickerPicker = rememberLauncherForActivityResult(OpenDocument()) { uri -> viewModel.stickers.onImagePicked(uri?.toString()) }
    val subtitlesPicker = rememberLauncherForActivityResult(CreateDocument(SRT_MIME_TYPE)) { uri -> viewModel.captionEdits.onSrtTarget(uri?.toString()) }
    val lutPicker = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        viewModel.look.onImportLut(uri?.toString())
    }
    val micPermission = rememberLauncherForActivityResult(RequestPermission()) { granted ->
        if (granted) viewModel.audio.onRecordStart(hasPermission = true)
    }
    val exportNavigator = rememberExportNavigator { onExport(viewModel.projectId, it.settings, it.kind) }
    ObserveAsEvents(viewModel.events) { event ->
        val message = when (event) {
            is EditorEvent.NavigateExport -> {
                exportNavigator.navigate(event)
                return@ObserveAsEvents
            }
            EditorEvent.FrameSaved -> resources.getString(R.string.export_frame_saved)
            EditorEvent.FrameNotSaved -> resources.getString(R.string.export_frame_failed)
            is EditorEvent.SomeFilesSkipped ->
                resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count)
            EditorEvent.NothingImported -> resources.getString(R.string.home_import_nothing)
            EditorEvent.PickFont -> {
                // Font files rarely have a dependable MIME type; any file is checked on import.
                fontPicker.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/octet-stream", "*/*"))
                return@ObserveAsEvents
            }
            EditorEvent.FontNotUsable -> resources.getString(R.string.text_font_import_failed)
            EditorEvent.SpeechFailed -> resources.getString(R.string.text_tts_failed)
            EditorEvent.PickStickerImage -> {
                stickerPicker.launch(arrayOf("image/png", "image/webp", "image/gif", "image/jpeg"))
                return@ObserveAsEvents
            }
            EditorEvent.StickerNotUsable -> resources.getString(R.string.sticker_import_failed)
            EditorEvent.StickerImageRemoved -> resources.getString(R.string.sticker_removed)
            EditorEvent.DrawingNotSaved -> resources.getString(R.string.draw_failed)
            EditorEvent.SpeedNotForPhotos -> resources.getString(R.string.speed_not_for_photos)
            EditorEvent.FreezeNotForPhotos -> resources.getString(R.string.freeze_not_for_photos)
            EditorEvent.FreezeFailed -> resources.getString(R.string.freeze_failed)
            EditorEvent.ReverseNotForPhotos -> resources.getString(R.string.reverse_not_for_photos)
            EditorEvent.ReverseFailed -> resources.getString(R.string.reverse_failed)
            EditorEvent.ReverseUnsupported -> resources.getString(R.string.reverse_unsupported)
            EditorEvent.CutoutNoPerson -> resources.getString(R.string.cutout_no_person)
            EditorEvent.CutoutFailed -> resources.getString(R.string.cutout_failed)
            EditorEvent.ReframeNoPerson -> resources.getString(R.string.reframe_no_person)
            EditorEvent.ReframeNoneFound -> resources.getString(R.string.reframe_none_found)
            EditorEvent.ReframeNothingToMove -> resources.getString(R.string.reframe_nothing_to_move)
            EditorEvent.ReframeFailed -> resources.getString(R.string.reframe_failed)
            EditorEvent.NoiseNoSound -> resources.getString(R.string.noise_no_sound)
            EditorEvent.NoiseFailed -> resources.getString(R.string.noise_failed)
            EditorEvent.ReframeDone, is EditorEvent.ReframedAll -> {
                val text = if (event is EditorEvent.ReframedAll) {
                    resources.getQuantityString(R.plurals.reframe_done_all, event.count, event.count)
                } else {
                    resources.getString(R.string.reframe_done)
                }
                scope.launch {
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            EditorEvent.TemplateSaved -> {
                templateSheet = false
                resources.getString(R.string.template_saved)
            }
            EditorEvent.TemplateSaveFailed -> resources.getString(R.string.template_save_failed)
            EditorEvent.BeatsUnreadable -> resources.getString(R.string.beats_unreadable)
            EditorEvent.NoBeatsFound -> resources.getString(R.string.beats_none_found)
            EditorEvent.NothingCutToBeats -> resources.getString(R.string.beats_nothing_to_cut)
            is EditorEvent.CutToBeats -> {
                val text = if (event.leftAlone == 0) {
                    resources.getQuantityString(R.plurals.beats_cut_done, event.cut, event.cut)
                } else {
                    resources.getQuantityString(R.plurals.beats_cut_done_some_left, event.leftAlone, event.cut, event.leftAlone)
                }
                scope.launch {
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            EditorEvent.FollowNoPerson -> resources.getString(R.string.follow_no_person)
            EditorEvent.BrandKitEmpty -> resources.getString(R.string.brand_empty)
            EditorEvent.FrozeFrame, EditorEvent.ClipReversed, EditorEvent.ClipUnreversed, EditorEvent.CutoutDone, EditorEvent.FollowDone, EditorEvent.BrandApplied -> {
                val text = when (event) {
                    EditorEvent.BrandApplied -> R.string.brand_applied
                    EditorEvent.FollowDone -> R.string.follow_done
                    EditorEvent.FrozeFrame -> R.string.freeze_added
                    EditorEvent.ClipReversed -> R.string.reverse_done
                    EditorEvent.CutoutDone -> R.string.cutout_done
                    else -> R.string.reverse_removed
                }
                scope.launch {
                    val result = snackbarHostState.showSnackbar(resources.getString(text), actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            EditorEvent.ShortsTooShort -> resources.getString(R.string.shorts_too_short)
            EditorEvent.ShortsNoneFound -> resources.getString(R.string.shorts_none)
            is EditorEvent.OpenProject -> {
                onOpenProject(event.projectId)
                return@ObserveAsEvents
            }
            is EditorEvent.ExportProject -> {
                onExport(event.projectId, event.settings, "VIDEO")
                return@ObserveAsEvents
            }
            is EditorEvent.OpenPaywall -> {
                onOpenPaywall(event.feature.name)
                return@ObserveAsEvents
            }
            is EditorEvent.Translated -> {
                scope.launch {
                    val text = when {
                        event.noVoice -> resources.getString(R.string.translate_done_no_voice)
                        event.dubbed > 0 -> resources.getQuantityString(R.plurals.translate_done_dubbed, event.dubbed, event.dubbed)
                        else -> resources.getString(R.string.translate_done)
                    }
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            is EditorEvent.TranscriptCut -> {
                scope.launch {
                    val text = resources.getString(R.string.transcript_cut_done, formatSeconds(event.removedMs))
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            is EditorEvent.AiEditApplied -> {
                scope.launch {
                    val text = resources.getQuantityString(R.plurals.ai_edit_applied, event.count, event.count)
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            is EditorEvent.CaptionsAdded, EditorEvent.CaptionRegenerated -> {
                scope.launch {
                    val text = if (event is EditorEvent.CaptionsAdded) {
                        resources.getQuantityString(R.plurals.captions_added, event.count, event.count)
                    } else {
                        resources.getString(R.string.captions_regenerated)
                    }
                    val result = snackbarHostState.showSnackbar(text, actionLabel = resources.getString(R.string.action_undo))
                    if (result == SnackbarResult.ActionPerformed) viewModel.onUndo()
                }
                return@ObserveAsEvents
            }
            is EditorEvent.PickSubtitlesTarget -> {
                subtitlesPicker.launch(event.fileName)
                return@ObserveAsEvents
            }
            is EditorEvent.ShareSubtitles -> {
                shareSubtitles(context, event.uri, resources.getString(R.string.captions_share_srt))
                return@ObserveAsEvents
            }
            EditorEvent.SubtitlesSaved -> resources.getString(R.string.captions_srt_saved)
            EditorEvent.SubtitlesNotSaved -> resources.getString(R.string.captions_srt_failed)
            EditorEvent.MusicImportFailed -> resources.getString(R.string.audio_import_failed)
            EditorEvent.RecordingFailed -> resources.getString(R.string.voiceover_failed)
            EditorEvent.NothingToExtract -> resources.getString(R.string.audio_nothing_to_extract)
            EditorEvent.PickReplacement -> {
                replacementPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                return@ObserveAsEvents
            }
            EditorEvent.LutImportFailed -> resources.getString(R.string.filter_lut_import_failed)
            EditorEvent.PickLut -> {
                lutPicker.launch(arrayOf("*/*"))
                return@ObserveAsEvents
            }
            EditorEvent.PickCoverImage -> {
                coverPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                return@ObserveAsEvents
            }
            EditorEvent.KeepOneMainClip -> resources.getString(R.string.overlay_keep_main)
            EditorEvent.PickOverlay -> {
                overlayPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo))
                return@ObserveAsEvents
            }
            EditorEvent.PickCanvasImage -> {
                canvasPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                return@ObserveAsEvents
            }
            is EditorEvent.LaunchCamera -> {
                camera.launch(event.cameraUri, event.video)
                return@ObserveAsEvents
            }
            EditorEvent.RequestMicPermission -> {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                return@ObserveAsEvents
            }
            EditorEvent.PickMusic -> {
                musicPicker.launch(arrayOf("audio/*"))
                return@ObserveAsEvents
            }
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }
    val timelineActions = rememberTimelineActions(viewModel)
    var renaming by rememberSaveable { mutableStateOf(false) }
    val topBarActions = remember(viewModel) {
        object : EditorTopBarActions {
            override fun onBack() = onBack()
            override fun onStartRename() {
                renaming = true
            }
            override fun onRename(name: String) {
                viewModel.onRename(name)
                renaming = false
            }
            override fun onCancelRename() {
                renaming = false
            }
            override fun onUndo() {
                viewModel.onUndo()
            }
            override fun onRedo() {
                viewModel.onRedo()
            }
            override fun onExport() = viewModel.export.onOpen()
            override fun onSaveAsTemplate() {
                templateSheet = true
            }
        }
    }
    BackHandler(enabled = renaming) { renaming = false }

    val coachTargets = remember { CoachTargets() }
    val settingsValues by viewModel.appSettingsForUi.values.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCoachTargets provides coachTargets) {
            Scaffold(
                topBar = {
                    val ready = uiState as? EditorUiState.Ready
                    if (ready?.cropDraft != null) {
                        CropTopBar(onCancel = viewModel.frame::onCropCancel, onApply = viewModel.frame::onCropApply)
                    } else {
                        EditorTopBar(
                            title = ready?.project?.let { projectDisplayName(it.name, it.createdAt) }.orEmpty(),
                            canExport = ready != null && ready.project.clips.isNotEmpty() && ready.missingIds.isEmpty(),
                            canUndo = ready?.canUndo == true,
                            canRedo = ready?.canRedo == true,
                            renaming = renaming && ready != null,
                            actions = topBarActions,
                        )
                    }
                },
                snackbarHost = {
                    // Above the toolbar, which reaches down behind the navigation bar.
                    SnackbarHost(snackbarHostState, Modifier.navigationBarsPadding().padding(bottom = 72.dp))
                },
                // The top bar pads for the status bar and the bottom panel for the navigation bar.
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { innerPadding ->
                val modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                when (val state = uiState) {
                    EditorUiState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    EditorUiState.NotFound -> Box(modifier, contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.editor_not_found))
                    }
                    is EditorUiState.Ready -> {
                        EditorContent(
                            state = state,
                            dimmed = renaming,
                            previewState = previewState,
                            canSplit = canSplit,
                            viewModel = viewModel,
                            timelineActions = timelineActions,
                            onAddMedia = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageAndVideo)) },
                            modifier = modifier,
                        )
                        EditorExportHost(state, viewModel, exportNavigator, viewModel.appSettingsForUi)
                        CaptionsHost(viewModel.captions)
                        AiEditHost(viewModel.aiEdit)
                        val hook by viewModel.hookCoach.state.collectAsStateWithLifecycle()
                        hook?.let { HookCoachSheet(it, viewModel.hookCoach::onApply, viewModel.hookCoach::onDismiss) }
                        val translating by viewModel.translate.state.collectAsStateWithLifecycle()
                        translating?.let {
                            TranslateSheet(it, viewModel.translate::onLanguage, viewModel.translate::onDub, viewModel.translate::onTranslate, viewModel.translate::onDismiss)
                        }
                        val shorts by viewModel.shorts.state.collectAsStateWithLifecycle()
                        val shortsActions = remember(viewModel) {
                            object : ShortsActions {
                                override fun onOpen(projectId: String) = viewModel.shorts.onOpen(projectId)
                                override fun onExport(projectId: String) = viewModel.shorts.onExport(projectId)
                                override fun onExportAll() = viewModel.shorts.onExportAll()
                                override fun onDismiss() = viewModel.shorts.onDismiss()
                            }
                        }
                        shorts?.let { ShortsSheet(it, shortsActions) }
                        val formats by viewModel.formats.state.collectAsStateWithLifecycle()
                        formats?.let { FormatsSheet(it, viewModel.formats::onToggle, viewModel.formats::onExport, viewModel.formats::onDismiss) }
                    }
                }
            }
        }
        // First-run tips (Phase 24), once a project with clips is open.
        val ready = uiState as? EditorUiState.Ready
        if (!settingsValues.coachMarksShown && ready != null && ready.project.clips.isNotEmpty() && !ready.fullScreen) {
            CoachMarksOverlay(coachTargets, onDone = { viewModel.appSettingsForUi.update { it.copy(coachMarksShown = true) } })
        }
        // Drawn over the whole screen, top bar included.
        (uiState as? EditorUiState.Ready)?.takeIf { it.fullScreen && it.project.clips.isNotEmpty() }?.let {
            FullScreenPreview(
                player = player,
                previewState = previewState,
                onPlayPause = viewModel::onPlayPause,
                onScrubStart = viewModel::onScrubStart,
                onScrubEnd = viewModel::onScrubEnd,
                onSeek = viewModel::onSeek,
                onClose = { viewModel.onFullScreen(false) },
                onSurfaceReady = viewModel::onFullScreenSurfaceReady,
            )
        }
        (uiState as? EditorUiState.Ready)?.takeIf { templateSheet }?.let { ready ->
            SaveTemplateSheet(
                project = ready.project,
                thumbnails = viewModel.thumbnails,
                saving = viewModel.savingTemplate.collectAsStateWithLifecycle().value,
                onSave = viewModel::onSaveAsTemplate,
                onDismiss = { templateSheet = false },
            )
        }
    }
}

/** SubRip subtitles' MIME type. */
private const val SRT_MIME_TYPE = "application/x-subrip"

/** Offers the SRT file at [uri] (the app's FileProvider) to other apps. */
private fun shareSubtitles(context: Context, uri: String, title: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(SRT_MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, Uri.parse(uri))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, title))
}
