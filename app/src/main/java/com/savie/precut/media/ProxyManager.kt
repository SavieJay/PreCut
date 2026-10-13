package com.savie.precut.media

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.savie.precut.di.ApplicationScope
import com.savie.precut.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ProxyState {
    /** Queued or transcoding; [percent] is null until Transformer can estimate it. */
    data class Pending(val percent: Int?) : ProxyState

    data class Ready(val uri: String) : ProxyState

    /** Couldn't be made (e.g. codec busy or unsupported); the preview uses the original. */
    data object Failed : ProxyState
}

/**
 * Makes preview proxies (see [ProxyRules]) in the background, one at a time, into
 * `cacheDir/proxies`. Main thread only (Transformer needs a Looper). Paused during export so the
 * two never compete for the encoder.
 */
@OptIn(UnstableApi::class)
@Singleton
class ProxyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val dir = File(context.cacheDir, DIR)
    private val scope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    private val _states = MutableStateFlow<Map<String, ProxyState>>(emptyMap())

    /** Proxy state per source Uri. */
    val states: StateFlow<Map<String, ProxyState>> = _states.asStateFlow()

    private val queue = ArrayDeque<String>()
    private var running: Running? = null
    private var pauses = 0

    private class Running(val uri: String, val transformer: Transformer, val partial: File, val poller: Job)

    /** Queues proxies for these source Uris; already made or queued ones are skipped. */
    fun request(sourceUris: Collection<String>) {
        scope.launch {
            for (uri in sourceUris.distinct()) {
                when (val state = _states.value[uri]) {
                    is ProxyState.Pending, ProxyState.Failed -> continue
                    is ProxyState.Ready -> if (fileExists(state.uri)) continue
                    null -> Unit
                }
                val file = File(dir, ProxyRules.fileName(uri))
                if (withContext(ioDispatcher) { file.exists() }) {
                    setState(uri, ProxyState.Ready(Uri.fromFile(file).toString()))
                } else {
                    setState(uri, ProxyState.Pending(null))
                    queue.addLast(uri)
                }
            }
            startNext()
        }
    }

    /** Stops proxying (the current one restarts later) until a matching [resume]. */
    fun pause() {
        pauses++
        running?.let { job ->
            job.poller.cancel()
            job.transformer.cancel()
            job.partial.delete()
            running = null
            queue.addFirst(job.uri)
            setState(job.uri, ProxyState.Pending(null))
        }
    }

    fun resume() {
        pauses = (pauses - 1).coerceAtLeast(0)
        startNext()
    }

    /** Deletes proxies of sources not in [keepSourceUris], then the oldest beyond the cache limit. */
    suspend fun trim(keepSourceUris: Set<String>) {
        val keepNames = keepSourceUris.map(ProxyRules::fileName).toSet() + listOfNotNull(running?.uri?.let(ProxyRules::fileName))
        val deleted = withContext(ioDispatcher) {
            val files = dir.listFiles().orEmpty().filter { it.extension == "mp4" }
            val cached = files.map { ProxyRules.CachedFile(it.name, it.length(), it.lastModified()) }
            val names = ProxyRules.filesToDelete(cached, keepNames, ProxyRules.PROXY_CACHE_LIMIT_BYTES).toSet()
            files.filter { it.name in names }.onEach { it.delete() }.map { it.name }.toSet()
        }
        if (deleted.isNotEmpty()) {
            _states.update { states -> states.filterKeys { ProxyRules.fileName(it) !in deleted } }
        }
    }

    private fun startNext() {
        if (pauses > 0 || running != null) return
        val uri = queue.removeFirstOrNull() ?: return
        dir.mkdirs()
        val target = File(dir, ProxyRules.fileName(uri))
        val partial = File(dir, target.name + ".part")
        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(
                DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(ProxyRules.PROXY_BITRATE)
                            .setiFrameIntervalSeconds(ProxyRules.PROXY_KEYFRAME_INTERVAL_S)
                            .build(),
                    )
                    .setEnableFallback(true)
                    .build(),
            )
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) =
                    finish(uri, if (partial.renameTo(target)) ProxyState.Ready(Uri.fromFile(target).toString()) else ProxyState.Failed)

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    partial.delete()
                    finish(uri, ProxyState.Failed)
                }
            })
            .build()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(emptyList(), listOf(Presentation.createForShortSide(ProxyRules.PROXY_SHORT_SIDE_PX))))
            .build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO)).addItem(item).build(),
        )
            // A proxy only needs to look right; SDR keeps it playable everywhere.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        transformer.start(composition, partial.absolutePath)
        val progress = ProgressHolder()
        val poller = scope.launch {
            while (isActive) {
                delay(PROGRESS_POLL_MS)
                if (transformer.getProgress(progress) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    setState(uri, ProxyState.Pending(progress.progress))
                }
            }
        }
        running = Running(uri, transformer, partial, poller)
    }

    private fun finish(uri: String, state: ProxyState) {
        running?.poller?.cancel()
        running = null
        setState(uri, state)
        startNext()
    }

    private fun setState(uri: String, state: ProxyState) = _states.update { it + (uri to state) }

    private suspend fun fileExists(fileUri: String): Boolean =
        withContext(ioDispatcher) { Uri.parse(fileUri).path?.let { File(it).exists() } == true }

    private companion object {
        const val DIR = "proxies"
        const val PROGRESS_POLL_MS = 500L
    }
}
