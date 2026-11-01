package com.savie.precut.ui.editor

import com.savie.precut.model.coach.HookTip
import com.savie.precut.model.coach.hookEdit
import com.savie.precut.model.coach.hookTips
import com.savie.precut.model.pro.ProFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The hook coach sheet: its tips and which have been applied. */
data class HookCoachUi(val tips: List<HookTip>, val applied: Set<HookTip> = emptySet())

/**
 * Hook coach (Phase 26B, Pro, opt-in): checks the first seconds and the pacing when asked, and each
 * tip's Apply is its own undo step.
 */
class EditorHookCoach(private val host: EditorHost, private val pro: EditorPro) {
    private val _state = MutableStateFlow<HookCoachUi?>(null)
    val state: StateFlow<HookCoachUi?> = _state.asStateFlow()

    fun open() = pro.gate(ProFeature.HOOK_COACH) {
        val project = host.ready?.project ?: return@gate
        pro.spend(ProFeature.HOOK_COACH)
        _state.value = HookCoachUi(project.hookTips())
    }

    /** Applies [tip]; [title] is the words for a suggested title, in the user's language. */
    fun onApply(tip: HookTip, title: String) {
        val project = host.ready?.project ?: return
        val edit = project.hookEdit(tip, title, host.newId()) ?: return
        host.applyEdit(edit)
        _state.update { it?.copy(applied = it.applied + tip) }
        if (tip == HookTip.AddTitle || tip is HookTip.TrimFirstClip) host.seekTo(0L)
    }

    fun onDismiss() {
        _state.value = null
    }
}
