package com.savie.precut.data

import com.savie.precut.model.Project
import com.savie.precut.model.totalDurationMs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** A draft as shown on the Projects screen. */
data class ProjectSummary(
    val id: String,
    val name: String,
    val coverPath: String?,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
)

interface ProjectRepository {
    suspend fun save(project: Project)
    suspend fun load(id: String): Project?

    /** Drafts, most recently edited first. */
    fun observeProjects(): Flow<List<ProjectSummary>>
    suspend fun delete(id: String)
    suspend fun rename(id: String, name: String)

    /** Copies a project under [newId]; false if [id] doesn't exist. */
    suspend fun duplicate(id: String, newId: String, nowMs: Long): Boolean

    /** Every saved project (for housekeeping such as unused-file cleanup). */
    suspend fun loadAll(): List<Project>
}

/** In-memory store, used by unit tests. */
class InMemoryProjectRepository : ProjectRepository {
    private val projects = MutableStateFlow<Map<String, Project>>(emptyMap())

    override suspend fun save(project: Project) = projects.update { it + (project.id to project) }

    override suspend fun load(id: String): Project? = projects.value[id]

    override fun observeProjects(): Flow<List<ProjectSummary>> = projects.map { all ->
        all.values.sortedByDescending { it.updatedAt }.map {
            ProjectSummary(it.id, it.name, null, it.totalDurationMs(), it.createdAt, it.updatedAt)
        }
    }

    override suspend fun delete(id: String) = projects.update { it - id }

    override suspend fun rename(id: String, name: String) =
        projects.update { all -> all[id]?.let { all + (id to it.copy(name = name)) } ?: all }

    override suspend fun duplicate(id: String, newId: String, nowMs: Long): Boolean {
        val original = projects.value[id] ?: return false
        save(original.copy(id = newId, name = original.name, createdAt = nowMs, updatedAt = nowMs))
        return true
    }

    override suspend fun loadAll(): List<Project> = projects.value.values.toList()
}
