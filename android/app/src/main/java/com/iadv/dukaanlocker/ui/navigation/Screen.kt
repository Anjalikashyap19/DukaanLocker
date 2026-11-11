package com.iadv.dukaanlocker.ui.navigation

import com.iadv.dukaanlocker.api.ShopResponse
import com.iadv.dukaanlocker.DocumentItem

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
    data object Notifications : Screen("notifications")
    data object Bin : Screen("bin")
    data object AppSettings : Screen("app_settings")
    data class RenewalPayment(val doc: DocumentItem) : Screen("renewal_payment")
    data class FssaCompliance(val shopId: Long) : Screen("fssa_compliance")
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
