package com.savie.precut.ui.navigation

import kotlinx.serialization.descriptors.elementNames
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun editorRoute_roundTripsThroughSerialization() {
        val route = EditorRoute(projectId = "abc-123")
        val json = Json.encodeToString(EditorRoute.serializer(), route)
        assertEquals(route, Json.decodeFromString(EditorRoute.serializer(), json))
    }

    @Test
    fun projectIdArg_matchesRoutePropertyNames() {
        // requireProjectId() reads the argument by name, so the route properties must keep this name.
        assertEquals(listOf(PROJECT_ID_ARG), EditorRoute.serializer().descriptor.elementNames.toList())
        assertEquals(listOf(PROJECT_ID_ARG), ExportRoute.serializer().descriptor.elementNames.toList())
    }
}
