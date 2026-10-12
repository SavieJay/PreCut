package com.savie.precut.data

import com.savie.precut.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

interface ProjectRepository {
    suspend fun save(project: Project)
    suspend fun load(id: String): Project?
}

/** Keeps projects for the lifetime of the process. Replaced by a Room-backed store in Phase 6. */
@Singleton
class InMemoryProjectRepository @Inject constructor() : ProjectRepository {
    private val projects = MutableStateFlow<Map<String, Project>>(emptyMap())

    override suspend fun save(project: Project) {
        projects.update { it + (project.id to project) }
    }

    override suspend fun load(id: String): Project? = projects.value[id]
}
