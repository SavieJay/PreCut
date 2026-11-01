package com.savie.precut.ui.editor.broll

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.model.broll.GalleryItem
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.editor.BrollUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

/** What the B-roll sheet can do. */
interface BrollActions {
    fun onAccessResult(granted: Boolean)
    fun onQuery(query: String)
    fun onPick(item: GalleryItem)
    fun onDismiss()
}

/** The permissions Smart B-roll asks for: photos and videos, with Android 14's partial access. */
fun galleryPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

/** Whether the app may read the gallery now (all of it, or the photos picked on Android 14). */
fun hasGalleryAccess(context: Context): Boolean =
    galleryPermissions().any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

/** Smart B-roll (Phase 26B): access, then looking through the gallery, then search and pick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrollSheet(ui: BrollUi, actions: BrollActions) {
    val access = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result -> actions.onAccessResult(result.values.any { it }) }
    ModalBottomSheet(
        onDismissRequest = actions::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.broll_title), style = MaterialTheme.typography.titleMedium)
            when (ui) {
                BrollUi.NeedsAccess -> {
                    Text(stringResource(R.string.broll_access_why), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    FilledButton(stringResource(R.string.broll_allow), { access.launch(galleryPermissions()) }, Modifier.fillMaxWidth(), large = true)
                }
                is BrollUi.Indexing -> {
                    Text(
                        if (ui.total == 0) stringResource(R.string.broll_indexing_start) else pluralStringResource(R.plurals.broll_indexing, ui.total, ui.done, ui.total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                    LinearProgressIndicator(
                        progress = { if (ui.total == 0) 0f else ui.done.toFloat() / ui.total },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = Accent, trackColor = Ink3, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {},
                    )
                }
                is BrollUi.Ready -> ReadyContent(ui, actions)
            }
        }
    }
}

@Composable
private fun ReadyContent(ui: BrollUi.Ready, actions: BrollActions) {
    OutlinedTextField(
        value = ui.query,
        onValueChange = actions::onQuery,
        placeholder = { Text(stringResource(R.string.broll_hint)) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Outline,
            focusedContainerColor = Ink0, unfocusedContainerColor = Ink0, cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    when {
        ui.items.isEmpty() -> Text(stringResource(R.string.broll_empty), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        ui.query.isNotBlank() && ui.results.isEmpty() -> Text(stringResource(R.string.broll_no_match), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        else -> Text(pluralStringResource(R.plurals.broll_looked_at, ui.items.size, ui.items.size), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
    }
    LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(320.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(ui.results, key = { it.id }) { item ->
            AsyncImage(
                model = item.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { actions.onPick(item) },
            )
        }
    }
}
