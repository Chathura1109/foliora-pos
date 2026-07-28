package com.foliora.pos.ui.navigation

/**
 * Sealed class representing navigation routes in the Foliora POS application.
 * Each object defines a unique route path used by Navigation Compose.
 *
 * @property route Unique string identifier for navigation destination.
 */
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Categories : Screen("categories")
    object Products : Screen("products")
    object Customers : Screen("customers")
    object Suppliers : Screen("suppliers")
    object Sales : Screen("sales")
    object NewSale : Screen("new_sale")
    object Purchases : Screen("purchases")
    object NewPurchase : Screen("new_purchase")
    object Settings : Screen("settings")
    object Reports : Screen("reports")
}
