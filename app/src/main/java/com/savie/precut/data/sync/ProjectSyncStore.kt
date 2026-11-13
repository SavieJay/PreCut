package com.savie.precut.data.sync

import com.savie.precut.model.Project
import com.savie.precut.model.sync.LocalCopy

/** A project the server hasn't taken yet: [project] is null when it was deleted here. */
data class DirtyProject(
    val id: String,
    val project: Project?,
    val serverRevision: Int?,
    val updatedAt: Long,
) {
    val local: LocalCopy get() = LocalCopy(id, serverRevision, dirty = true, deleted = project == null)
}

/** What cloud sync (Phase 30C) needs from the phone's projects. */
interface ProjectSyncStore {
    /** Projects made before signing in join [owner]'s account. */
    suspend fun claim(owner: String)

    suspend fun localCopy(id: String): LocalCopy?

    suspend fun dirty(owner: String): List<DirtyProject>

    /** The server's copy of a project, at [revision], as [owner]'s; not dirty. */
    suspend fun takeRemote(owner: String, project: Project, revision: Int)

    /** Both sides changed: the phone's copy becomes project [newId], named by [rename], never uploaded. */
    suspend fun keepAsNew(id: String, newId: String, rename: (String) -> String)

    /** The server took the copy saved at [updatedAt], as [revision]. */
    suspend fun markSynced(id: String, revision: Int, updatedAt: Long)

    /** The phone's copy now builds on server [revision] (null: as new); it stays dirty. */
    suspend fun rebase(id: String, revision: Int?)

    /** Gone for good from the phone (deleted on the server, or a deletion the server has). */
    suspend fun remove(id: String)
}
