package com.z.diabetescompanion.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.ui.AppViewModelProvider
import com.z.diabetescompanion.ui.LocalSession
import com.z.diabetescompanion.ui.auth.CompleteProfileScreen
import com.z.diabetescompanion.ui.auth.ForgotPasswordScreen
import com.z.diabetescompanion.ui.auth.LoginScreen
import com.z.diabetescompanion.ui.auth.SignUpScreen
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.shell.AdminShell
import com.z.diabetescompanion.ui.shell.CaregiverShell
import com.z.diabetescompanion.ui.shell.DoctorShell
import com.z.diabetescompanion.ui.shell.PatientShell
import com.z.diabetescompanion.ui.welcome.WelcomeScreen

object Routes {
    const val AUTH_GRAPH = "auth"
    const val WELCOME = "auth/welcome"
    const val LOGIN = "auth/login"
    const val SIGN_UP = "auth/sign_up"
    const val FORGOT_PASSWORD = "auth/forgot_password"
    const val MAIN = "main"
}

/**
 * Root graph: auth flow <-> signed-in shell. Switching is driven purely by session state,
 * so sign-in, sign-up, sign-out and token expiry all route the same way.
 */
@Composable
fun AppNavHost(
    rootViewModel: RootViewModel,
    navController: NavHostController = rememberNavController(),
) {
    val sessionState by rootViewModel.sessionState.collectAsStateWithLifecycle()
    val startDestination = remember {
        if (rootViewModel.isSignedInAtLaunch) Routes.MAIN else Routes.AUTH_GRAPH
    }

    val isSignedIn = sessionState !is SessionState.SignedOut
    val accountBusy by rootViewModel.accountBusy.collectAsStateWithLifecycle()

    LaunchedEffect(isSignedIn, accountBusy) {
        // Creating an account or verifying a sign-in role flips the auth state before the
        // work is finished. Stay on the form until it settles: a rejected role then lands
        // back on a live screen with the typed email and password still there.
        if (accountBusy) return@LaunchedEffect
        val target = if (isSignedIn) Routes.MAIN else Routes.AUTH_GRAPH
        val current = navController.currentDestination ?: return@LaunchedEffect
        if (current.hierarchy.none { it.route == target }) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
            // A sign-in rejected for the wrong role has a message to show, so open the
            // form on top of Welcome rather than leaving the user on the landing page.
            if (!isSignedIn && rootViewModel.signInError.value != null) {
                navController.navigate(Routes.LOGIN)
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(300)) { it / 4 } + fadeIn(tween(300)) },
        exitTransition = { slideOutHorizontally(tween(300)) { -it / 4 } + fadeOut(tween(300)) },
        popEnterTransition = { slideInHorizontally(tween(300)) { -it / 4 } + fadeIn(tween(300)) },
        popExitTransition = { slideOutHorizontally(tween(300)) { it / 4 } + fadeOut(tween(300)) },
    ) {
        navigation(startDestination = Routes.WELCOME, route = Routes.AUTH_GRAPH) {
            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onSignIn = { navController.navigate(Routes.LOGIN) },
                    onCreateAccount = { navController.navigate(Routes.SIGN_UP) },
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    onNavigateToSignUp = {
                        navController.navigate(Routes.SIGN_UP) { popUpTo(Routes.WELCOME) }
                    },
                    onNavigateToForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                )
            }
            composable(Routes.SIGN_UP) {
                SignUpScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLogin = {
                        navController.navigate(Routes.LOGIN) { popUpTo(Routes.WELCOME) }
                    },
                )
            }
            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
            }
        }

        composable(
            route = Routes.MAIN,
            enterTransition = { fadeIn(tween(400)) },
            exitTransition = { fadeOut(tween(250)) },
        ) {
            when (val state = sessionState) {
                is SessionState.SignedOut, SessionState.Loading -> LoadingState(message = "Loading your profile")
                is SessionState.Failed -> ErrorState(message = state.message, onRetry = rootViewModel::retry)
                is SessionState.NeedsProfile -> CompleteProfileScreen(
                    user = state.user,
                    onSignOut = rootViewModel::signOut,
                )
                is SessionState.Ready -> CompositionLocalProvider(LocalSession provides state.session) {
                    when (state.session.effectiveRole) {
                        UserRole.Patient -> PatientShell()
                        UserRole.Doctor -> DoctorShell()
                        UserRole.Caregiver -> CaregiverShell()
                        UserRole.Admin -> AdminShell()
                    }
                }
            }
        }
    }
}
