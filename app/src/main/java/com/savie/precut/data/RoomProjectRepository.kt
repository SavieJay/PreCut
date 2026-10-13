package com.savie.precut.data

import com.savie.precut.data.db.ProjectDao
import com.savie.precut.data.db.ProjectEntity
import com.savie.precut.media.ProjectCoverMaker
import com.savie.precut.model.Project
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Projects in Room as JSON, with a cover image regenerated only when the first clip changes. */
@Singleton
class RoomProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val covers: ProjectCoverMaker,
) : ProjectRepository {

    // Serializes read-modify-write of a row (cover bookkeeping) across concurrent saves.
    private val lock = Mutex()

    override suspend fun save(project: Project) = lock.withLock {
        val existing = dao.get(project.id)
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
            ),
        )
    }

    override suspend fun load(id: String): Project? = dao.get(id)?.json?.let(ProjectSerializer::decode)

    override fun observeProjects(): Flow<List<ProjectSummary>> = dao.observeSummaries().map { rows ->
        rows.map { ProjectSummary(it.id, it.name, it.coverPath, it.durationMs, it.createdAt, it.updatedAt) }
    }

    override suspend fun delete(id: String) = lock.withLock {
        covers.delete(dao.get(id)?.coverPath)
        dao.delete(id)
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
}
