package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.media.sticker.LibraryImage
import com.savie.precut.model.sticker.PackSticker
import com.savie.precut.model.sticker.StickerPack
import com.savie.precut.ui.components.PillTab
import com.savie.precut.ui.components.PillTabs
import com.savie.precut.ui.editor.tools.ToolPanel

/** What the sticker sheet can do. */
interface StickerSheetActions {
    fun onEmoji(emoji: String)
    fun onImage(image: LibraryImage)
    fun onImport()
    fun onRemoveImage(image: LibraryImage)
    fun onPack(pack: StickerPack, item: PackSticker)
}

/**
 * S-01 / S-01c: Emoji (search, recent, categories), the packs that ship with the app (Animated,
 * 3D) and My images; a tap places the sticker at the playhead.
 */
@Composable
fun StickerSheet(recent: List<String>, images: List<LibraryImage>, actions: StickerSheetActions, onClose: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ToolPanel(stringResource(R.string.sticker_title), onClose) {
        PillTabs(
            listOf(R.string.sticker_tab_emoji, R.string.sticker_tab_animated, R.string.sticker_tab_3d, R.string.sticker_tab_images).map { PillTab(stringResource(it)) },
            tab,
            { tab = it },
        )
        when (tab) {
            0 -> EmojiTab(recent, actions::onEmoji)
            1 -> PackTab(StickerPack.ANIMATED, actions::onPack)
            2 -> PackTab(StickerPack.THREE_D, actions::onPack)
            else -> ImagesTab(images, actions)
        }
    }
}
