package com.savie.precut.ui.export

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R

@Composable
fun ExportScreen(
    onClose: () -> Unit,
    onHome: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.export_share_title)

    // Android 8–9 need storage permission to write to the shared Movies folder.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.start() else viewModel.onPermissionDenied()
    }
    val startExport = {
        if (needsStoragePermission(context)) {
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            viewModel.start()
        }
    }

    LaunchedEffect(Unit) {
        if (viewModel.uiState.value == ExportUiState.Idle) startExport()
    }

    val info by viewModel.info.collectAsStateWithLifecycle()
    ExportContent(
        state = uiState,
        info = info,
        actions = object : ExportActions {
            override fun onCancel() {
                viewModel.cancel()
                onClose()
            }
            override fun onRetry() = startExport()
            override fun onOpen(uri: String) {
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri.toUri(), VIDEO_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(intent)
            }
            override fun onShare(uri: String) {
                val intent = Intent(Intent.ACTION_SEND)
                    .setType(VIDEO_MIME)
                    .putExtra(Intent.EXTRA_STREAM, uri.toUri())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(Intent.createChooser(intent, shareTitle))
            }
            override fun onBackToEdit() = onClose()
            override fun onProjects() = onHome()
        },
    )
}

private const val VIDEO_MIME = "video/mp4"

private fun needsStoragePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
        PackageManager.PERMISSION_GRANTED
