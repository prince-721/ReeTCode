package com.reeltracker.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.*
import com.reeltracker.ui.screens.*
import com.reeltracker.viewmodel.HomeUiState
import com.reeltracker.viewmodel.ReelTrackerViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val FOCUS_MODES = "focus_modes"
    const val FOCUS_MODE_EDITOR = "focus_mode_editor"
    const val STUDY_MODE = "study_mode"
}

@Composable
fun NavGraph(
    uiState: HomeUiState,
    viewModel: ReelTrackerViewModel
) {
    val navController = rememberNavController()
    // null = still loading; false = not done; true = done
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsStateWithLifecycle()

    val startDestination = when (hasCompletedOnboarding) {
        null -> return  // Still loading from DataStore — render nothing
        false -> Routes.ONBOARDING
        else -> Routes.HOME
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(280)) +
                    fadeIn(tween(280))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(280)) +
                    fadeOut(tween(280))
        },
        popEnterTransition = {
            slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(280)) +
                    fadeIn(tween(280))
        },
        popExitTransition = {
            slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(280)) +
                    fadeOut(tween(280))
        }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToStudyMode = { navController.navigate(Routes.STUDY_MODE) },
                onNavigateToHistory = { navController.navigate(Routes.HISTORY) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.HISTORY) {
            HistoryScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                onNavigateToFocusModes = { navController.navigate(Routes.FOCUS_MODES) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.STUDY_MODE) {
            StudyModeScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.FOCUS_MODES) {
            FocusModesScreen(
                viewModel = viewModel,
                onNavigateToEditor = { modeId ->
                    navController.navigate("${Routes.FOCUS_MODE_EDITOR}/$modeId")
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Routes.FOCUS_MODE_EDITOR}/{modeId}",
            arguments = listOf(navArgument("modeId") { type = NavType.LongType })
        ) { backStackEntry ->
            val modeId = backStackEntry.arguments?.getLong("modeId") ?: 0L
            FocusModeEditorScreen(
                viewModel = viewModel,
                modeId = modeId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
