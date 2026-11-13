package com.savie.precut.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * A file on this phone and its place in the cloud (Phase 30D): its SHA-256 (so it's hashed once,
 * while it doesn't change), whether the account has it, and an upload in progress (to resume).
 * Rows for files still to download say where each one goes.
 */
@Entity(tableName = "media_files")
data class MediaFileEntity(
    /** The Uri projects use (`file://…` or `content://…`). */
    @PrimaryKey val uri: String,
    val sha256: String,
    val size: Long,
    /** When the file last changed (ms), or 0 when unknown; a change means hashing again. */
    val modified: Long,
    val mime: String,
    /** The account has this file (uploaded and confirmed, or found there). */
    val inCloud: Boolean,
    /** A resumable upload's address, while one is under way. */
    val uploadUrl: String? = null,
)

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files WHERE uri = :uri")
    suspend fun get(uri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sha256 = :sha256")
    suspend fun withHash(sha256: String): List<MediaFileEntity>

    @Upsert
    suspend fun upsert(entity: MediaFileEntity)

    @Query("UPDATE media_files SET inCloud = 1, uploadUrl = NULL WHERE sha256 = :sha256")
    suspend fun markInCloud(sha256: String)

    @Query("UPDATE media_files SET uploadUrl = :uploadUrl WHERE uri = :uri")
    suspend fun setUploadUrl(uri: String, uploadUrl: String?)

    @Query("SELECT * FROM media_files WHERE uri LIKE 'file://%'")
    suspend fun appFiles(): List<MediaFileEntity>

    @Query("DELETE FROM media_files WHERE uri = :uri")
    suspend fun delete(uri: String)
}
