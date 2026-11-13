package com.savie.precut.data.sync

import com.savie.precut.auth.AccessTokens
import com.savie.precut.data.ProjectSerializer
import com.savie.precut.model.Project
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.sync.CURSOR_OVERLAP_MS
import com.savie.precut.model.sync.Incoming
import com.savie.precut.model.sync.Outgoing
import com.savie.precut.model.sync.RemoteCopy
import com.savie.precut.model.sync.keptCopyName
import com.savie.precut.model.sync.planIncoming
import com.savie.precut.model.sync.planOutgoing
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Instant

/** How a sync ended. */
sealed interface SyncOutcome {
    data object Done : SyncOutcome
    data object NotSignedIn : SyncOutcome
    /** Offline or the server was busy: try again later. */
    data class Retry(val failure: AuthFailure) : SyncOutcome
}

/** Where the last sync's changes ended, per account. */
interface SyncCursors {
    fun get(userId: String): Cursor?
    fun set(userId: String, cursor: Cursor?)
}

/**
 * Cloud project sync (Phase 30C): first takes what changed on the server (other phones), then
 * sends what changed here, deciding each project with [planIncoming] and [planOutgoing]. Safe to
 * run again at any time: every step is idempotent. [conflictLabel] names the phone's copy when
 * both sides changed ("this phone"); [newId] makes its id.
 */
class ProjectSync(
    private val tokens: AccessTokens,
    private val api: ProjectSyncApi,
    private val store: ProjectSyncStore,
    private val cursors: SyncCursors,
    private val conflictLabel: () -> String,
    private val newId: () -> String,
) {
    private val mutex = Mutex()

    suspend fun run(): SyncOutcome = mutex.withLock {
        val user = tokens.userId ?: return SyncOutcome.NotSignedIn
        store.claim(user)
        pull(user)?.let { return it }
        // Edits made while sending, and copies a conflict moved on, are sent too; a copy already
        // tried as it is (waiting for files, too large) isn't sent again in this run.
        val tried = mutableSetOf<Triple<String, Long, Int?>>()
        repeat(MAX_PUSH_ROUNDS) {
            val dirty = store.dirty(user).filter { tried.add(Triple(it.id, it.updatedAt, it.serverRevision)) }
            if (dirty.isEmpty()) return SyncOutcome.Done
            for (project in dirty) push(user, project)?.let { return it }
        }
        SyncOutcome.Done
    }

    /** Every change since the last sync, page by page; null when all were taken. */
    private suspend fun pull(user: String): SyncOutcome? {
        var cursor = cursors.get(user)
        var last: Cursor? = null
        while (true) {
            val page = when (val result = tokens.withToken { api.changes(it, cursor) }) {
                is AuthResult.Ok -> result.value
                is AuthResult.Failed -> return SyncOutcome.Retry(result.failure)
            }
            page.projects.forEach { take(user, it) }
            page.next?.let { cursor = it; last = it }
            if (!page.more || page.next == null) break
        }
        last?.let { cursors.set(user, overlap(it)) }
        return null
    }

    private suspend fun take(user: String, remote: RemoteProject) {
        val local = store.localCopy(remote.id)
        when (val plan = planIncoming(local, RemoteCopy(remote.id, remote.revision, remote.deleted))) {
            Incoming.Ignore -> Unit
            Incoming.Take -> decode(remote)?.let { store.takeRemote(user, it, remote.revision) }
            Incoming.DropLocal -> store.remove(remote.id)
            Incoming.KeepBoth -> {
                val project = decode(remote) ?: return
                store.keepAsNew(remote.id, newId()) { name -> keptCopyName(name, conflictLabel()) }
                store.takeRemote(user, project, remote.revision)
            }
            is Incoming.KeepLocal -> store.rebase(remote.id, plan.revision)
        }
    }

    /** Sends one project; a result to stop with (offline, signed out), else null. */
    private suspend fun push(user: String, dirty: DirtyProject): SyncOutcome? {
        val reply = when (val plan = planOutgoing(dirty.local)) {
            Outgoing.Forget -> return null.also { store.remove(dirty.id) }
            is Outgoing.DeleteRemote -> tokens.withToken { api.delete(it, dirty.id, plan.base) }
            is Outgoing.Upload -> {
                val project = dirty.project ?: return null
                tokens.withToken { api.save(it, dirty.id, upload(project, plan.base)) }
            }
        }
        when (reply) {
            is AuthResult.Failed -> return SyncOutcome.Retry(reply.failure)
            is AuthResult.Ok -> when (val answer = reply.value) {
                is SaveReply.Saved -> if (dirty.project == null) store.remove(dirty.id) else store.markSynced(dirty.id, answer.revision, dirty.updatedAt)
                is SaveReply.Conflict -> {
                    val server = answer.server
                    if (server != null) {
                        take(user, server)
                    } else if (dirty.project == null) {
                        store.remove(dirty.id)
                    } else {
                        // The server forgot it (a deletion older than 90 days): send it as new.
                        store.rebase(dirty.id, null)
                    }
                }
                // Files come with Phase 30D; a project too large stays on the phone only.
                is SaveReply.MissingMedia, SaveReply.TooLarge -> Unit
            }
        }
        return null
    }

    private fun upload(project: Project, base: Int?) = ProjectUpload(
        baseRevision = base,
        name = project.name,
        project = Json.parseToJsonElement(ProjectSerializer.encode(project)) as JsonObject,
        media = emptyList(),
        coverSha256 = null,
        durationMs = project.totalDurationMs(),
        editedAt = Instant.ofEpochMilli(project.updatedAt).toString(),
    )

    private fun decode(remote: RemoteProject): Project? =
        remote.project?.let { ProjectSerializer.decode(it.toString()) }?.copy(id = remote.id)

    private fun overlap(cursor: Cursor): Cursor {
        val since = runCatching { Instant.parse(cursor.since) }.getOrNull() ?: return cursor
        return Cursor(since.minusMillis(CURSOR_OVERLAP_MS).toString(), NO_ID)
    }

    private companion object {
        const val MAX_PUSH_ROUNDS = 3
        const val NO_ID = "00000000-0000-0000-0000-000000000000"
    }
}
