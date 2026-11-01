package com.savie.precut.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** A template link that arrived (Phase 28), waiting for the navigation host to open it. */
@Singleton
class TemplateLinkInbox @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun offer(id: String) {
        _pending.value = id
    }

    /** Handled: a recreated screen doesn't open it again. */
    fun handled() {
        _pending.value = null
    }
}
