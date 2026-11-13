package com.savie.precut.data

import com.savie.precut.auth.AccountRepository
import com.savie.precut.data.db.ProjectDao
import com.savie.precut.data.db.ProjectEntity
import com.savie.precut.data.sync.DirtyProject
import com.savie.precut.data.sync.ProjectSyncStore
import com.savie.precut.data.sync.SyncRequests
import com.savie.precut.media.ProjectCoverMaker
import com.savie.precut.model.Project
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.sync.LocalCopy
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Projects in Room as JSON, with a cover image regenerated only when the first clip changes.
 * Phase 30: each project belongs to the signed-in account (or no one, before signing in); a save
 * marks it for upload and asks for a sync; Projects shows only this account's (and no one's).
 */
@Singleton
class RoomProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val covers: ProjectCoverMaker,
    private val accounts: AccountRepository,
    private val sync: SyncRequests,
    private val clock: Clock,
) : ProjectRepository, ProjectSyncStore {

    // Serializes read-modify-write of a row (cover bookkeeping) across concurrent saves.
    private val lock = Mutex()

    private val owner: String? get() = (accounts.state.value as? AuthState.SignedIn)?.userId

    override suspend fun save(project: Project) {
        lock.withLock {
            val existing = dao.get(project.id)
            write(project, existing, ownerId = existing?.ownerId ?: owner, serverRevision = existing?.serverRevision, dirty = true)
        }
        if (owner != null) sync.afterEdit()
    }

    /** One row with its cover, keeping or remaking the cover as the first clip says. */
    private suspend fun write(project: Project, existing: ProjectEntity?, ownerId: String?, serverRevision: Int?, dirty: Boolean) {
        val key = covers.keyFor(project)
        val coverPath = when {
            key == null -> null.also { covers.delete(existing?.coverPath) }
            key == existing?.coverKey && existing.coverPath != null -> existing.coverPath
            else -> covers.make(project, key)?.also { if (it != existing?.coverPath) covers.delete(existing?.coverPath) }
        }
        dao.upsert(
            ProjectEntity(
                id = project.id,
                name = project.name,
                json = ProjectSerializer.encode(project),
                coverPath = coverPath,
                coverKey = key.takeIf { coverPath != null },
                durationMs = project.totalDurationMs(),
                createdAt = project.createdAt,
                updatedAt = project.updatedAt,
                ownerId = ownerId,
                serverRevision = serverRevision,
                dirty = dirty,
                deletedAt = null,
            ),
        )
    }

    override suspend fun load(id: String): Project? =
        dao.get(id)?.takeIf { it.deletedAt == null }?.json?.let(ProjectSerializer::decode)

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeProjects(): Flow<List<ProjectSummary>> = accounts.state.flatMapLatest { state ->
        val signedIn = state as? AuthState.SignedIn
        dao.observeSummaries(signedIn?.userId, all = state is AuthState.NotSetUp).map { rows ->
            rows.map {
                ProjectSummary(
                    it.id, it.name, it.coverPath, it.durationMs, it.createdAt, it.updatedAt,
                    cloud = if (signedIn == null) null else if (it.dirty) CloudMark.Pending else CloudMark.Synced,
                )
            }
        }
    }

    /** A project the server has stays as a deletion until the server knows; others go at once. */
    override suspend fun delete(id: String) {
        val tombstone = lock.withLock {
            val row = dao.get(id) ?: return@withLock false
            covers.delete(row.coverPath)
            if (row.serverRevision != null && row.ownerId != null) {
                dao.markDeleted(id, clock.millis())
                true
            } else {
                dao.delete(id)
                false
            }
        }
        if (tombstone) sync.afterEdit()
    }

    override suspend fun rename(id: String, name: String) {
        val project = load(id) ?: return
        save(project.copy(name = name))
    }

    override suspend fun duplicate(id: String, newId: String, nowMs: Long): Boolean {
        val project = load(id) ?: return false
        save(project.copy(id = newId, createdAt = nowMs, updatedAt = nowMs))
        return true
    }

    override suspend fun loadAll(): List<Project> = dao.allJson().mapNotNull(ProjectSerializer::decode)

    override suspend fun hasAny(): Boolean = dao.any()

    // ProjectSyncStore (Phase 30C)

    override suspend fun claim(owner: String) {
        dao.claim(owner)
    }

    override suspend fun localCopy(id: String): LocalCopy? =
        dao.get(id)?.let { LocalCopy(it.id, it.serverRevision, it.dirty, deleted = it.deletedAt != null) }

    override suspend fun dirty(owner: String): List<DirtyProject> = dao.dirty(owner).map {
        DirtyProject(it.id, it.takeIf { row -> row.deletedAt == null }?.json?.let(ProjectSerializer::decode), it.serverRevision, it.updatedAt)
    }

    override suspend fun takeRemote(owner: String, project: Project, revision: Int) = lock.withLock {
        write(project, dao.get(project.id), ownerId = owner, serverRevision = revision, dirty = false)
    }

    override suspend fun keepAsNew(id: String, newId: String, rename: (String) -> String) = lock.withLock {
        val row = dao.get(id) ?: return@withLock
        val project = ProjectSerializer.decode(row.json) ?: return@withLock
        write(project.copy(id = newId, name = rename(project.name)), null, ownerId = row.ownerId, serverRevision = null, dirty = true)
    }

    override suspend fun markSynced(id: String, revision: Int, updatedAt: Long) = dao.markSynced(id, revision, updatedAt)

    override suspend fun rebase(id: String, revision: Int?) = dao.rebase(id, revision)

    override suspend fun refreshCovers(owner: String) = lock.withLock {
        for (row in dao.withoutCover(owner)) {
            val project = ProjectSerializer.decode(row.json) ?: continue
            write(project, row, ownerId = row.ownerId, serverRevision = row.serverRevision, dirty = row.dirty)
        }
    }

    override suspend fun remove(id: String) = lock.withLock {
        covers.delete(dao.get(id)?.coverPath)
        dao.delete(id)
    }
}
