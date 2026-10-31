package com.savie.precut.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.savie.precut.ui.editor.tools.NoiseActions

/** The Reduce noise rows' actions (clip and audio Volume panels), wired to the ViewModel. */
@Composable
fun rememberNoiseActions(viewModel: EditorViewModel): NoiseActions = remember(viewModel) {
    val noise = viewModel.noise
    object : NoiseActions {
        override fun onNoiseReduction(enabled: Boolean) = noise.onEnabled(enabled)
        override fun onNoiseStrength(strength: Float) = noise.onStrength(strength)
    }
}
