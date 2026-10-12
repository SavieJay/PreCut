package com.savie.precut.di

import com.savie.precut.data.InMemoryProjectRepository
import com.savie.precut.data.ProjectRepository
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
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindProjectRepository(impl: InMemoryProjectRepository): ProjectRepository

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
    }
}
