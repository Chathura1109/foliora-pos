package com.foliora.pos.ui.screens.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import com.foliora.pos.data.local.entity.SettingEntity
import com.foliora.pos.data.local.entity.UserEntity
import com.foliora.pos.ui.components.FolioraTopAppBar
import com.foliora.pos.ui.receipt.ReceiptData
import com.foliora.pos.ui.receipt.ReceiptDialog
import com.foliora.pos.ui.receipt.ReceiptLineItem
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateful composable for the Sales History screen in Foliora POS.
 * Connects to [SalesViewModel] to retrieve sales list and customer data, and provides
 * navigation triggers for back navigation and creating a new sale transaction.
 *
 * @param onBackClick Optional callback for navigating back.
 * @param onNavigateToNewSale Callback invoked to navigate to the New Sale screen.
 * @param viewModel Hilt-injected instance of [SalesViewModel].
 */
@Composable
fun SalesScreen(
    onBackClick: (() -> Unit)? = null,
    onNavigateToNewSale: () -> Unit = {},
    viewModel: SalesViewModel = hiltViewModel()
) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()

    SalesScreenContent(
        sales = sales,
        customers = customers,
        products = products,
        users = users,
        settings = settings,
        userRole = role,
        onBackClick = onBackClick,
        onNewSaleClick = onNavigateToNewSale,
        onDeleteSale = { sale -> viewModel.deleteSale(sale) },
        onMarkAsPaid = { sale -> viewModel.markSaleAsPaid(sale) },
        onGetSaleItems = { saleId -> viewModel.getSaleItems(saleId) }
    )
}

/**
 * Stateless UI content composable for the Sales History Screen.
 * Renders the top bar, search filter, sales list, customer name resolution, status chips,
 * FAB for creating new sales, and sale deletion dialogs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreenContent(
    sales: List<SaleEntity>,
    customers: List<CustomerEntity>,
    products: List<ProductEntity>,
    users: List<UserEntity>,
    settings: SettingEntity?,
    userRole: String,
    onBackClick: (() -> Unit)?,
    onNewSaleClick: () -> Unit,
    onDeleteSale: (SaleEntity) -> Unit,
    onMarkAsPaid: (SaleEntity) -> Unit,
    onGetSaleItems: (Int) -> Flow<List<SaleItemEntity>>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var saleToDelete by remember { mutableStateOf<SaleEntity?>(null) }
    var saleDetailsToShow by remember { mutableStateOf<SaleEntity?>(null) }

    // Efficient map lookup for customer ID to customer entity conversion
    val customerMap = remember(customers) {
        customers.associateBy { it.id }
    }
    val userMap = remember(users) { users.associateBy { it.id } }

    // Filter sales by ID, customer name, payment method, or status string
    val filteredSales = remember(sales, searchQuery, customerMap) {
        if (searchQuery.isBlank()) {
            sales
        } else {
            sales.filter { sale ->
                val customerName = sale.customerId?.let { customerMap[it]?.name }.orEmpty()
                val saleIdStr = sale.id.toString()
                val statusStr = sale.status
                val paymentMethodStr = sale.paymentMethod

                saleIdStr.contains(searchQuery, ignoreCase = true) ||
                        customerName.contains(searchQuery, ignoreCase = true) ||
                        statusStr.contains(searchQuery, ignoreCase = true) ||
                        paymentMethodStr.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "Sales History",
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewSaleClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Sale"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search sales by ID, customer, method, or status...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear Search"
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Sales List or Empty State View
            if (filteredSales.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (sales.isEmpty()) "No sales transactions yet" else "No matching sales found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (sales.isEmpty()) "Tap the + button to process a new sale" else "Try adjusting your search query",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = filteredSales,
                        key = { it.id }
                    ) { sale ->
                        val customerName = sale.customerId?.let { customerMap[it]?.name }

                        SaleItemCard(
                            sale = sale,
                            customerName = customerName,
                            userRole = userRole,
                            onClick = { saleDetailsToShow = sale },
                            onDeleteClick = { saleToDelete = sale },
                            onMarkAsPaidClick = { onMarkAsPaid(sale) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp)) // Clearance space for FAB
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (saleToDelete != null) {
        SaleDeleteConfirmationDialog(
            sale = saleToDelete!!,
            onDismiss = { saleToDelete = null },
            onConfirm = {
                onDeleteSale(saleToDelete!!)
                saleToDelete = null
            }
        )
    }

    // Reusable receipt preview for a saved sale.
    if (saleDetailsToShow != null) {
        SaleDetailsDialog(
            sale = saleDetailsToShow!!,
            products = products,
            customerName = saleDetailsToShow!!.customerId?.let { customerMap[it]?.name },
            cashierName = userMap[saleDetailsToShow!!.cashierId]?.name ?: "Unknown",
            settings = settings,
            onGetSaleItems = onGetSaleItems,
            onDismiss = { saleDetailsToShow = null }
        )
    }
}

/**
 * Card composable displaying individual sale header details including Sale ID,
 * formatted date, customer name (if available), total amount, payment method, and status badge.
 */
@Composable
fun SaleItemCard(
    sale: SaleEntity,
    customerName: String?,
    userRole: String,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onMarkAsPaidClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Format epoch date timestamp
    val formattedDate = remember(sale.date) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(sale.date))
    }

    // Format currency amount
    val formattedAmount = remember(sale.totalAmount) {
        String.format(Locale.getDefault(), "Rs.%.2f", sale.totalAmount)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header Row: Sale ID, Status Chip, Delete Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sale #${sale.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    StatusBadge(status = sale.status)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (sale.status.equals("PENDING", ignoreCase = true)) {
                        IconButton(
                            onClick = onMarkAsPaidClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Mark as Paid",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (userRole == "OWNER") {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Sale #${sale.id}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Customer Name Row (if associated customer exists)
            if (!customerName.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = customerName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Walk-in Customer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // Date Row
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Details Row: Payment Method & Total Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Payment Method Tag
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (sale.paymentMethod.uppercase()) {
                        "CARD" -> Icons.Default.CreditCard
                        "BANK" -> Icons.Default.Payments
                        else -> Icons.Default.Money
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = sale.paymentMethod.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                // Total Amount
                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Custom chip badge displaying the sale status (e.g. PAID or PENDING) with color coding.
 */
@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val isPaid = status.equals("PAID", ignoreCase = true)
    val isPending = status.equals("PENDING", ignoreCase = true)

    val backgroundColor = when {
        isPaid -> Color(0xFFE8F5E9) // Light green
        isPending -> Color(0xFFFFF3E0) // Light orange
        else -> Color(0xFFF5F5F5) // Neutral gray
    }

    val textColor = when {
        isPaid -> Color(0xFF2E7D32) // Dark green
        isPending -> Color(0xFFE65100) // Dark orange
        else -> Color(0xFF616161) // Dark gray
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Confirmation dialog presented prior to deleting a sale record.
 */
@Composable
fun SaleDeleteConfirmationDialog(
    sale: SaleEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Sale Record")
        },
        text = {
            Text(text = "Are you sure you want to delete Sale #${sale.id}? This action cannot be undone.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}

@Composable
fun SaleDetailsDialog(
    sale: SaleEntity,
    products: List<ProductEntity>,
    customerName: String?,
    cashierName: String,
    settings: SettingEntity?,
    onGetSaleItems: (Int) -> Flow<List<SaleItemEntity>>,
    onDismiss: () -> Unit
) {
    val itemsFlow = remember(sale.id) { onGetSaleItems(sale.id) }
    val items by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val productMap = remember(products) { products.associateBy { it.id } }

    val receipt = remember(sale, items, productMap, customerName, cashierName, settings) {
        ReceiptData(
            saleId = sale.id,
            date = sale.date,
            shopName = settings?.shopName ?: "Foliora",
            shopAddress = settings?.address.orEmpty(),
            shopPhone = settings?.phoneNumber.orEmpty(),
            cashierName = cashierName,
            customerName = customerName,
            paymentMethod = sale.paymentMethod,
            status = sale.status,
            items = items.map { item ->
                ReceiptLineItem(
                    productName = productMap[item.productId]?.name ?: "Product #${item.productId}",
                    quantity = item.quantity,
                    sellingPrice = item.sellingPrice,
                    subtotal = item.subtotal
                )
            },
            totalAmount = sale.totalAmount,
            receiptMessage = settings?.receiptMessage.orEmpty()
        )
    }
    ReceiptDialog(
        receipt = receipt,
        onDismiss = onDismiss,
        dismissLabel = "Close",
        isLoadingItems = items.isEmpty()
    )
}
