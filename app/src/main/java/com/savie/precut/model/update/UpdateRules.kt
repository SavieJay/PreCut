package com.savie.precut.model.update

import kotlinx.serialization.Serializable

/**
 * Forced updates (Phase 23): what the server says about app versions (docs/RELEASING.md). Zeros
 * never block or offer anything.
 */
@Serializable
data class AppConfig(
    /** Older versions can't be used until they update. */
    val minSupportedVersionCode: Int = 0,
    /** The newest version released; older ones are offered it. */
    val latestVersionCode: Int = 0,
    /** The newest version's name ("1.2.0"), shown beside this one's; null when not set. */
    val latestVersionName: String? = null,
    /** Shown on the blocking screen instead of the app's own words. */
    val forceMessage: String? = null,
    /** "What's new" after updating to [latestVersionCode]. */
    val whatsNew: List<String> = emptyList(),
    /** Where a copy not installed from Google Play gets the new version; null for the Play listing. */
    val downloadUrl: String? = null,
)

/** What the app shows about updates. */
sealed interface UpdatePrompt {
    data object None : UpdatePrompt

    /** A newer version is out: a banner on Home offers it. */
    data object Available : UpdatePrompt

    /** This version may no longer be used: the blocking screen ([latestVersionName] when known). */
    data class Required(val message: String?, val downloadUrl: String?, val latestVersionName: String? = null) : UpdatePrompt
}

/**
 * The prompt for app [versionCode] under [config] (the server's, or the last one kept; null when
 * there has never been one, offline included, which never blocks). While [exporting], a required
 * update waits: an export is never interrupted.
 */
fun updatePrompt(versionCode: Int, config: AppConfig?, exporting: Boolean): UpdatePrompt = when {
    config == null -> UpdatePrompt.None
    versionCode < config.minSupportedVersionCode ->
        if (exporting) {
            UpdatePrompt.None
        } else {
            UpdatePrompt.Required(
                config.forceMessage?.takeIf { it.isNotBlank() },
                config.downloadUrl?.takeIf { it.isNotBlank() },
                config.latestVersionName?.takeIf { it.isNotBlank() },
            )
        }
    versionCode < config.latestVersionCode -> UpdatePrompt.Available
    else -> UpdatePrompt.None
}

/**
 * "What's new" to show once after an update: when this [versionCode] is newer than the one that
 * last ran ([lastRunVersionCode]; null on a fresh install, which shows nothing) and [config] has
 * bullets for it. Bullets written for a newer release than this one aren't shown.
 */
fun whatsNewAfterUpdate(versionCode: Int, lastRunVersionCode: Int?, config: AppConfig?): List<String> = when {
    lastRunVersionCode == null || lastRunVersionCode >= versionCode -> emptyList()
    config == null || config.latestVersionCode > versionCode -> emptyList()
    else -> config.whatsNew.filter { it.isNotBlank() }
}

/**
 * Whether Home's banner offers the update: a newer version is out ([prompt] is Available) and the
 * banner wasn't closed for it ([dismissedForVersionCode] is the latest version it was closed for;
 * a newer release brings it back).
 */
fun updateBannerShown(prompt: UpdatePrompt, latestVersionCode: Int, dismissedForVersionCode: Int?): Boolean =
    prompt == UpdatePrompt.Available && (dismissedForVersionCode == null || dismissedForVersionCode < latestVersionCode)

/** At most one check per [intervalMs] (15 minutes): on a cold start and on coming back to the app. */
fun shouldCheck(nowMs: Long, lastCheckMs: Long?, intervalMs: Long = CHECK_INTERVAL_MS): Boolean =
    lastCheckMs == null || nowMs - lastCheckMs >= intervalMs || nowMs < lastCheckMs

const val CHECK_INTERVAL_MS = 15 * 60 * 1_000L
