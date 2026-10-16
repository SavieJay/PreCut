package com.savie.precut.media.text

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.savie.precut.R
import com.savie.precut.model.text.BundledFont
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Typefaces for text: Roboto Flex, the bundled fonts, and fonts imported into app storage. */
@Singleton
class TextFonts @Inject constructor(@ApplicationContext private val context: Context) {

    private val cache = ConcurrentHashMap<String, Typeface>()

    /** The typeface for [fontId] in the requested style (synthesised when the font has no bold/italic). */
    fun typeface(fontId: String?, bold: Boolean, italic: Boolean): Typeface {
        val base = cache.getOrPut(fontId ?: DEFAULT) { load(fontId) ?: Typeface.DEFAULT }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return if (style == Typeface.NORMAL) base else Typeface.create(base, style)
    }

    /** Fonts imported so far, newest first. */
    fun imported(): List<ImportedFont> =
        File(context.filesDir, DIR).listFiles().orEmpty().filter { it.isFile }.sortedByDescending { it.lastModified() }.map { file ->
            ImportedFont(BundledFont.IMPORTED_PREFIX + file.name, file.nameWithoutExtension.substringAfter('_'), file)
        }

    /**
     * Copies a picked font into app storage and checks it loads; its id, or null when it isn't a
     * usable .ttf/.otf. Call off the main thread.
     */
    fun import(source: android.net.Uri): String? {
        val displayName = try {
            context.contentResolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: RuntimeException) {
            null
        } ?: "Font.ttf"
        val extension = displayName.substringAfterLast('.', "ttf").lowercase().takeIf { it == "ttf" || it == "otf" } ?: "ttf"
        val safeName = displayName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9 _-]"), "").ifBlank { "Font" }
        val target = File(File(context.filesDir, DIR).apply { mkdirs() }, "${System.currentTimeMillis()}_$safeName.$extension")
        return try {
            context.contentResolver.openInputStream(source)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
            Typeface.createFromFile(target)
            BundledFont.IMPORTED_PREFIX + target.name
        } catch (_: java.io.IOException) {
            target.delete()
            null
        } catch (_: RuntimeException) {
            target.delete()
            null
        }
    }

    /** Where an imported font with this id is kept. */
    fun importedFile(fontId: String): File? =
        fontId.takeIf { it.startsWith(BundledFont.IMPORTED_PREFIX) }?.let { File(File(context.filesDir, DIR), it.removePrefix(BundledFont.IMPORTED_PREFIX)) }

    private fun load(fontId: String?): Typeface? {
        val bundled = when (BundledFont.of(fontId)) {
            BundledFont.BEBAS_NEUE -> R.font.bebas_neue
            BundledFont.PLAYFAIR -> R.font.playfair_display
            BundledFont.PACIFICO -> R.font.pacifico
            BundledFont.SPACE_MONO -> R.font.space_mono
            BundledFont.CAVEAT -> R.font.caveat
            BundledFont.ANTON -> R.font.anton
            BundledFont.LOBSTER -> R.font.lobster
            null -> if (fontId == null) R.font.roboto_flex else null
        }
        if (bundled != null) return ResourcesCompat.getFont(context, bundled)
        val file = fontId?.let(::importedFile)?.takeIf { it.exists() } ?: return null
        return try {
            Typeface.createFromFile(file)
        } catch (_: RuntimeException) {
            null // Not a usable font file.
        }
    }

    companion object {
        private const val DEFAULT = "default"
        /** App-private folder for imported fonts. */
        const val DIR = "fonts"
    }
}

/** A font imported from the device: its id (`file:<name>`), the name shown, and its file. */
data class ImportedFont(val id: String, val name: String, val file: File)
