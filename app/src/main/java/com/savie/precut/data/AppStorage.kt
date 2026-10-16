package com.savie.precut.data

import android.content.Context
import android.os.StatFs
import com.savie.precut.di.IoDispatcher
import com.savie.precut.media.ProxyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Free space and PreCut's rebuildable cache (preview proxies, waveforms, leftovers). */
interface AppStorage {
    fun freeBytes(): Long
    suspend fun cacheBytes(): Long
    /** Deletes what can be rebuilt; call while no editor or export is running. */
    suspend fun clearCache()
}

class AndroidAppStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val proxies: ProxyManager,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AppStorage {

    override fun freeBytes(): Long = StatFs(context.filesDir.path).availableBytes

    override suspend fun cacheBytes(): Long = withContext(io) {
        context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    override suspend fun clearCache() {
        proxies.trim(emptySet())
        withContext(io) {
            CLEARABLE.forEach { File(context.cacheDir, it).deleteRecursively() }
        }
    }

    private companion object {
        /** Waveform peaks, half-made reversed clips and exports that never reached the gallery. */
        val CLEARABLE = listOf("waveforms", "reverse", "exports")
    }
}
