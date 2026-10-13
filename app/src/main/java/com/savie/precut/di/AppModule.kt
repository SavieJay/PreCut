package com.savie.precut.di

import android.content.Context
import androidx.room.Room
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.RoomProjectRepository
import com.savie.precut.data.UnusedMediaCleaner
import com.savie.precut.data.VoiceoverJanitor
import com.savie.precut.data.db.AppDatabase
import com.savie.precut.data.db.ProjectDao
import com.savie.precut.media.AndroidMediaImporter
import com.savie.precut.media.AndroidMediaProbe
import com.savie.precut.media.ExportManager
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.MediaProbe
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.MediaStoreSaver
import com.savie.precut.media.VideoExporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindProjectRepository(impl: RoomProjectRepository): ProjectRepository

    @Binds
    abstract fun bindUnusedMediaCleaner(impl: VoiceoverJanitor): UnusedMediaCleaner

    @Binds
    abstract fun bindMediaProbe(impl: AndroidMediaProbe): MediaProbe

    @Binds
    abstract fun bindMediaImporter(impl: AndroidMediaImporter): MediaImporter

    @Binds
    abstract fun bindVideoExporter(impl: ExportManager): VideoExporter

    @Binds
    abstract fun bindMediaSaver(impl: MediaStoreSaver): MediaSaver

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemUTC()

        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()

        @Provides
        fun provideProjectDao(database: AppDatabase): ProjectDao = database.projectDao()

        /** Outlives screens, so a final auto-save still completes after the editor closes. */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
