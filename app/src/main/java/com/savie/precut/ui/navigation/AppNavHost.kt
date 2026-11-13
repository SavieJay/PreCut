package com.savie.precut.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import com.savie.precut.model.account.AuthState
import com.savie.precut.model.account.needsSignIn
import com.savie.precut.ui.auth.SignInScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.savie.precut.data.ShareInbox
import com.savie.precut.data.TemplateLinkInbox
import com.savie.precut.ui.editor.EditorScreen
import com.savie.precut.ui.export.ExportScreen
import com.savie.precut.ui.home.HomeScreen
import com.savie.precut.ui.onboarding.OnboardingScreen
import com.savie.precut.ui.pro.PaywallScreen
import com.savie.precut.ui.settings.BrandKitScreen
import com.savie.precut.ui.settings.LicencesScreen
import com.savie.precut.ui.settings.SettingsScreen
import com.savie.precut.ui.templates.TemplateScreen

/**
 * [auth] gates every screen behind sign-in (Phase 30): signed out (or signed out by the server)
 * shows Sign-in only; signing in moves on to onboarding the first time ([onboardingDone]), else
 * Projects. A build without account keys ([AuthState.NotSetUp]) never asks.
 */
@Composable
fun AppNavHost(
    shareInbox: ShareInbox,
    showOnboarding: Boolean,
    templateLinks: TemplateLinkInbox? = null,
    auth: StateFlow<AuthState> = MutableStateFlow(AuthState.NotSetUp),
    onboardingDone: () -> Boolean = { !showOnboarding },
) {
    val navController = rememberNavController()
    val authState by auth.collectAsStateWithLifecycle()
    val signedOut = authState.needsSignIn
    // Template links (Phase 28): fetched, kept in Templates, then opened; they wait for sign-in.
    if (!signedOut) templateLinks?.let { TemplateLinkOpener(it, navController) }
    // Signing in or out swaps the whole back stack.
    LaunchedEffect(signedOut) {
        val onSignIn = navController.currentDestination?.hasRoute<SignInRoute>() == true
        when {
            signedOut && !onSignIn -> navController.navigate(SignInRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            !signedOut && onSignIn -> {
                val next: Any = if (onboardingDone()) HomeRoute else OnboardingRoute
                navController.navigate(next) { popUpTo<SignInRoute> { inclusive = true } }
            }
        }
    }
    // Shared media becomes a new project on the Projects screen, wherever the user was.
    LaunchedEffect(shareInbox) {
        shareInbox.arrivals.collect { navController.popBackStack(HomeRoute, inclusive = false) }
    }
    val start: Any = remember {
        when {
            signedOut -> SignInRoute
            showOnboarding -> OnboardingRoute
            else -> HomeRoute
        }
    }
    NavHost(navController = navController, startDestination = start) {
        composable<SignInRoute> { SignInScreen() }
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
                onOpenAiEdit = { projectId -> navController.navigate(EditorRoute(projectId, aiEdit = true)) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
                onOpenTemplate = { navController.navigate(TemplateRoute(it)) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(
                onBack = { navController.popBackStack() },
                onExport = { projectId, settings, kind -> navController.navigate(ExportRoute(projectId, settings, kind)) },
                onOpenPaywall = { feature -> navController.navigate(PaywallRoute(feature)) },
                // A short replaces the editor it came from: Back goes to Projects.
                onOpenProject = { projectId -> navController.navigate(EditorRoute(projectId)) { popUpTo<HomeRoute>() } },
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
