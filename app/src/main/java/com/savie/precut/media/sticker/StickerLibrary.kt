package com.savie.precut.media.sticker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.edit
import com.savie.precut.model.sticker.Sticker
import com.savie.precut.model.sticker.StickerKind
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** A picture in "My images" (S-01c). */
data class LibraryImage(val file: File, val animated: Boolean) {
    val uri: String get() = Uri.fromFile(file).toString()
}

/**
 * The sticker sheet's own storage: pictures imported into "My images" (kept across projects),
 * recently used emoji, and drawings. A placed picture is copied into the swept `imports` folder,
 * so removing it from "My images" never breaks a project. Call file work off the main thread.
 */
@Singleton
class StickerLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: StickerRenderer,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val libraryDir get() = File(context.filesDir, LIBRARY_DIR)

    /** "My images", newest first. */
    fun images(): List<LibraryImage> =
        libraryDir.listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }
            .map { LibraryImage(it, it.extension == "gif") }

    /** Copies a picked picture into "My images"; null when it isn't a readable image. */
    fun import(source: Uri): LibraryImage? {
        val bytes = try {
            context.contentResolver.openInputStream(source)?.use { it.readBytes() }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } ?: return null
        val gif = renderer.gifInfo(bytes)
        val extension = when {
            gif != null -> "gif"
            else -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0) return null
                when (bounds.outMimeType) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    else -> "jpg"
                }
            }
        }
        val file = File(libraryDir.apply { mkdirs() }, "${UUID.randomUUID()}.$extension")
        return try {
            file.writeBytes(bytes)
            LibraryImage(file, animated = (gif?.frameCount ?: 0) > 1)
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    fun remove(image: LibraryImage) {
        image.file.delete()
    }

    /** A project's own copy of [image], as a sticker; null when copying fails. */
    fun place(image: LibraryImage): Sticker? {
        val copy = File(File(context.filesDir, PLACED_DIR).apply { mkdirs() }, "sticker_${UUID.randomUUID()}.${image.file.extension}")
        return try {
            image.file.copyTo(copy)
            Sticker(StickerKind.IMAGE, Uri.fromFile(copy).toString(), aspectOf(copy), animated = image.animated)
        } catch (_: IOException) {
            copy.delete()
            null
        }
    }

    /** Saves a finished drawing (S-02) as a PNG sticker. */
    fun saveDrawing(bitmap: Bitmap): Sticker? {
        val file = File(File(context.filesDir, DRAWINGS_DIR).apply { mkdirs() }, "drawing_${UUID.randomUUID()}.png")
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Sticker(StickerKind.DRAWING, Uri.fromFile(file).toString(), bitmap.width.toFloat() / bitmap.height)
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    /** Recently used emoji, newest first. */
    fun recentEmoji(): List<String> = prefs.getString(KEY_RECENT, null)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    fun useEmoji(emoji: String) {
        val recent = (listOf(emoji) + recentEmoji().filter { it != emoji }).take(MAX_RECENT)
        prefs.edit { putString(KEY_RECENT, recent.joinToString(SEPARATOR)) }
    }

    private fun aspectOf(file: File): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) bounds.outWidth.toFloat() / bounds.outHeight else 1f
    }

    private companion object {
        const val PREFS = "stickers"
        const val KEY_RECENT = "recent_emoji"
        const val SEPARATOR = "\n"
        const val MAX_RECENT = 16
        /** "My images"; not swept, the user removes pictures from it. */
        const val LIBRARY_DIR = "sticker_library"
        /** Swept when no project uses a file (see APP_MEDIA_DIRS). */
        const val PLACED_DIR = "imports"
        const val DRAWINGS_DIR = "drawings"
    }
}
