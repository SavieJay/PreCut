package com.savie.precut.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.pro.EntitlementRepository
import com.savie.precut.pro.KnownPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProSectionState(
    val known: KnownPlan = KnownPlan(null, null),
    val isPro: Boolean = false,
    val restoring: Boolean = false,
    val restored: Boolean = false,
    val failure: AiFailure? = null,
)

/** Settings → PreCut Pro: the server's plan, refreshed when shown; Restore purchases. */
@HiltViewModel
class ProSectionViewModel @Inject constructor(private val entitlements: EntitlementRepository) : ViewModel() {
    private val restore = MutableStateFlow(ProSectionState())

    val state: StateFlow<ProSectionState> = combine(entitlements.known, entitlements.isPro, restore) { known, pro, local ->
        local.copy(known = known, isPro = pro)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProSectionState())

    fun onShown() {
        viewModelScope.launch { entitlements.refresh() }
    }

    fun onRestore() {
        if (restore.value.restoring) return
        viewModelScope.launch {
            restore.value = restore.value.copy(restoring = true, failure = null, restored = false)
            val failure = entitlements.restore()
            restore.value = restore.value.copy(restoring = false, failure = failure, restored = failure == null)
        }
    }
}
