package com.savie.precut.ui.editor.tools.text

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.media.text.ImportedFont
import com.savie.precut.media.text.resource
import com.savie.precut.model.TextOverlay
import com.savie.precut.model.text.BundledFont
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

/** Font (X-04a): import, Roboto Flex, the bundled fonts and imported ones, each shown in itself. */
@Composable
fun TextFontTab(overlay: TextOverlay, importedFonts: List<ImportedFont>, onChange: (TextOverlay) -> Unit, onImport: () -> Unit) {
    AccentTextButton(stringResource(R.string.text_import_font), onClick = onImport, icon = R.drawable.ic_upload_file)
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FontRow(stringResource(R.string.text_font_default_name), FontFamily(Font(R.font.roboto_flex)), stringResource(R.string.text_font_default), overlay.fontId == null) {
            onChange(overlay.copy(fontId = null))
        }
        BundledFont.entries.forEach { font ->
            val family = remember(font) { FontFamily(Font(font.resource())) }
            FontRow(font.displayName, family, null, overlay.fontId == font.id) { onChange(overlay.copy(fontId = font.id)) }
        }
        importedFonts.forEach { font ->
            val family = remember(font.id) { runCatching { FontFamily(Font(font.file)) }.getOrNull() }
            FontRow(font.name, family, stringResource(R.string.text_font_imported), overlay.fontId == font.id) {
                onChange(overlay.copy(fontId = font.id))
            }
        }
    }
}

@Composable
private fun FontRow(name: String, family: FontFamily?, note: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(FontRowShape)
            .then(if (selected) Modifier.background(Accent.copy(alpha = 0.08f)).border(2.dp, Accent, FontRowShape) else Modifier)
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(name, fontFamily = family, fontSize = 20.sp, color = TextPrimary, modifier = Modifier.weight(1f))
        note?.let { Text(it, style = PreCutType.labelChip.copy(fontSize = 12.sp), color = TextMuted) }
        if (selected) Icon(painterResource(R.drawable.ic_check_circle), contentDescription = null, tint = Accent, modifier = Modifier.size(20.dp))
    }
}

private val FontRowShape = Radius.tile
