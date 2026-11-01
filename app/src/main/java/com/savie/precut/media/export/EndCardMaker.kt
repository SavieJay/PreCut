package com.savie.precut.media.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.savie.precut.R
import com.savie.precut.di.IoDispatcher
import com.savie.precut.export.EndCards
import com.savie.precut.model.ClipType
import com.savie.precut.model.MediaInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** The end card: the logo and "Made with PreCut" on the app's dark background, kept per frame size. */
class EndCardMaker @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : EndCards {
    override suspend fun make(width: Int, height: Int): Pair<String, MediaInfo>? = withContext(io) {
        val file = File(File(context.cacheDir, "endcards").apply { mkdirs() }, "end_${width}x$height.png")
        try {
            if (!file.exists()) {
                val bitmap = createBitmap(width, height)
                val canvas = Canvas(bitmap)
                canvas.drawColor(BACKGROUND)
                val side = minOf(width, height)
                val logoSize = side / 5
                ContextCompat.getDrawable(context, R.drawable.ic_logo)?.mutate()?.apply {
                    setTint(ACCENT)
                    setBounds((width - logoSize) / 2, height / 2 - logoSize, (width + logoSize) / 2, height / 2)
                    draw(canvas)
                }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = TEXT
                    textSize = side / 18f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.DEFAULT_BOLD
                }
                canvas.drawText(context.getString(R.string.end_card_text), width / 2f, height / 2f + side / 10f, paint)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            file.toUri().toString() to MediaInfo(ClipType.IMAGE, 0L, width, height, 0)
        } catch (_: IOException) {
            null
        }
    }

    private companion object {
        const val BACKGROUND = 0xFF0E0E10.toInt()
        const val ACCENT = 0xFFFF4D6D.toInt()
        const val TEXT = 0xFFFFFFFF.toInt()
    }
}
