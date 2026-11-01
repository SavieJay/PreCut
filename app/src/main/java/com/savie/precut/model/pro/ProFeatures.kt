package com.savie.precut.model.pro

/**
 * PreCut Pro's features (Phase 26B; AI edit from Phase 27) and each one's free taste per month,
 * where cost allows. AI edit's taste is counted by the server (its free runs), the rest on the
 * phone. Every on-phone creative tool is free and isn't listed here (Phase 26A).
 */
enum class ProFeature(val freeUsesPerMonth: Int) {
    AI_EDIT(0),
    TRANSCRIPT_EDIT(3),
    LONG_TO_SHORTS(2),
    MULTI_FORMAT_EXPORT(2),
    BRAND_KIT(0),
    TRANSLATE_CAPTIONS(2),
    HOOK_COACH(3),
    SMART_BROLL(5),
}

/** Whether a Pro feature can be used now. */
sealed interface ProAccess {
    /** Pro: no limit. */
    data object Unlocked : ProAccess

    /** Free, with [usesLeft] uses left this month (shown as "2 free uses left"). */
    data class Taste(val usesLeft: Int) : ProAccess

    /** Free and used up (or no taste): tapping opens the paywall. */
    data object Locked : ProAccess
}

/** [feature]'s access for a Pro or free user who has used it [usedThisMonth] times this month. */
fun proAccess(feature: ProFeature, isPro: Boolean, usedThisMonth: Int): ProAccess = when {
    isPro -> ProAccess.Unlocked
    usedThisMonth < feature.freeUsesPerMonth -> ProAccess.Taste(feature.freeUsesPerMonth - usedThisMonth)
    else -> ProAccess.Locked
}
