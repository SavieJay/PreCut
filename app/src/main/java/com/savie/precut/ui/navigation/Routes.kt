package com.savie.precut.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** First-run onboarding (Phase 24), and Settings → Replay intro. */
@Serializable
data object OnboardingRoute

@Serializable
data class EditorRoute(val projectId: String)

/** [settings] is an [com.savie.precut.model.export.ExportSettings] as JSON; [kind] an `ExportKind` name. */
@Serializable
data class ExportRoute(val projectId: String, val settings: String = "", val kind: String = "VIDEO")

@Serializable
data object SettingsRoute

/** PreCut Pro's paywall (Phase 25); [feature] is the `ProFeature` name that opened it, if any. */
@Serializable
data class PaywallRoute(val feature: String? = null)

@Serializable
data object LicencesRoute

@Serializable
data class TemplateRoute(val templateId: String)
