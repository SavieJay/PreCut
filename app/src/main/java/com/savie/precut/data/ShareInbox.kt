package com.savie.precut.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import javax.inject.Inject
import javax.inject.Singleton

/** Media shared to the app from other apps, waiting to become a new project. */
@Singleton
class ShareInbox @Inject constructor() {
    private val _pending = MutableStateFlow<List<String>>(emptyList())
    val pending: StateFlow<List<String>> = _pending.asStateFlow()

    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when a share arrives, so the app can return to the Projects screen. Not replayed. */
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    fun offer(uris: List<String>) {
        if (uris.isEmpty()) return
        _pending.value = uris
        _arrivals.tryEmit(Unit)
    }

    /** Returns the pending Uris and empties the inbox. */
    fun take(): List<String> = _pending.getAndUpdate { emptyList() }
}
