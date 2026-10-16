package com.savie.precut.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class InMemoryAppSettings(initial: SettingsValues = SettingsValues()) : AppSettings {
    private val state = MutableStateFlow(initial)
    override val values: StateFlow<SettingsValues> = state
    override fun update(transform: (SettingsValues) -> SettingsValues) {
        state.value = transform(state.value)
    }
}
