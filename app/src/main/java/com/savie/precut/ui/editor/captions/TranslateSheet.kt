package com.savie.precut.ui.editor.captions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.SwitchRow
import com.savie.precut.ui.editor.TRANSLATE_LANGUAGES
import com.savie.precut.ui.editor.TranslateUi
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink1
import com.savie.precut.ui.theme.TextSecondary

/** Translate captions (Phase 26B): the language, an optional dub with a phone voice, what's sent. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranslateSheet(ui: TranslateUi, onLanguage: (String) -> Unit, onDub: (Boolean) -> Unit, onTranslate: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink1,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.translate_title), style = MaterialTheme.typography.titleMedium)
            when (ui) {
                is TranslateUi.Working -> {
                    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally), color = Accent)
                    Text(stringResource(if (ui.dubbing) R.string.translate_working_dub else R.string.translate_working), color = TextSecondary)
                }
                is TranslateUi.Choose -> {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TRANSLATE_LANGUAGES.forEach { code ->
                            PreCutChip(stringResource(languageName(code)), selected = ui.language == code, onClick = { onLanguage(code) })
                        }
                    }
                    SwitchRow(
                        label = stringResource(R.string.translate_dub),
                        description = stringResource(R.string.translate_dub_note),
                        checked = ui.dub,
                        onCheckedChange = onDub,
                    )
                    Text(stringResource(R.string.translate_disclosure), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    ui.failure?.let { Text(aiFailureMessage(it), style = MaterialTheme.typography.bodySmall, color = Accent) }
                    FilledButton(stringResource(R.string.translate_go), onTranslate, Modifier.fillMaxWidth(), large = true)
                }
            }
        }
    }
}

@StringRes
private fun languageName(code: String): Int = when (code) {
    "en" -> R.string.captions_language_en
    "fr" -> R.string.captions_language_fr
    "es" -> R.string.captions_language_es
    "pt" -> R.string.captions_language_pt
    "de" -> R.string.language_de
    "ar" -> R.string.captions_language_ar
    "yo" -> R.string.captions_language_yo
    "ig" -> R.string.captions_language_ig
    "ha" -> R.string.captions_language_ha
    "sw" -> R.string.language_sw
    "hi" -> R.string.language_hi
    else -> R.string.language_zh
}
