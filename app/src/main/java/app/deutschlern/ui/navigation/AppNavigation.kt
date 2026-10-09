package app.deutschlern.ui.navigation

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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.deutschlern.R
import app.deutschlern.ui.review.ReviewScreen
import app.deutschlern.ui.settings.SettingsScreen
import app.deutschlern.ui.translate.TranslateScreen
import app.deutschlern.ui.words.WordsScreen

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
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
                                        popUpTo(navController.graph.findStartDestination().id) {
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
            startDestination = Screen.Translate.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                    onNavigateToRecording = { navController.navigate(Screen.DialogRecording.route) },
                    onNavigateToDetail = { dialogId -> navController.navigate(Screen.DialogDetail.createRoute(dialogId)) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.DialogRecording.route) {
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
            composable(Screen.Check.route) {
                app.deutschlern.ui.check.CheckScreen(
                    onNavigateToHistory = { navController.navigate(Screen.CheckHistory.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }
            composable(Screen.CheckHistory.route) {
                app.deutschlern.ui.check.CheckHistoryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onSelectCheck = { checkId ->
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.Review.route) {
                ReviewScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
