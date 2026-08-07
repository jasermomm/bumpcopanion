@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jasermohamed.bumpcompanion.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*
import com.jasermohamed.bumpcompanion.R
import com.jasermohamed.bumpcompanion.ui.bumps.BumpsRoute
import com.jasermohamed.bumpcompanion.ui.calibration.CalibrationRoute
import com.jasermohamed.bumpcompanion.ui.drives.DrivesRoute
import com.jasermohamed.bumpcompanion.ui.home.HomeRoute
import com.jasermohamed.bumpcompanion.ui.review.ReviewRoute
import com.jasermohamed.bumpcompanion.ui.settings.SettingsRoute

private object Routes {
    const val HOME = "home"
    const val BUMPS = "bumps"
    const val DRIVES = "drives"
    const val SETTINGS = "settings"
    const val REVIEW = "review"
    const val CALIBRATION = "calibration"
}

private data class Destination(val route: String, val label: Int, val icon: ImageVector)

@Composable
fun BumpCompanionRoot() {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    val bottomDestinations = remember {
        listOf(
            Destination(Routes.HOME, R.string.home, Icons.Outlined.Home),
            Destination(Routes.BUMPS, R.string.bumps, Icons.Outlined.Speed),
            Destination(Routes.DRIVES, R.string.drives, Icons.Outlined.Route),
            Destination(Routes.SETTINGS, R.string.settings, Icons.Outlined.Settings),
        )
    }
    val showBottom = current?.route in bottomDestinations.map { it.route }
    Scaffold(
        bottomBar = {
            if (showBottom) {
                NavigationBar {
                    bottomDestinations.forEach { destination ->
                        val selected = current?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            }
        },
    ) { rootPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(bottom = rootPadding.calculateBottomPadding()),
        ) {
            composable(Routes.HOME) {
                HomeRoute(onReviewCandidates = { navController.navigate(Routes.REVIEW) })
            }
            composable(Routes.BUMPS) { BumpsRoute() }
            composable(Routes.DRIVES) { DrivesRoute() }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onCalibration = { navController.navigate(Routes.CALIBRATION) },
                )
            }
            composable(Routes.REVIEW) { ReviewRoute(onBack = navController::navigateUp) }
            composable(Routes.CALIBRATION) { CalibrationRoute(onBack = navController::navigateUp) }
        }
    }
}
