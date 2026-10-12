package com.savie.precut.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class EditorRoute(val projectId: String)

@Serializable
data class ExportRoute(val projectId: String)
