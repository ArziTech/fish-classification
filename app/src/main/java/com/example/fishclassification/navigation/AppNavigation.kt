package com.example.fishclassification.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.fishclassification.ui.home.HomeScreen
import com.example.fishclassification.ui.result.ResultScreen

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Home,
    ) {
        composable<Home> {
            HomeScreen(
                onNavigateToResult = { uri ->
                    navController.navigate(Result(imageUri = uri))
                }
            )
        }

        composable<Result> { backStackEntry ->
            val result = backStackEntry.toRoute<Result>()
            ResultScreen(
                imageUri = result.imageUri,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
