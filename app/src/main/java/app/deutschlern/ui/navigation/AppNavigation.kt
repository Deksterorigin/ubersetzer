package app.deutschlern.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.deutschlern.R
import app.deutschlern.ui.onboarding.OnboardingExit
import app.deutschlern.ui.onboarding.OnboardingScreen
import app.deutschlern.ui.review.ReviewScreen
import app.deutschlern.ui.trainer.TrainerScreen
import app.deutschlern.ui.settings.SettingsScreen
import app.deutschlern.ui.translate.TranslateScreen
import app.deutschlern.ui.words.WordsScreen

private const val KEY_SELECTED_CHECK_ID = "selected_check_id"

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Translate.route
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val dividerColor = MaterialTheme.colorScheme.outline
    val showBottomBar = currentRoute in Screen.bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.drawBehind {
                        drawLine(
                            color = dividerColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            alwaysShowLabel = false,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        // Not the graph's start destination: onboarding can be that, and it is gone by now
                                        popUpTo(Screen.Translate.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = stringResource(screen.titleRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(screen.titleRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            composable(
                route = Screen.Onboarding.route,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(200)) },
                popExitTransition = { fadeOut(tween(200)) }
            ) {
                OnboardingScreen(
                    onFinished = { exit -> navController.leaveOnboarding(exit) }
                )
            }
            composable(Screen.Translate.route) {
                TranslateScreen(
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.Words.route) {
                WordsScreen()
            }
            composable(Screen.Dialogues.route) {
                app.deutschlern.ui.dialog.DialoguesScreen(
                    onNavigateToRecording = { navController.navigate(Screen.DialogRecording.createRoute()) },
                    onResumeRecording = { dialogId -> navController.navigate(Screen.DialogRecording.createRoute(dialogId)) },
                    onNavigateToDetail = { dialogId -> navController.navigate(Screen.DialogDetail.createRoute(dialogId)) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(
                route = Screen.DialogRecording.route,
                arguments = listOf(
                    androidx.navigation.navArgument("dialogId") {
                        type = androidx.navigation.NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) {
                app.deutschlern.ui.dialog.DialogRecordingScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onRecordingSaved = { dialogId ->
                        navController.popBackStack()
                        navController.navigate(Screen.DialogDetail.createRoute(dialogId))
                    }
                )
            }
            composable(
                route = Screen.DialogDetail.route,
                arguments = listOf(
                    androidx.navigation.navArgument("dialogId") { type = androidx.navigation.NavType.LongType }
                )
            ) {
                app.deutschlern.ui.dialog.DialogDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onResumeRecording = { id ->
                        navController.navigate(Screen.DialogRecording.createRoute(id))
                    },
                    onNavigateToTask = { dialogId, taskId ->
                        navController.navigate(Screen.DialogTask.createRoute(dialogId, taskId))
                    }
                )
            }
            composable(
                route = Screen.DialogTask.route,
                arguments = listOf(
                    androidx.navigation.navArgument("dialogId") { type = androidx.navigation.NavType.LongType },
                    androidx.navigation.navArgument("taskId") { type = androidx.navigation.NavType.LongType }
                )
            ) {
                app.deutschlern.ui.dialog.DialogTaskScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Check.route) { entry ->
                val savedCheckId by entry.savedStateHandle
                    .getStateFlow<Long?>(KEY_SELECTED_CHECK_ID, null)
                    .collectAsStateWithLifecycle()
                app.deutschlern.ui.check.CheckScreen(
                    onNavigateToHistory = { navController.navigate(Screen.CheckHistory.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    savedCheckId = savedCheckId,
                    onSavedCheckOpened = { entry.savedStateHandle[KEY_SELECTED_CHECK_ID] = null }
                )
            }
            composable(Screen.CheckHistory.route) {
                app.deutschlern.ui.check.CheckHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSelectCheck = { checkId ->
                        navController.previousBackStackEntry?.savedStateHandle?.set(KEY_SELECTED_CHECK_ID, checkId)
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Review.route) {
                ReviewScreen(
                    onOpenTrainer = { mode, pool -> navController.navigate(Screen.Trainer.createRoute(mode, pool)) }
                )
            }
            composable(Screen.Trainer.route) {
                TrainerScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onShowWelcomeAgain = { navController.navigate(Screen.Onboarding.route) }
                )
            }
        }
    }
}

private fun NavHostController.leaveOnboarding(exit: OnboardingExit) {
    // Something underneath means a replay from Settings: go back there whatever was chosen
    if (previousBackStackEntry != null) {
        popBackStack()
        return
    }
    navigate(Screen.Translate.route) {
        popUpTo(Screen.Onboarding.route) { inclusive = true }
    }
    if (exit == OnboardingExit.SETTINGS) {
        navigate(Screen.Settings.route)
    }
}
