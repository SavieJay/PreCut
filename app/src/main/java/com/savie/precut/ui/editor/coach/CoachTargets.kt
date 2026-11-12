package com.savie.precut.ui.editor.coach

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.savie.precut.R

/** What the editor's first-run tips (Phase 24) point at, in order. */
enum class CoachTarget(@StringRes val tip: Int) {
    TIMELINE(R.string.coach_timeline),
    TOOLS(R.string.coach_tools),
    EXPORT(R.string.coach_export),
}

/** Where each target is on screen, as laid out. */
class CoachTargets {
    val bounds = mutableStateMapOf<CoachTarget, Rect>()
}

/** Provided by the editor screen; null elsewhere (previews). */
val LocalCoachTargets = staticCompositionLocalOf<CoachTargets?> { null }

/** Records this element as [target] for the tips (nothing without [targets]). */
fun Modifier.coachTarget(targets: CoachTargets?, target: CoachTarget): Modifier =
    if (targets == null) this else onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }
