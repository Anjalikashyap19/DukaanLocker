package com.iadv.dukaanlocker.ui.navigation

import com.iadv.dukaanlocker.api.ShopResponse

/**
 * Type-safe navigation routes for the app.
 * Replaces string-based navigation with sealed class routes.
 */
sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Login : Screen("login")
    data object Wizard : Screen("wizard")
    data object AddBusiness : Screen("add_business")
    data object OwnerHome : Screen("owner_home")
    data object ManagerHome : Screen("manager_home")
}

/**
 * Bottom navigation routes (tabs within home screens).
 */
sealed class BottomTab(val route: String) {
    data object Home : BottomTab("home")
    data object Business : BottomTab("business")
    data object Docs : BottomTab("docs")
    data object Team : BottomTab("team")
    data object Settings : BottomTab("settings")
}
