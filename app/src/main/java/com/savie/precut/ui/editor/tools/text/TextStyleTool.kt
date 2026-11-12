package com.savie.precut.ui.editor.tools.text

import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.media.text.ImportedFont
import com.savie.precut.model.OutputSize
import com.savie.precut.model.TextOverlay
import com.savie.precut.ui.editor.tools.ApplyToAllButton
import com.savie.precut.ui.editor.tools.ToolPanel

/** What the Text style sheet reports. */
interface TextStyleActions {
    fun onChange(overlay: TextOverlay)
    fun onImportFont()
    /** This text's look on every text (batch edit). */
    fun onApplyAll()
    fun onBack()
}

/** Text style (X-04): Font, Style, Effects, Animation and Templates tabs for the selected text. */
@Composable
fun TextStyleTool(
    overlay: TextOverlay,
    /** Fonts imported into the app (ids). */
    importedFonts: List<ImportedFont>,
    recentColors: List<Int>,
    render: (TextOverlay, OutputSize) -> Bitmap?,
    actions: TextStyleActions,
    onDone: () -> Unit,
    /** Above the tabs: what a caption's changes apply to (Phase 21B); null for other texts. */
    scope: (@Composable () -> Unit)? = null,
    /** The tab to open on (the text toolbar's Font, Style, Effects, Animation; Phase 29E). */
    initialTab: Int = 0,
) {
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    ToolPanel(
        stringResource(R.string.text_style_title),
        onDone,
        actions = { ApplyToAllButton(actions::onApplyAll) },
        onBack = actions::onBack,
    ) {
        scope?.invoke()
        TextUnderlineTabs(
            listOf(R.string.text_tab_font, R.string.text_tab_style, R.string.text_tab_effects, R.string.text_tab_animation, R.string.text_tab_templates)
                .map { stringResource(it) },
            selected = tab,
            onSelect = { tab = it },
        )
        // Scrolls when the panel is capped so the preview keeps its room (Phase 29E).
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> TextFontTab(overlay, importedFonts, actions::onChange, actions::onImportFont)
                1 -> TextStyleTab(overlay, actions::onChange)
                2 -> TextEffectsTab(overlay, recentColors, actions::onChange)
                3 -> TextAnimationTab(overlay, actions::onChange)
                else -> TextTemplatesTab(overlay, render, actions::onChange)
            }
        }
    }
}
