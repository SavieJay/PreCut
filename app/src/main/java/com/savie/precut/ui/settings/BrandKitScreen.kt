package com.savie.precut.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.brand.BrandMedia
import com.savie.precut.model.brand.MAX_BRAND_COLORS
import com.savie.precut.model.text.BundledFont
import com.savie.precut.ui.components.IconButtonStyle
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.components.PreCutSwitch
import com.savie.precut.ui.editor.tools.ColorPickerSheet
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.afterglowBackground

private val SwatchEdge = Color.White.copy(alpha = 0.18f)

/** Settings → Brand kit (E4): logo, colours, two fonts, intro and outro, and Apply to new projects. */
@Composable
fun BrandKitScreen(onBack: () -> Unit, viewModel: BrandKitViewModel = hiltViewModel()) {
    val kit by viewModel.kit.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<BrandSlot?>(null) }
    var editingColor by remember { mutableStateOf<Int?>(null) }
    var choosingFont by remember { mutableStateOf<Boolean?>(null) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> picking?.let { viewModel.onPicked(it, uri?.toString()) } }
    fun pick(slot: BrandSlot, images: Boolean) {
        picking = slot
        picker.launch(PickVisualMediaRequest(if (images) PickVisualMedia.ImageOnly else PickVisualMedia.VideoOnly))
    }
    Column(Modifier.fillMaxSize().afterglowBackground().statusBarsPadding().navigationBarsPadding()) {
        SettingsTopBar(stringResource(R.string.brand_title), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.brand_intro_text), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, modifier = Modifier.padding(horizontal = 20.dp))
            if (failed) Text(stringResource(R.string.brand_pick_failed), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            SettingsGroup(null) {
                MediaRow(R.drawable.ic_image, R.string.brand_logo, kit.logo, { pick(BrandSlot.LOGO, images = true) }) { viewModel.onRemove(BrandSlot.LOGO) }
            }
            SettingsGroup(stringResource(R.string.brand_colors)) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    kit.colors.forEachIndexed { index, argb ->
                        Box(Modifier.size(40.dp).background(Color(argb), CircleShape).border(1.dp, SwatchEdge, CircleShape).clickable { editingColor = index })
                    }
                    if (kit.colors.size < MAX_BRAND_COLORS) {
                        PreCutIconButton(R.drawable.ic_add, stringResource(R.string.brand_add_color), { editingColor = kit.colors.size }, style = IconButtonStyle.Glass, small = true)
                    }
                }
            }
            SettingsGroup(null) {
                SettingRow(R.drawable.ic_text, stringResource(R.string.brand_title_font), fontName(kit.titleFontId), chevron = true) { choosingFont = true }
                SettingRow(R.drawable.ic_closed_caption, stringResource(R.string.brand_body_font), fontName(kit.bodyFontId), chevron = true) { choosingFont = false }
            }
            SettingsGroup(null) {
                MediaRow(R.drawable.ic_movie, R.string.brand_intro, kit.intro, { pick(BrandSlot.INTRO, images = false) }) { viewModel.onRemove(BrandSlot.INTRO) }
                MediaRow(R.drawable.ic_movie, R.string.brand_outro, kit.outro, { pick(BrandSlot.OUTRO, images = false) }) { viewModel.onRemove(BrandSlot.OUTRO) }
            }
            SettingsGroup(null) {
                SettingRow(
                    R.drawable.ic_auto_fix_high, stringResource(R.string.brand_apply_new), stringResource(R.string.brand_apply_new_summary),
                    trailing = { PreCutSwitch(kit.applyToNew, viewModel::onApplyToNew) },
                ) { viewModel.onApplyToNew(!kit.applyToNew) }
            }
        }
    }
    editingColor?.let { index ->
        ColorPickerSheet(
            initialArgb = kit.colors.getOrElse(index) { 0xFFFF4D6D.toInt() },
            recentColors = kit.colors,
            onPicked = { viewModel.onColor(index, it); editingColor = null },
            onDismiss = { editingColor = null },
        )
    }
    choosingFont?.let { title ->
        val options = listOf<String?>(null) + BundledFont.entries.map { it.id }
        ChoiceDialog(
            stringResource(if (title) R.string.brand_title_font else R.string.brand_body_font), options,
            if (title) kit.titleFontId else kit.bodyFontId, { fontName(it) },
            onPick = { if (title) viewModel.onTitleFont(it) else viewModel.onBodyFont(it); choosingFont = null },
            onDismiss = { choosingFont = null },
        )
    }
}

@Composable
private fun fontName(id: String?): String = BundledFont.entries.firstOrNull { it.id == id }?.displayName ?: stringResource(R.string.brand_font_unchanged)

@Composable
private fun MediaRow(icon: Int, title: Int, media: BrandMedia?, onPick: () -> Unit, onRemove: () -> Unit) {
    SettingRow(
        icon, stringResource(title), stringResource(if (media == null) R.string.brand_none else R.string.brand_set),
        trailing = if (media != null) {
            { PreCutIconButton(R.drawable.ic_close, stringResource(R.string.brand_remove), onRemove, small = true, tint = TextSecondary) }
        } else {
            null
        },
        onClick = onPick,
    )
}
