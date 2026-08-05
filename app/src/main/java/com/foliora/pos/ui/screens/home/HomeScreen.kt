package com.foliora.pos.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.ui.components.*
import com.foliora.pos.ui.navigation.Screen
import com.foliora.pos.ui.screens.reports.ReportsViewModel
import com.foliora.pos.ui.screens.sale.SalesViewModel
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*
import com.foliora.pos.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    reportsViewModel: ReportsViewModel = hiltViewModel(),
    salesViewModel: SalesViewModel = hiltViewModel()
) {
    val role by homeViewModel.currentUserRole.collectAsStateWithLifecycle()
    var currentTab by remember { mutableStateOf("home") }

    val bottomNavItems = if (role == "OWNER") {
        listOf(
            BottomNavItem("Home", Icons.Default.Home, "home"),
            BottomNavItem("POS", Icons.Default.PointOfSale, Screen.NewSale.route),
            BottomNavItem("Products", Icons.Default.Inventory, Screen.Products.route),
            BottomNavItem("Purchases", Icons.Default.LocalShipping, Screen.Purchases.route),
            BottomNavItem("More", Icons.Default.MoreHoriz, "more")
        )
    } else {
        listOf(
            BottomNavItem("Home", Icons.Default.Home, "home"),
            BottomNavItem("POS", Icons.Default.PointOfSale, Screen.NewSale.route),
            BottomNavItem("Products", Icons.Default.Inventory, Screen.Products.route),
            BottomNavItem("Sales", Icons.Default.Receipt, Screen.Sales.route),
            BottomNavItem("More", Icons.Default.MoreHoriz, "more")
        )
    }

    Scaffold(
        bottomBar = {
            FolioraBottomNavigation(
                items = bottomNavItems,
                currentRoute = currentTab,
                onNavigate = { route ->
                    if (route == "home" || route == "more") {
                        currentTab = route
                    } else {
                        onNavigate(route)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (currentTab) {
                "home" -> {
                    if (role == "OWNER") {
                        AdminDashboard(
                            reportsViewModel = reportsViewModel,
                            salesViewModel = salesViewModel,
                            onNavigate = onNavigate
                        )
                    } else {
                        CashierDashboard(
                            reportsViewModel = reportsViewModel,
                            salesViewModel = salesViewModel,
                            onNavigate = onNavigate
                        )
                    }
                }
                "more" -> {
                    MoreScreen(onNavigate = onNavigate, onLogout = onLogout, role = role)
                }
            }
        }
    }
}

@Composable
fun AdminDashboard(
    reportsViewModel: ReportsViewModel,
    salesViewModel: SalesViewModel,
    onNavigate: (String) -> Unit
) {
    val todaysSales by reportsViewModel.todaysSalesTotal.collectAsStateWithLifecycle()
    val todaysProfit by reportsViewModel.todaysProfit.collectAsStateWithLifecycle()
    val lowStockProducts by reportsViewModel.lowStockProducts.collectAsStateWithLifecycle()
    val salesHistory by salesViewModel.sales.collectAsStateWithLifecycle()
    
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    
    val todayTransactionsCount = salesHistory.count { it.date >= todayStart }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            DashboardHeader(role = "Owner")
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Today's Sales",
                    value = "Rs. ${String.format(Locale.US, "%.2f", todaysSales)}",
                    icon = Icons.Default.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Today's Profit",
                    value = "Rs. ${String.format(Locale.US, "%.2f", todaysProfit)}",
                    icon = Icons.Default.TrendingUp,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Transactions",
                    value = "$todayTransactionsCount",
                    icon = Icons.Default.Receipt,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Low Stock",
                    value = "${lowStockProducts.size}",
                    icon = Icons.Default.Warning,
                    iconTint = WarningYellow,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuickActionButton(
                    title = "New Sale",
                    icon = Icons.Default.PointOfSale,
                    onClick = { onNavigate(Screen.NewSale.route) }
                )
                QuickActionButton(
                    title = "Add Product",
                    icon = Icons.Default.AddBox,
                    onClick = { onNavigate(Screen.Products.route) }
                )
                QuickActionButton(
                    title = "Restock",
                    icon = Icons.Default.LocalShipping,
                    onClick = { onNavigate(Screen.NewPurchase.route) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (lowStockProducts.isNotEmpty()) {
            item {
                SectionHeader("Low Stock Alerts", onSeeAll = { onNavigate(Screen.Products.route) })
            }
            items(lowStockProducts.take(3)) { product ->
                LowStockRow(
                    productName = product.name,
                    stock = product.stockQuantity,
                    unit = product.unit,
                    onClick = { onNavigate(Screen.Products.route) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (salesHistory.isNotEmpty()) {
            item {
                SectionHeader("Recent Sales", onSeeAll = { onNavigate(Screen.Sales.route) })
            }
            items(salesHistory.take(5)) { sale ->
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sale.date))
                RecentSaleRow(
                    saleId = sale.id.toString(),
                    time = timeStr,
                    amount = "Rs. ${String.format(Locale.US, "%.2f", sale.totalAmount)}",
                    status = sale.status,
                    onClick = { onNavigate(Screen.Sales.route) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun CashierDashboard(
    reportsViewModel: ReportsViewModel,
    salesViewModel: SalesViewModel,
    onNavigate: (String) -> Unit
) {
    val todaysSales by reportsViewModel.todaysSalesTotal.collectAsStateWithLifecycle()
    val lowStockProducts by reportsViewModel.lowStockProducts.collectAsStateWithLifecycle()
    val salesHistory by salesViewModel.sales.collectAsStateWithLifecycle()

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    
    val todayTransactionsCount = salesHistory.count { it.date >= todayStart }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            DashboardHeader(role = "Cashier")
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            PrimaryButton(
                text = "Start New Sale",
                icon = Icons.Default.PointOfSale,
                onClick = { onNavigate(Screen.NewSale.route) }
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                MetricCard(
                    title = "Today's Sales",
                    value = "Rs. ${String.format(Locale.US, "%.2f", todaysSales)}",
                    icon = Icons.Default.AttachMoney,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Transactions",
                    value = "$todayTransactionsCount",
                    icon = Icons.Default.Receipt,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (lowStockProducts.isNotEmpty()) {
            item {
                SectionHeader("Low Stock Alerts", onSeeAll = { onNavigate(Screen.Products.route) })
            }
            items(lowStockProducts.take(3)) { product ->
                LowStockRow(
                    productName = product.name,
                    stock = product.stockQuantity,
                    unit = product.unit,
                    onClick = { onNavigate(Screen.Products.route) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }

        if (salesHistory.isNotEmpty()) {
            item {
                SectionHeader("Recent Sales", onSeeAll = { onNavigate(Screen.Sales.route) })
            }
            items(salesHistory.take(5)) { sale ->
                val timeStr = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sale.date))
                RecentSaleRow(
                    saleId = sale.id.toString(),
                    time = timeStr,
                    amount = "Rs. ${String.format(Locale.US, "%.2f", sale.totalAmount)}",
                    status = sale.status,
                    onClick = { onNavigate(Screen.Sales.route) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun DashboardHeader(role: String) {
    val dateStr = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault()).format(Date())
    Column {
        Text(
            text = "Welcome back, $role!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = dateStr,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(title: String, onSeeAll: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "See All",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { onSeeAll() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onNavigate: (String) -> Unit, onLogout: () -> Unit, role: String) {
    val items = if (role == "OWNER") {
        listOf(
            Pair("Customers", Screen.Customers.route),
            Pair("Suppliers", Screen.Suppliers.route),
            Pair("Categories", Screen.Categories.route),
            Pair("Reports", Screen.Reports.route),
            Pair("Settings", Screen.Settings.route),
            Pair("Logout", "action_logout")
        )
    } else {
        listOf(
            Pair("Customers", Screen.Customers.route),
            Pair("Logout", "action_logout")
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        FolioraTopAppBar(title = "More Options", onBackClick = null)
        
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items) { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.second == "action_logout") {
                                FirebaseAuth.getInstance().signOut()
                                onLogout()
                            } else {
                                onNavigate(item.second)
                            }
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.first,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (item.first == "Logout") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = if (item.first == "Logout") {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}
