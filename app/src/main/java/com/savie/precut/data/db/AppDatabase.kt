package com.savie.precut.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * v2 (Phase 30C): the cloud sync columns (owner, server revision, dirty, deleted), with defaults.
 * v3 (Phase 30D): `media_files`, each file's hash and cloud state.
 */
@Database(
    entities = [ProjectEntity::class, MediaFileEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    abstract fun mediaFileDao(): MediaFileDao

    companion object {
        const val NAME = "precut.db"
    }
}
