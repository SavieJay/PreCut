package com.savie.precut.ui.navigation

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
}
