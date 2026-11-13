package com.savie.precut.data.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** v2 (Phase 30): the cloud sync columns (owner, server revision, dirty, deleted), added with their defaults. */
@Database(entities = [ProjectEntity::class], version = 2, exportSchema = true, autoMigrations = [AutoMigration(from = 1, to = 2)])
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        const val NAME = "precut.db"
    }
}
