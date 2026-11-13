package com.savie.precut.data.sync

import com.savie.precut.model.Project
import com.savie.precut.model.account.AuthFailure
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Why files are waiting (Phase 30D), as Projects and Settings say it. */
sealed interface SyncNotice {
    /** Videos and photos upload on Wi-Fi only (Settings → Account), and this is mobile data. */
    data object WaitingForWifi : SyncNotice

    /** The account's cloud storage is full: new files wait, projects stay on the phone. */
    data class StorageFull(val used: Long, val limit: Long) : SyncNotice
}

sealed interface Prepared {
    /** Every file is in the cloud: [project] names them as cloud references; [extra] maps font and LUT ids to theirs. */
    data class Ready(val project: Project, val extra: Map<String, String>, val hashes: List<String>) : Prepared
    data class Wait(val notice: SyncNotice) : Prepared
    data class Failed(val failure: AuthFailure) : Prepared
}

/** What sync needs from the phone's files: uploading a project's, placing and downloading the cloud's. */
interface ProjectMedia {
    /** Uploads the files [project] uses that the account hasn't got. */
    suspend fun prepare(project: Project): Prepared

    /** The cloud's [project] as this phone's: cloud references become local files (to download if needed). */
    suspend fun localize(project: Project, extra: Map<String, String>): Project

    /** The server said it lacks these files after all: upload them again next time. */
    suspend fun forget(hashes: List<String>)

    /** Downloads files the phone's projects need; the Uris that arrived (empty when waiting for Wi-Fi). */
    suspend fun downloadMissing(): List<String>
}

/** Sync's state for the screens: a notice to show, and files arriving for open editors. */
@Singleton
class SyncStatus @Inject constructor() {
    private val _notice = MutableStateFlow<SyncNotice?>(null)
    val notice: StateFlow<SyncNotice?> = _notice.asStateFlow()

    private val _arrived = MutableSharedFlow<List<String>>(extraBufferCapacity = 4)

    /** Files that just downloaded (their Uris); an open editor shows them. */
    val arrived: SharedFlow<List<String>> = _arrived.asSharedFlow()

    fun notice(notice: SyncNotice?) {
        _notice.value = notice
    }

    fun arrived(uris: List<String>) {
        if (uris.isNotEmpty()) _arrived.tryEmit(uris)
    }
}
