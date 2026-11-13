package com.savie.precut.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    /** Projects shown: not deleted, and [owner]'s or no one's ([all] for a build without accounts). */
    @Query(
        "SELECT id, name, coverPath, durationMs, createdAt, updatedAt, dirty, ownerId FROM projects " +
            "WHERE deletedAt IS NULL AND (:all OR ownerId IS NULL OR ownerId = :owner) ORDER BY updatedAt DESC",
    )
    fun observeSummaries(owner: String?, all: Boolean): Flow<List<ProjectSummaryRow>>

    /** A row, deleted or not (sync needs both). */
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    /** Every project kept on the phone, whoever's, so housekeeping keeps their files. */
    @Query("SELECT json FROM projects WHERE deletedAt IS NULL")
    suspend fun allJson(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE deletedAt IS NULL)")
    suspend fun any(): Boolean

    /** [owner]'s rows the server hasn't taken yet, deletions included. */
    @Query("SELECT * FROM projects WHERE dirty = 1 AND ownerId = :owner")
    suspend fun dirty(owner: String): List<ProjectEntity>

    /** Projects made before anyone signed in join [owner]'s account (and will upload). */
    @Query("UPDATE projects SET ownerId = :owner, dirty = 1 WHERE ownerId IS NULL")
    suspend fun claim(owner: String): Int

    /** The server took the copy saved at [updatedAt]; an edit since then stays dirty. */
    @Query(
        "UPDATE projects SET serverRevision = :revision, " +
            "dirty = CASE WHEN updatedAt = :updatedAt THEN 0 ELSE 1 END WHERE id = :id",
    )
    suspend fun markSynced(id: String, revision: Int, updatedAt: Long)

    @Query("UPDATE projects SET serverRevision = :revision WHERE id = :id")
    suspend fun rebase(id: String, revision: Int?)

    @Query("UPDATE projects SET deletedAt = :deletedAt, dirty = 1, coverPath = NULL, coverKey = NULL WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: Long)

    @Upsert
    suspend fun upsert(entity: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)
}
