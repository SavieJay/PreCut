package com.savie.precut.model.export

import com.savie.precut.model.MediaInfo
import com.savie.precut.model.Project
import com.savie.precut.model.clipFromMedia

/** How long the "Made with PreCut" end card shows (Phase 28). */
const val END_CARD_MS = 2_000L

/** The project to export with the end card picture ([uri], [info]) after its last clip. */
fun Project.withEndCard(uri: String, info: MediaInfo): Project =
    copy(clips = clips + clipFromMedia("end-card", uri, info, END_CARD_MS))
