package com.homehub.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homehub.app.ui.AppViewModel
import com.homehub.app.ui.screens.AddDeviceScreen
import com.homehub.app.ui.screens.DashboardScreen
import com.homehub.app.ui.screens.DeviceDetailScreen
import com.homehub.app.ui.screens.SettingsScreen
import com.homehub.app.ui.theme.HomeHubTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeHubTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "dashboard") {
                    composable("dashboard") {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenDevice = { id -> navController.navigate("device/$id") },
                            onAddDevice = { navController.navigate("add") },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("add") {
                        AddDeviceScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = "device/{id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        DeviceDetailScreen(viewModel = viewModel, deviceId = id, onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
