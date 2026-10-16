package com.savie.precut.ui.editor.tools.text

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
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
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
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ToolPanel(
        stringResource(R.string.text_style_title),
        onDone,
        actions = { ApplyToAllButton(actions::onApplyAll) },
        onBack = actions::onBack,
    ) {
        PillTabs(
            listOf(R.string.text_tab_font, R.string.text_tab_style, R.string.text_tab_effects, R.string.text_tab_animation, R.string.text_tab_templates)
                .map { PillTab(stringResource(it)) },
            selected = tab,
            onSelect = { tab = it },
        )
        when (tab) {
            0 -> TextFontTab(overlay, importedFonts, actions::onChange, actions::onImportFont)
            1 -> TextStyleTab(overlay, actions::onChange)
            2 -> TextEffectsTab(overlay, recentColors, actions::onChange)
            3 -> TextAnimationTab(overlay, actions::onChange)
            else -> TextTemplatesTab(overlay, render, actions::onChange)
        }
    }
}
