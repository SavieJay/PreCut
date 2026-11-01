package com.savie.precut.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

/** First-run onboarding (Phase 24), and Settings → Replay intro. */
@Serializable
data object OnboardingRoute

/** [aiEdit] opens the AI edit sheet once the project has loaded (Home's AI Edit, Phase 27). */
@Serializable
data class EditorRoute(val projectId: String, val aiEdit: Boolean = false)

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

/** Settings → Brand kit (Phase 26B). */
@Serializable
data object BrandKitRoute

@Serializable
data class TemplateRoute(val templateId: String)
