package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.ui.editor.tools.CutoutActions

/** The Remove background panel's actions, wired to the ViewModel. */
@Composable
fun rememberCutoutActions(viewModel: EditorViewModel): CutoutActions = remember(viewModel) {
    val cutout = viewModel.cutout
    object : CutoutActions {
        override fun onEnabled(enabled: Boolean) = cutout.onEnabled(enabled)
        override fun onUpdate() = cutout.onUpdate()
        override fun onSoftness(softness: Float) = cutout.onSoftness(softness)
        override fun onEdge(edge: Float) = cutout.onEdge(edge)
        override fun onShowOriginal() = cutout.onShowOriginal()
    }
}
