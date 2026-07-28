package com.foliora.pos.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * Main navigation graph composable for the Foliora POS application.
 * Defines routing and destination placeholders for all app screens.
 *
 * @param navController Controller managing navigation hierarchy and backstack.
 * @param modifier Optional modifier for the NavHost container.
 */
@Composable
fun FolioraNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route,
        modifier = modifier
    ) {
        composable(Screen.Login.route) {
            com.foliora.pos.ui.screens.auth.LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            com.foliora.pos.ui.screens.home.HomeScreen(
                onNavigate = { route -> navController.navigate(route) },
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Categories.route) {
            com.foliora.pos.ui.screens.category.CategoryScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Products.route) {
            com.foliora.pos.ui.screens.product.ProductScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Customers.route) {
            com.foliora.pos.ui.screens.customer.CustomerScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Suppliers.route) {
            com.foliora.pos.ui.screens.supplier.SupplierScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Sales.route) {
            com.foliora.pos.ui.screens.sale.SalesScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToNewSale = { navController.navigate(Screen.NewSale.route) }
            )
        }
        composable(Screen.NewSale.route) {
            com.foliora.pos.ui.screens.sale.NewSaleScreen(
                onBackClick = { navController.popBackStack() },
                onCheckoutSuccess = { navController.popBackStack() }
            )
        }
        composable(Screen.Purchases.route) {
            com.foliora.pos.ui.screens.purchase.PurchasesScreen(
                onBackClick = { navController.popBackStack() },
                onNavigateToNewPurchase = { navController.navigate(Screen.NewPurchase.route) }
            )
        }
        composable(Screen.NewPurchase.route) {
            com.foliora.pos.ui.screens.purchase.NewPurchaseScreen(
                onBackClick = { navController.popBackStack() },
                onCheckoutSuccess = { navController.popBackStack() }
            )
        }
        composable(Screen.Reports.route) {
            com.foliora.pos.ui.screens.reports.ReportsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            com.foliora.pos.ui.screens.settings.SettingsScreen(
                onLogoutSuccess = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Centered text placeholder composable for unimplemented screens.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title)
    }
}
