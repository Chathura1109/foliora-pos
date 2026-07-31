package com.foliora.pos.ui.screens.purchase

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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
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
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.PurchaseItemEntity
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.ui.components.FolioraTopAppBar
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stateful composable for the Purchases History screen in Foliora POS.
 * Connects to [PurchasesViewModel] to retrieve purchase records and supplier details, and provides
 * callbacks for back navigation and navigating to the New Purchase screen.
 *
 * @param onBackClick Optional callback for navigating back.
 * @param onNavigateToNewPurchase Callback invoked to navigate to the New Purchase screen.
 * @param viewModel Hilt-injected instance of [PurchasesViewModel].
 */
@Composable
fun PurchasesScreen(
    onBackClick: (() -> Unit)? = null,
    onNavigateToNewPurchase: () -> Unit = {},
    viewModel: PurchasesViewModel = hiltViewModel()
) {
    val purchases by viewModel.purchases.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()

    PurchasesScreenContent(
        purchases = purchases,
        suppliers = suppliers,
        products = products,
        onBackClick = onBackClick,
        onNewPurchaseClick = onNavigateToNewPurchase,
        onDeletePurchase = { purchase -> viewModel.deletePurchase(purchase) },
        onGetPurchaseItems = viewModel::getPurchaseItems
    )
}

/**
 * Stateless UI content composable for the Purchases History Screen.
 * Renders top app bar, search bar, purchases list, supplier name lookup, status badges,
 * FAB for creating new purchases, and confirmation dialogs for deletion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreenContent(
    purchases: List<PurchaseEntity>,
    suppliers: List<SupplierEntity>,
    products: List<ProductEntity>,
    onBackClick: (() -> Unit)?,
    onNewPurchaseClick: () -> Unit,
    onDeletePurchase: (PurchaseEntity) -> Unit,
    onGetPurchaseItems: (Int) -> Flow<List<PurchaseItemEntity>>,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var purchaseToDelete by remember { mutableStateOf<PurchaseEntity?>(null) }
    var purchaseDetailsToShow by remember { mutableStateOf<PurchaseEntity?>(null) }

    // Map supplier ID to supplier entity for fast lookup
    val supplierMap = remember(suppliers) {
        suppliers.associateBy { it.id }
    }

    // Filter purchases by purchase ID, supplier name, or status string
    val filteredPurchases = remember(purchases, searchQuery, supplierMap) {
        if (searchQuery.isBlank()) {
            purchases
        } else {
            purchases.filter { purchase ->
                val supplierName = supplierMap[purchase.supplierId]?.name.orEmpty()
                val purchaseIdStr = purchase.id.toString()
                val statusStr = purchase.status

                purchaseIdStr.contains(searchQuery, ignoreCase = true) ||
                        supplierName.contains(searchQuery, ignoreCase = true) ||
                        statusStr.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "Purchases History",
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewPurchaseClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Purchase"
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
                placeholder = { Text("Search purchases by ID, supplier, or status...") },
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

            // Purchases List or Empty State
            if (filteredPurchases.isEmpty()) {
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
                            text = if (purchases.isEmpty()) "No purchase orders recorded" else "No matching purchases found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (purchases.isEmpty()) "Tap the + button to create a new purchase order" else "Try adjusting your search query",
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
                        items = filteredPurchases,
                        key = { it.id }
                    ) { purchase ->
                        val supplierName = supplierMap[purchase.supplierId]?.name

                        PurchaseItemCard(
                            purchase = purchase,
                            supplierName = supplierName,
                            onClick = { purchaseDetailsToShow = purchase },
                            onDeleteClick = { purchaseToDelete = purchase }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp)) // Clearance for FAB
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (purchaseToDelete != null) {
        PurchaseDeleteConfirmationDialog(
            purchase = purchaseToDelete!!,
            onDismiss = { purchaseToDelete = null },
            onConfirm = {
                onDeletePurchase(purchaseToDelete!!)
                purchaseToDelete = null
            }
        )
    }

    if (purchaseDetailsToShow != null) {
        PurchaseDetailsDialog(
            purchase = purchaseDetailsToShow!!,
            products = products,
            onGetPurchaseItems = onGetPurchaseItems,
            onDismiss = { purchaseDetailsToShow = null }
        )
    }
}

/**
 * Card composable displaying individual purchase details including Purchase ID,
 * formatted date, supplier name (looked up via supplierId), total cost, and status badge.
 */
@Composable
fun PurchaseItemCard(
    purchase: PurchaseEntity,
    supplierName: String?,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Format epoch date timestamp
    val formattedDate = remember(purchase.date) {
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(Date(purchase.date))
    }

    // Format currency amount for total cost
    val formattedCost = remember(purchase.totalCost) {
        String.format(Locale.getDefault(), "Rs.%.2f", purchase.totalCost)
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
            // Header Row: Purchase ID, Status Chip, Delete Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Purchase #${purchase.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    PurchaseStatusBadge(status = purchase.status)
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Purchase #${purchase.id}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Supplier Name Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (!supplierName.isNullOrBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = supplierName ?: "Unknown Supplier (ID: ${purchase.supplierId})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (!supplierName.isNullOrBlank()) FontWeight.Medium else FontWeight.Normal,
                    color = if (!supplierName.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
            }

            // Date Row
            Text(
                text = formattedDate,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Bottom Row: Total Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total Cost",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Text(
                    text = formattedCost,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Custom status badge composable displaying purchase status (e.g. COMPLETED or PENDING) with color coding.
 */
@Composable
fun PurchaseStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val isCompleted = status.equals("COMPLETED", ignoreCase = true)
    val isPending = status.equals("PENDING", ignoreCase = true)

    val backgroundColor = when {
        isCompleted -> Color(0xFFE8F5E9) // Light green
        isPending -> Color(0xFFFFF3E0) // Light orange
        else -> Color(0xFFF5F5F5) // Light gray
    }

    val textColor = when {
        isCompleted -> Color(0xFF2E7D32) // Dark green
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
 * Confirmation dialog presented prior to deleting a purchase record.
 */
@Composable
fun PurchaseDeleteConfirmationDialog(
    purchase: PurchaseEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Delete Purchase Order")
        },
        text = {
            Text(text = "Are you sure you want to delete Purchase #${purchase.id}? This action cannot be undone.")
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
fun PurchaseDetailsDialog(
    purchase: PurchaseEntity,
    products: List<ProductEntity>,
    onGetPurchaseItems: (Int) -> Flow<List<PurchaseItemEntity>>,
    onDismiss: () -> Unit
) {
    val itemsFlow = remember(purchase.id) { onGetPurchaseItems(purchase.id) }
    val purchaseItems by itemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val productMap = remember(products) { products.associateBy { it.id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Purchase #${purchase.id} Details", fontWeight = FontWeight.Bold)
        },
        text = {
            if (purchaseItems.isEmpty()) {
                Text(text = "Loading items...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(purchaseItems) { item ->
                        val productName = productMap[item.productId]?.name ?: "Unknown Product"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = productName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${item.quantity} x Rs. ${String.format(Locale.US, "%.2f", item.buyingPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "Rs. ${String.format(Locale.US, "%.2f", item.subtotal)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    item {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "Rs. ${String.format(Locale.US, "%.2f", purchase.totalCost)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close")
            }
        }
    )
}
