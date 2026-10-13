package com.savie.precut.media.look

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import com.savie.precut.di.IoDispatcher
import com.savie.precut.model.look.Cube
import com.savie.precut.model.look.CubeParser
import com.savie.precut.model.look.FilterCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A `.cube` file the user imported. */
data class CustomLut(val id: String, val name: String)

/**
 * LUT cubes by filter id: built-in grades are baked on first use, imported `.cube` files
 * (`files/luts`) are parsed on first use. Thread-safe; called from the GL thread.
 */
@Singleton
class LutCache @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cubes = LruCache<String, Cube>(MAX_CACHED)
    private val dir get() = File(context.filesDir, DIR)

    /** The cube for [filterId], or null if it's unknown or its file is gone. Blocking. */
    fun cube(filterId: String): Cube? {
        cubes.get(filterId)?.let { return it }
        val cube = if (FilterCatalog.isCustom(filterId)) {
            val file = File(dir, filterId.removePrefix(FilterCatalog.CUSTOM_PREFIX) + ".cube")
            try {
                CubeParser.parse(file.readText()).getOrNull()
            } catch (_: IOException) {
                null
            }
        } else {
            FilterCatalog.preset(filterId)?.let { Cube.bake(it.grade) }
        }
        return cube?.also { cubes.put(filterId, it) }
    }

    /** Imported LUTs, oldest first. */
    suspend fun customLuts(): List<CustomLut> = withContext(ioDispatcher) {
        dir.listFiles { file -> file.extension == "cube" }.orEmpty().sortedBy { it.lastModified() }.map { file ->
            val name = File(dir, file.nameWithoutExtension + ".name").takeIf { it.exists() }?.readText() ?: file.nameWithoutExtension
            CustomLut(FilterCatalog.CUSTOM_PREFIX + file.nameWithoutExtension, name)
        }
    }

    /** Validates and copies a `.cube` file into app storage; the result is its filter id. */
    suspend fun import(uri: String): Result<CustomLut> = withContext(ioDispatcher) {
        runCatching {
            val parsed = Uri.parse(uri)
            val text = context.contentResolver.openInputStream(parsed)?.use { it.readBytes().decodeToString() }
                ?: throw IOException("Can't open $uri")
            require(text.length <= MAX_FILE_CHARS) { "LUT file too large" }
            val cube = CubeParser.parse(text).getOrThrow()
            val key = UUID.randomUUID().toString()
            val name = displayName(parsed) ?: "LUT"
            dir.mkdirs()
            File(dir, "$key.cube").writeText(text)
            File(dir, "$key.name").writeText(name)
            val id = FilterCatalog.CUSTOM_PREFIX + key
            cubes.put(id, cube)
            CustomLut(id, name)
        }
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0)?.substringBeforeLast('.') else null }
    } catch (_: RuntimeException) {
        null
    }

    private companion object {
        const val DIR = "luts"
        const val MAX_CACHED = 24

        /** A 65³ cube is about 7 MB of text. */
        const val MAX_FILE_CHARS = 12_000_000
    }
}
