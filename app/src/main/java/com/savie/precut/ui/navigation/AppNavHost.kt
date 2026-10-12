package com.savie.precut.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.savie.precut.ui.editor.EditorScreen
import com.savie.precut.ui.home.HomeScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(onOpenEditor = { projectId -> navController.navigate(EditorRoute(projectId)) })
        }
        composable<EditorRoute> {
            EditorScreen(onBack = { navController.popBackStack() })
        }
    }
}
