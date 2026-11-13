package com.savie.precut.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One saved project: the full model as JSON plus columns the drafts list needs without parsing. */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val json: String,
    /** Cover image file, or null when the project has no clips. */
    val coverPath: String?,
    /** What the cover was made from (first clip's uri/time/rotation), to skip needless regeneration. */
    val coverKey: String?,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
    /** Cloud projects (Phase 30): the account it belongs to; null until someone signs in. */
    val ownerId: String? = null,
    /** The server's revision this copy matches; null for one the server never had. */
    val serverRevision: Int? = null,
    /** Changed since the server last took it. */
    @ColumnInfo(defaultValue = "1") val dirty: Boolean = true,
    /** Deleted on this phone, kept until the server knows (then the row goes). */
    val deletedAt: Long? = null,
)

/** The drafts-list columns only. */
data class ProjectSummaryRow(
    val id: String,
    val name: String,
    val coverPath: String?,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean,
    val ownerId: String?,
)
