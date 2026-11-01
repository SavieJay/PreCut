package com.savie.precut.export

import com.savie.precut.model.MediaInfo

/** Makes the "Made with PreCut" end card picture for a frame of [width] × [height] (Phase 28). */
fun interface EndCards {
    /** The picture's Uri and media facts, or null when it couldn't be made (the export goes on without it). */
    suspend fun make(width: Int, height: Int): Pair<String, MediaInfo>?

    companion object {
        /** No end card (tests). */
        val NONE = EndCards { _, _ -> null }
    }
}
