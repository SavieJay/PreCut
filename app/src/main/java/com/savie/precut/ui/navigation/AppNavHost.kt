package com.savie.precut.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.savie.precut.data.ShareInbox
import com.savie.precut.ui.editor.EditorScreen
import com.savie.precut.ui.export.ExportScreen
import com.savie.precut.ui.home.HomeScreen
import com.savie.precut.ui.settings.LicencesScreen
import com.savie.precut.ui.settings.SettingsScreen
import com.savie.precut.ui.templates.TemplateScreen

@Composable
fun AppNavHost(shareInbox: ShareInbox) {
    val navController = rememberNavController()
    // Shared media becomes a new project on the Projects screen, wherever the user was.
    LaunchedEffect(shareInbox) {
        shareInbox.arrivals.collect { navController.popBackStack(HomeRoute, inclusive = false) }
    }
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenEditor = { projectId -> navController.navigate(EditorRoute(projectId)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTemplate = { navController.navigate(TemplateRoute(it)) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(
                onBack = { navController.popBackStack() },
                onExport = { projectId, settings, kind -> navController.navigate(ExportRoute(projectId, settings, kind)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() }, onLicences = { navController.navigate(LicencesRoute) })
        }
        composable<TemplateRoute> {
            TemplateScreen(
                onBack = { navController.popBackStack() },
                // The new project replaces the template page: Back from the editor returns to Projects.
                onOpenEditor = { projectId -> navController.navigate(EditorRoute(projectId)) { popUpTo<HomeRoute>() } },
            )
        }
        composable<LicencesRoute> {
            LicencesScreen(onBack = { navController.popBackStack() })
        }
        composable<ExportRoute> {
            ExportScreen(
                onClose = { navController.popBackStack() },
                onHome = { navController.popBackStack(HomeRoute, inclusive = false) },
            )
        }
    }
}
