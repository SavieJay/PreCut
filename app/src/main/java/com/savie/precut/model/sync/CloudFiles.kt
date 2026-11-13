package com.savie.precut.model.sync

import com.savie.precut.model.Project
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.mapMediaUris
import com.savie.precut.model.mediaUris
import com.savie.precut.model.text.BundledFont

/**
 * A project's files in the cloud (Phase 30D). In the JSON the server keeps, every media Uri reads
 * `cloud:<sha256>.<ext>`, so any phone can find the file; each phone maps it back to a file it has.
 */
data class CloudRef(val sha256: String, val extension: String?) {
    override fun toString(): String = CLOUD_PREFIX + sha256 + (extension?.let { ".$it" } ?: "")

    companion object {
        private val PATTERN = Regex("^cloud:([0-9a-f]{64})(?:\\.([a-z0-9]{1,8}))?$")

        fun parse(uri: String): CloudRef? = PATTERN.matchEntire(uri)?.let { CloudRef(it.groupValues[1], it.groupValues[2].ifEmpty { null }) }
    }
}

const val CLOUD_PREFIX = "cloud:"

/** A file's extension from its Uri or name, lower case and short, or null. */
fun extensionOf(uriOrName: String): String? =
    uriOrName.substringBefore('?').substringAfterLast('/').substringAfterLast('.', "").lowercase()
        .takeIf { it.isNotEmpty() && it.length <= 8 && it.all(Char::isLetterOrDigit) }

/** Imported fonts the project's texts use (`file:<name>` ids, files in the app's fonts folder). */
fun Project.importedFontIds(): Set<String> =
    textOverlays.mapNotNull { it.fontId }.filter { it.startsWith(BundledFont.IMPORTED_PREFIX) }.toSet()

/** Custom LUTs the project's clips use (`custom:<key>` filters, files in the app's luts folder). */
fun Project.customLutIds(): Set<String> =
    (clips + overlays.map { it.clip }).mapNotNull { it.look.filterId }.filter { it.startsWith(FilterCatalog.CUSTOM_PREFIX) }.toSet()

/** The cloud references in a project from the cloud. */
fun Project.cloudRefs(): Set<CloudRef> = mediaUris().mapNotNull(CloudRef::parse).toSet()

/** Every media Uri of the project that's a real file (not already a cloud reference). */
fun Project.localMediaUris(): Set<String> = mediaUris().filter { it.isNotBlank() && !it.startsWith(CLOUD_PREFIX) }.toSet()

/**
 * The project as the cloud keeps it: each Uri [refs] knows becomes its cloud reference; one it
 * doesn't (a file that couldn't be read) stays as it is and shows as missing elsewhere.
 */
fun Project.toCloud(refs: Map<String, CloudRef>): Project = mapMediaUris { uri -> refs[uri]?.toString() ?: uri }

/** The cloud's project on this phone: each cloud reference becomes the local Uri [local] gives it. */
fun Project.fromCloud(local: (CloudRef) -> String): Project = mapMediaUris { uri -> CloudRef.parse(uri)?.let(local) ?: uri }
