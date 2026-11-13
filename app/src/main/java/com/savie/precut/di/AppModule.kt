package com.savie.precut.di

import android.content.Context
import androidx.room.Room
import com.savie.precut.BuildConfig
import com.savie.precut.R
import com.savie.precut.ai.CloudTranscriber
import com.savie.precut.ai.Transcriber
import com.savie.precut.ai.cloud.AndroidNetworkState
import com.savie.precut.ai.cloud.CloudConfig
import com.savie.precut.auth.AccessTokens
import com.savie.precut.auth.AccountRepository
import com.savie.precut.auth.AuthConfig
import com.savie.precut.data.sync.CloudProjectSyncApi
import com.savie.precut.data.sync.PrefsSyncCursors
import com.savie.precut.data.sync.ProjectSync
import com.savie.precut.data.sync.ProjectSyncApi
import com.savie.precut.data.sync.ProjectSyncStore
import com.savie.precut.data.sync.SyncCursors
import com.savie.precut.data.sync.SyncRequests
import com.savie.precut.data.sync.WorkManagerSyncRequests
import com.savie.precut.auth.KeystoreSessionStore
import com.savie.precut.auth.SessionStore
import com.savie.precut.ai.cloud.InstallStore
import com.savie.precut.ai.cloud.IntegrityTokens
import com.savie.precut.ai.cloud.NetworkState
import com.savie.precut.ai.cloud.PlayIntegrityTokens
import com.savie.precut.ai.cloud.PrefsInstallStore
import com.savie.precut.data.AndroidSubtitleFiles
import com.savie.precut.data.InstallReferrerSource
import com.savie.precut.data.PlayInstallReferrerSource
import com.savie.precut.data.SubtitleFiles
import com.savie.precut.data.AndroidAppStorage
import com.savie.precut.data.AppMediaJanitor
import com.savie.precut.data.CloudTemplateSharing
import com.savie.precut.export.EndCards
import com.savie.precut.media.export.EndCardMaker
import com.savie.precut.data.TemplateSharing
import com.savie.precut.data.WebConfig
import com.savie.precut.data.AppSettings
import com.savie.precut.data.AppStorage
import com.savie.precut.data.FileTemplateStore
import com.savie.precut.data.PrefsAppSettings
import com.savie.precut.data.PrefsTemplateHistory
import com.savie.precut.data.ProjectRepository
import com.savie.precut.data.RoomProjectRepository
import com.savie.precut.data.TemplateHistory
import com.savie.precut.data.TemplateStore
import com.savie.precut.data.UnusedMediaCleaner
import com.savie.precut.data.db.AppDatabase
import com.savie.precut.data.db.ProjectDao
import com.savie.precut.export.AndroidExportServiceStarter
import com.savie.precut.export.ExportServiceStarter
import com.savie.precut.media.AndroidMediaImporter
import com.savie.precut.media.AndroidMediaProbe
import com.savie.precut.media.CaptureFiles
import com.savie.precut.media.CaptureStore
import com.savie.precut.media.ExportManager
import com.savie.precut.media.MediaImporter
import com.savie.precut.media.MediaProbe
import com.savie.precut.media.MediaSaver
import com.savie.precut.media.MediaStoreSaver
import com.savie.precut.media.SharedMediaCopier
import com.savie.precut.media.SharedMediaStore
import com.savie.precut.media.AndroidSpeechAudio
import com.savie.precut.media.SpeechAudio
import com.savie.precut.media.VideoExporter
import com.savie.precut.media.audio.AndroidBeatDetector
import com.savie.precut.media.audio.BeatDetector
import com.savie.precut.pro.PlayTrialOffers
import com.savie.precut.pro.TrialOffers
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.util.concurrent.TimeUnit
import java.util.UUID
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    abstract fun bindSessionStore(impl: KeystoreSessionStore): SessionStore

    @Binds
    abstract fun bindAccessTokens(impl: AccountRepository): AccessTokens

    @Binds
    abstract fun bindSyncRequests(impl: WorkManagerSyncRequests): SyncRequests

    @Binds
    abstract fun bindSyncCursors(impl: PrefsSyncCursors): SyncCursors

    @Binds
    abstract fun bindProjectSyncStore(impl: RoomProjectRepository): ProjectSyncStore

    @Binds
    abstract fun bindProjectSyncApi(impl: CloudProjectSyncApi): ProjectSyncApi

    @Binds
    abstract fun bindProjectRepository(impl: RoomProjectRepository): ProjectRepository

    @Binds
    abstract fun bindUnusedMediaCleaner(impl: AppMediaJanitor): UnusedMediaCleaner

    @Binds
    abstract fun bindMediaProbe(impl: AndroidMediaProbe): MediaProbe

    @Binds
    abstract fun bindMediaImporter(impl: AndroidMediaImporter): MediaImporter

    @Binds
    abstract fun bindTemplateSharing(impl: CloudTemplateSharing): TemplateSharing

    @Binds
    abstract fun bindEndCards(impl: EndCardMaker): EndCards

    @Binds
    abstract fun bindTrialOffers(impl: PlayTrialOffers): TrialOffers

    @Binds
    abstract fun bindInstallReferrerSource(impl: PlayInstallReferrerSource): InstallReferrerSource

    @Binds
    abstract fun bindBeatDetector(impl: AndroidBeatDetector): BeatDetector

    @Binds
    abstract fun bindTemplateStore(impl: FileTemplateStore): TemplateStore

    @Binds
    abstract fun bindTemplateHistory(impl: PrefsTemplateHistory): TemplateHistory

    @Binds
    abstract fun bindVideoExporter(impl: ExportManager): VideoExporter

    @Binds
    abstract fun bindSpeechAudio(impl: AndroidSpeechAudio): SpeechAudio

    @Binds
    abstract fun bindSubtitleFiles(impl: AndroidSubtitleFiles): SubtitleFiles

    @Binds
    abstract fun bindMediaSaver(impl: MediaStoreSaver): MediaSaver

    @Binds
    abstract fun bindAppStorage(impl: AndroidAppStorage): AppStorage

    @Binds
    abstract fun bindAppSettings(impl: PrefsAppSettings): AppSettings

    @Binds
    abstract fun bindExportServiceStarter(impl: AndroidExportServiceStarter): ExportServiceStarter

    @Binds
    abstract fun bindCaptureStore(impl: CaptureFiles): CaptureStore

    @Binds
    abstract fun bindSharedMediaStore(impl: SharedMediaCopier): SharedMediaStore

    @Binds
    abstract fun bindInstallStore(impl: PrefsInstallStore): InstallStore

    @Binds
    abstract fun bindIntegrityTokens(impl: PlayIntegrityTokens): IntegrityTokens

    @Binds
    abstract fun bindNetworkState(impl: AndroidNetworkState): NetworkState

    @Binds
    abstract fun bindTranscriber(impl: CloudTranscriber): Transcriber

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

        /** This build's backend (Phase 20), from the gitignored cloud.properties; empty without it. */
        @Provides
        fun provideCloudConfig(): CloudConfig =
            CloudConfig(BuildConfig.CLOUD_URL, BuildConfig.CLOUD_REGION, BuildConfig.PLAY_CLOUD_PROJECT_NUMBER)

        /** Accounts (Phase 30): the project behind the function, and the keys from cloud.properties. */
        @Provides
        fun provideAuthConfig(): AuthConfig = AuthConfig.fromCloudUrl(
            BuildConfig.CLOUD_URL,
            BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            BuildConfig.GOOGLE_WEB_CLIENT_ID,
        )

        /** Cloud project sync (Phase 30C); the phone's copy in a conflict is named "… (this phone)". */
        @Provides
        @Singleton
        fun provideProjectSync(
            @ApplicationContext context: Context,
            tokens: AccessTokens,
            api: ProjectSyncApi,
            store: ProjectSyncStore,
            cursors: SyncCursors,
        ): ProjectSync = ProjectSync(
            tokens, api, store, cursors,
            conflictLabel = { context.getString(R.string.sync_conflict_label) },
            newId = { UUID.randomUUID().toString() },
        )

        /** The website template links point at (Phase 28), from cloud.properties' `webHost`. */
        @Provides
        fun provideWebConfig(): WebConfig = WebConfig(BuildConfig.WEB_HOST)

        /**
         * One client for the cloud API. Long write and read timeouts: a 10 MB upload on a slow
         * mobile connection takes minutes, and the backend may work for up to 150 s.
         */
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.MINUTES)
            .build()

        /** Outlives screens, so a final auto-save still completes after the editor closes. */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
