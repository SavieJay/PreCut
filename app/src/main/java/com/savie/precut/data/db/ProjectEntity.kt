package com.savie.precut.data.db

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
)

/** The drafts-list columns only. */
data class ProjectSummaryRow(
    val id: String,
    val name: String,
    val coverPath: String?,
    val durationMs: Long,
    val createdAt: Long,
    val updatedAt: Long,
)
