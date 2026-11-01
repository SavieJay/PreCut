package com.savie.precut.ui.editor

import com.savie.precut.data.ProTastes
import com.savie.precut.model.pro.ProAccess
import com.savie.precut.model.pro.ProFeature
import com.savie.precut.model.pro.proAccess
import com.savie.precut.pro.EntitlementRepository

/**
 * Pro gating in the editor (Phase 25 rules): Pro is unlimited, free users get the feature's taste,
 * then the paywall, which only ever opens from the tap on the Pro tool itself.
 */
class EditorPro(
    private val host: EditorHost,
    private val entitlements: EntitlementRepository,
    private val tastes: ProTastes,
) {
    fun access(feature: ProFeature): ProAccess = proAccess(feature, entitlements.isPro.value, tastes.used(feature))

    /** Runs [action] when [feature] may be used now; otherwise opens the paywall for it. */
    fun gate(feature: ProFeature, action: () -> Unit) {
        if (access(feature) == ProAccess.Locked) host.send(EditorEvent.OpenPaywall(feature)) else action()
    }

    /** One use of a free taste (nothing for Pro). */
    fun spend(feature: ProFeature) {
        if (!entitlements.isPro.value) tastes.spend(feature)
    }
}
