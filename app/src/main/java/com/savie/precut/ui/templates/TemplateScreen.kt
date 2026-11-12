package com.savie.precut.ui.templates

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.ObserveAsEvents
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutTopBar
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/**
 * A template (plain Base 0: its preview video plays here): its preview playing, Use template (pick
 * media → a new project in the editor) and Delete (an ErrorRed confirmation).
 */
@Composable
fun TemplateScreen(onBack: () -> Unit, onOpenEditor: (projectId: String) -> Unit, viewModel: TemplateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    var confirmDelete by remember { mutableStateOf(false) }
    val pick = rememberTemplatePicker(state.template?.slotCount ?: 1, viewModel::onUse)
    ObserveAsEvents(viewModel.events) { event ->
        val message = when (event) {
            is TemplateEvent.OpenEditor -> return@ObserveAsEvents onOpenEditor(event.projectId)
            TemplateEvent.Deleted -> return@ObserveAsEvents onBack()
            TemplateEvent.NothingImported -> resources.getString(R.string.home_import_nothing)
            is TemplateEvent.SomeFilesSkipped -> resources.getQuantityString(R.plurals.home_import_skipped, event.count, event.count)
            is TemplateEvent.ExtraPicksIgnored -> resources.getQuantityString(R.plurals.template_extra_ignored, event.count, event.count)
            is TemplateEvent.SomeClipsShort -> resources.getQuantityString(R.plurals.template_clips_short, event.count, event.count)
            TemplateEvent.SongUnreadable -> resources.getString(R.string.autocut_music_unreadable)
            TemplateEvent.NoBeats -> resources.getString(R.string.template_no_beats)
            is TemplateEvent.ShareLink -> {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, event.url)
                context.startActivity(Intent.createChooser(send, resources.getString(R.string.template_share)))
                return@ObserveAsEvents
            }
            TemplateEvent.NotShareable -> resources.getString(R.string.template_not_shareable)
            is TemplateEvent.ShareFailed -> resources.getString(R.string.template_share_failed)
        }
        // A toast outlives the move to the editor that follows.
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    Column(Modifier.fillMaxSize().background(Ink0).statusBarsPadding().navigationBarsPadding()) {
        PreCutTopBar(title = state.template?.template?.title.orEmpty(), onBack = onBack) {
            if (state.template != null) {
                PreCutIconButton(R.drawable.ic_share, stringResource(R.string.template_share), viewModel::onShare, enabled = !state.sharing)
                PreCutIconButton(
                    if (state.favourite) R.drawable.ic_star_filled else R.drawable.ic_star,
                    stringResource(if (state.favourite) R.string.template_unfavourite else R.string.template_favourite),
                    viewModel::onToggleFavourite,
                    tint = if (state.favourite) Accent else TextPrimary,
                )
            }
        }
        val stored = state.template
        Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            TemplatePreview(stored, state.missing)
        }
        if (stored != null) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    pluralStringResource(R.plurals.template_details, stored.slotCount, stored.slotCount, formatDurationMs(stored.durationMs), stringResource(stored.template.category.label)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                FilledButton(
                    stringResource(if (state.busy) R.string.template_using else R.string.template_use),
                    onClick = { pick(false) },
                    enabled = !state.busy,
                    large = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                TonalButton(stringResource(R.string.template_use_with_song), onClick = { pick(true) }, modifier = Modifier.fillMaxWidth(), icon = R.drawable.ic_beats)
                if (!stored.builtIn) {
                    AccentTextButton(stringResource(R.string.action_delete), onClick = { confirmDelete = true }, color = TextSecondary, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
    if (confirmDelete) {
        GlassDialog(title = stringResource(R.string.template_delete_title), onDismiss = { confirmDelete = false }) {
            Text(stringResource(R.string.template_delete_body), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
            FilledButton(
                stringResource(R.string.action_delete),
                onClick = { confirmDelete = false; viewModel.onDelete() },
                icon = R.drawable.ic_delete,
                large = true,
                destructive = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
