package com.savie.precut.ui.editor.captions

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.text.CaptionStyle
import com.savie.precut.ui.components.AiTag
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.editor.CAPTION_LANGUAGES
import com.savie.precut.ui.editor.CaptionsUi
import com.savie.precut.ui.theme.GlassSolid
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

/** What the Captions sheet can do. */
interface CaptionsSheetActions {
    fun onLanguage(language: String?)
    fun onStyle(style: CaptionStyle)
    fun onGenerate()
    fun onDismiss()
}

/** Captions (Phase 21A): language, style, what is listened to, the minutes it needs, then Generate. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionsSheet(options: CaptionsUi.Options, actions: CaptionsSheetActions) {
    ModalBottomSheet(
        onDismissRequest = actions::onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = GlassSolid,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.captions_title), style = PreCutType.panelTitle, color = TextPrimary)
                AiTag(stringResource(R.string.rd_media_cloud_tag))
            }
            Section(R.string.captions_language) {
                CAPTION_LANGUAGES.forEach { code ->
                    PreCutChip(stringResource(languageName(code)), selected = options.language == code, onClick = { actions.onLanguage(code) })
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel(R.string.captions_style)
                CaptionStyleTiles(options.style, actions::onStyle)
            }
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = TextMuted, modifier = Modifier.padding(top = 1.dp).size(16.dp))
                Text(stringResource(R.string.captions_sound), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            CaptionsMinutes(options)
            if (options.replacing > 0) {
                Text(
                    pluralStringResource(R.plurals.captions_replacing, options.replacing, options.replacing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            FilledButton(
                stringResource(R.string.captions_generate),
                actions::onGenerate,
                Modifier.fillMaxWidth(),
                icon = R.drawable.ic_closed_caption,
                large = true,
                enabled = options.canGenerate,
            )
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(title)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/** A section label (the design's caps label): 12 sp, muted. */
@Composable
private fun SectionLabel(@StringRes title: Int) {
    Text(stringResource(title).uppercase(), style = PreCutType.section, color = TextMuted)
}

@StringRes
private fun languageName(code: String?): Int = when (code) {
    "en" -> R.string.captions_language_en
    "yo" -> R.string.captions_language_yo
    "ig" -> R.string.captions_language_ig
    "ha" -> R.string.captions_language_ha
    "fr" -> R.string.captions_language_fr
    "ar" -> R.string.captions_language_ar
    "pt" -> R.string.captions_language_pt
    "es" -> R.string.captions_language_es
    else -> R.string.captions_language_auto
}

@StringRes
internal fun CaptionStyle.label(): Int = when (this) {
    CaptionStyle.CLASSIC -> R.string.captions_style_classic
    CaptionStyle.BOX -> R.string.captions_style_box
    CaptionStyle.BOLD -> R.string.captions_style_bold
    CaptionStyle.HIGHLIGHT -> R.string.captions_style_highlight
    CaptionStyle.POP -> R.string.captions_style_pop
    CaptionStyle.MINIMAL -> R.string.captions_style_minimal
}
