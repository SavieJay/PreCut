package com.savie.precut.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.savie.precut.ui.editor.EditorScreen
import com.savie.precut.ui.home.HomeScreen
import java.util.UUID

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onNewProject = {
                    // Placeholder id until projects are created from picked media in Phase 1.
                    navController.navigate(EditorRoute(projectId = UUID.randomUUID().toString()))
                },
            )
        }
        composable<EditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditorRoute>()
            EditorScreen(
                projectId = route.projectId,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
