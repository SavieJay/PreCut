package com.savie.precut.ui.navigation

import androidx.lifecycle.SavedStateHandle

/**
 * Reads the `projectId` argument of [EditorRoute] / [ExportRoute]. Type-safe navigation stores each
 * route property under its name; reading it directly (instead of `toRoute()`) keeps ViewModels
 * testable on the JVM, where `toRoute()` needs Android's Bundle.
 */
fun SavedStateHandle.requireProjectId(): String =
    requireNotNull(get<String>(PROJECT_ID_ARG)) { "Missing $PROJECT_ID_ARG route argument" }

const val PROJECT_ID_ARG = "projectId"

/** [EditorRoute]'s `aiEdit`, read by name like [PROJECT_ID_ARG]. */
const val AI_EDIT_ARG = "aiEdit"

/** [ExportRoute]'s settings (JSON) and kind, read by name like [PROJECT_ID_ARG]. */
const val EXPORT_SETTINGS_ARG = "settings"
const val EXPORT_KIND_ARG = "kind"

/** [TemplateRoute]'s `templateId`, read by name like [requireProjectId]. */
fun SavedStateHandle.requireTemplateId(): String =
    requireNotNull(get<String>(TEMPLATE_ID_ARG)) { "Missing $TEMPLATE_ID_ARG route argument" }

const val TEMPLATE_ID_ARG = "templateId"
