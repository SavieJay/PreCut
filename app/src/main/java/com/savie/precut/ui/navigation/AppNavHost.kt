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
import com.savie.precut.ui.onboarding.OnboardingScreen
import com.savie.precut.ui.pro.PaywallScreen
import com.savie.precut.ui.settings.BrandKitScreen
import com.savie.precut.ui.settings.LicencesScreen
import com.savie.precut.ui.settings.SettingsScreen
import com.savie.precut.ui.templates.TemplateScreen

@Composable
fun AppNavHost(shareInbox: ShareInbox, showOnboarding: Boolean) {
    val navController = rememberNavController()
    // Shared media becomes a new project on the Projects screen, wherever the user was.
    LaunchedEffect(shareInbox) {
        shareInbox.arrivals.collect { navController.popBackStack(HomeRoute, inclusive = false) }
    }
    NavHost(navController = navController, startDestination = if (showOnboarding) OnboardingRoute else HomeRoute) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onDone = { openPaywall ->
                    // From a first run, Projects replaces onboarding; from Replay intro, back to where it was.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } }
                    }
                    // "Start free trial" (A5) goes on to the paywall over Projects.
                    if (openPaywall) navController.navigate(PaywallRoute())
                },
            )
        }
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
                onOpenPaywall = { feature -> navController.navigate(PaywallRoute(feature)) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLicences = { navController.navigate(LicencesRoute) },
                onReplayIntro = { navController.navigate(OnboardingRoute) },
                onGetPro = { navController.navigate(PaywallRoute()) },
                onBrandKit = { navController.navigate(BrandKitRoute) },
            )
        }
        composable<TemplateRoute> {
            TemplateScreen(
                onBack = { navController.popBackStack() },
                // The new project replaces the template page: Back from the editor returns to Projects.
                onOpenEditor = { projectId -> navController.navigate(EditorRoute(projectId)) { popUpTo<HomeRoute>() } },
            )
        }
        composable<BrandKitRoute> {
            BrandKitScreen(onBack = { navController.popBackStack() })
        }
        composable<PaywallRoute> {
            PaywallScreen(onClose = { navController.popBackStack() })
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
