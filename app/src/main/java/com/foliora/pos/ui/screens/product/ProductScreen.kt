package com.foliora.pos.ui.screens.product

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.foliora.pos.data.local.entity.CategoryEntity
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.ui.components.CameraPreviewScreen
import com.foliora.pos.ui.components.FolioraTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Product Management Screen composable for Foliora POS.
 * Displays interactive product inventory, category filtering, search, and dialog forms
 * to add, edit, or delete product records with CameraX photo capture integration.
 *
 * @param onBackClick Lambda callback for top bar navigation back action.
 * @param viewModel Hilt-injected [ProductViewModel] instance.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProductScreen(
    onBackClick: (() -> Unit)? = null,
    viewModel: ProductViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val inventoryBatches by viewModel.inventoryBatches.collectAsStateWithLifecycle()
    val role by viewModel.currentUserRole.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isAdjustingStock by viewModel.isAdjustingStock.collectAsStateWithLifecycle()
    val batchPriceEditState by viewModel.batchPriceEditState.collectAsStateWithLifecycle()
    val isUpdatingBatchPrices by viewModel.isUpdatingBatchPrices.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<Int?>(null) }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var productToEdit by remember { mutableStateOf<ProductEntity?>(null) }
    var productToDelete by remember { mutableStateOf<ProductEntity?>(null) }
    var productForBatchDetails by remember { mutableStateOf<ProductEntity?>(null) }
    var productForStockAdjustment by remember { mutableStateOf<ProductEntity?>(null) }

    // Filter products based on search query and optional category selection
    val filteredProducts = remember(products, searchQuery, selectedCategoryId) {
        products.filter { product ->
            val matchesQuery = product.name.contains(searchQuery, ignoreCase = true) ||
                    product.unit.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryId == null || product.categoryId == selectedCategoryId
            matchesQuery && matchesCategory
        }
    }
    val batchesByProduct = remember(inventoryBatches) {
        inventoryBatches.groupBy { it.productId }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FolioraTopAppBar(
                title = "Products",
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            if (role == "OWNER") {
                FloatingActionButton(
                    onClick = {
                        productToEdit = null
                        showAddEditDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Product"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                placeholder = { Text("Search products by name or unit...") },
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

            // Category Filter Chips
            if (categories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategoryId == null,
                        onClick = { selectedCategoryId = null },
                        label = { Text("All") }
                    )
                    categories.forEach { category ->
                        FilterChip(
                            selected = selectedCategoryId == category.id,
                            onClick = {
                                selectedCategoryId = if (selectedCategoryId == category.id) null else category.id
                            },
                            label = { Text(category.name) }
                        )
                    }
                }
            }

            // Product List or Empty State
            if (filteredProducts.isEmpty()) {
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
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (products.isEmpty()) "No products added yet" else "No matching products found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (products.isEmpty()) "Tap the '+' button below to add your first product." else "Try adjusting your search or category filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredProducts, key = { it.id }) { product ->
                        val category = categories.find { it.id == product.categoryId }
                        ProductItemCard(
                            product = product,
                            batches = batchesByProduct[product.id].orEmpty(),
                            categoryName = category?.name ?: "Uncategorized",
                            userRole = role,
                            onEditClick = {
                                productToEdit = product
                                showAddEditDialog = true
                            },
                            onDeleteClick = {
                                productToDelete = product
                            },
                            onViewBatches = {
                                productForBatchDetails = product
                            },
                            onAdjustStock = {
                                productForStockAdjustment = product
                            }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Bottom padding for FAB accessibility
                    }
                }
            }
        }
    }

    // Add / Edit Product Full Dialog
    if (showAddEditDialog) {
        AddEditProductDialog(
            product = productToEdit,
            categories = categories,
            onDismiss = {
                showAddEditDialog = false
                productToEdit = null
            },
            onSave = { categoryId, name, buyingPrice, sellingPrice, stock, unit, lowStockLimit, photoPath, notes ->
                if (productToEdit == null) {
                    viewModel.addProduct(
                        categoryId = categoryId,
                        name = name,
                        buyingPrice = buyingPrice,
                        sellingPrice = sellingPrice,
                        stockQuantity = stock,
                        unit = unit,
                        lowStockLimit = lowStockLimit,
                        photoPath = photoPath,
                        notes = notes
                    )
                } else {
                    viewModel.updateProduct(
                        productToEdit!!.copy(
                            categoryId = categoryId,
                            name = name,
                            buyingPrice = buyingPrice,
                            sellingPrice = sellingPrice,
                            stockQuantity = productToEdit!!.stockQuantity,
                            unit = unit,
                            lowStockLimit = lowStockLimit,
                            photoPath = photoPath,
                            notes = notes
                        )
                    )
                }
                showAddEditDialog = false
                productToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    if (productToDelete != null) {
        AlertDialog(
            onDismissRequest = { productToDelete = null },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete '${productToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        productToDelete?.let { viewModel.deleteProduct(it) }
                        productToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { productToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    productForBatchDetails?.let { product ->
        ProductBatchesDialog(
            product = product,
            batches = batchesByProduct[product.id].orEmpty(),
            showCostPrice = role == "OWNER",
            onEditBatch = { batch ->
                productForBatchDetails = null
                viewModel.openBatchPriceEditor(batch)
            },
            onDismiss = { productForBatchDetails = null }
        )
    }

    productForStockAdjustment?.let { product ->
        StockAdjustmentDialog(
            product = product,
            batches = batchesByProduct[product.id].orEmpty(),
            isSubmitting = isAdjustingStock,
            onDismiss = { productForStockAdjustment = null },
            onConfirm = { batchId, type, quantity, reason, notes ->
                viewModel.adjustStock(
                    productId = product.id,
                    batchId = batchId,
                    adjustmentType = type,
                    quantity = quantity,
                    reason = reason,
                    notes = notes
                )
                productForStockAdjustment = null
            }
        )
    }

    batchPriceEditState?.let { editState ->
        BatchPriceEditDialog(
            batch = editState.batch,
            canEditUnitCost = editState.canEditUnitCost,
            isSubmitting = isUpdatingBatchPrices,
            onDismiss = viewModel::dismissBatchPriceEditor,
            onConfirm = viewModel::updateBatchPrices
        )
    }
}

/**
 * Card component displaying individual product information.
 * Shows photo preview thumbnail (if available), name, category tag, pricing (buying & selling),
 * stock quantity with unit, and low stock warnings.
 */
@Composable
private fun ProductItemCard(
    product: ProductEntity,
    batches: List<InventoryBatchEntity>,
    categoryName: String,
    userRole: String,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onViewBatches: () -> Unit,
    onAdjustStock: () -> Unit
) {
    val isLowStock = product.stockQuantity <= product.lowStockLimit
    val activeBatches = remember(batches) { batches.filter { it.remainingQuantity > 0 } }
    val sellingPriceRange = remember(activeBatches) {
        formatBatchPriceRange(activeBatches.map { it.sellingPrice })
    }
    val costPriceRange = remember(activeBatches) {
        formatBatchPriceRange(activeBatches.map { it.unitCost })
    }

    val imageBitmap = rememberSampledImageBitmap(product.photoPath, 52.dp)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Product Photo Thumbnail
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = product.name,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (userRole == "OWNER") {
                    Row {
                        IconButton(onClick = onEditClick) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Product",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onDeleteClick) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Product",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pricing Info
                Column {
                    Text(
                        text = sellingPriceRange?.let { "Selling: $it" } ?: "Selling: No active batch",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (userRole == "OWNER") {
                        Text(
                            text = costPriceRange?.let { "Cost: $it" } ?: "Cost: No active batch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stock Info & Low Stock Badge
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Stock: ${product.stockQuantity} ${product.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (isLowStock) {
                        Spacer(modifier = Modifier.height(4.dp))
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    text = "Low Stock",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                leadingIconContentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
                    }
                }
            }

            if (!product.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Note: ${product.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val activeBatchCount = batches.count { it.remainingQuantity > 0 }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (userRole == "OWNER" && batches.isNotEmpty()) {
                    TextButton(onClick = onAdjustStock) {
                        Text("Adjust Stock")
                    }
                }
                TextButton(onClick = onViewBatches) {
                    Text(
                        text = "View Batches ($activeBatchCount active)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun StockAdjustmentDialog(
    product: ProductEntity,
    batches: List<InventoryBatchEntity>,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (batchId: Int, type: String, quantity: Double, reason: String, notes: String?) -> Unit
) {
    var adjustmentType by remember { mutableStateOf("DECREASE") }
    var selectedBatchId by remember { mutableStateOf(batches.firstOrNull { it.remainingQuantity > 0 }?.id) }
    var quantityText by remember { mutableStateOf("") }
    var reasonText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    val selectableBatches = if (adjustmentType == "DECREASE") {
        batches.filter { it.remainingQuantity > 0 }
    } else {
        batches
    }

    LaunchedEffect(adjustmentType, batches) {
        if (selectableBatches.none { it.id == selectedBatchId }) {
            selectedBatchId = selectableBatches.firstOrNull()?.id
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text("Adjust ${product.name} Stock") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Choose the exact batch. Product total stock will be recalculated automatically.",
                    style = MaterialTheme.typography.bodySmall
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = adjustmentType == "DECREASE",
                        onClick = { adjustmentType = "DECREASE" },
                        label = { Text("Decrease") }
                    )
                    FilterChip(
                        selected = adjustmentType == "INCREASE",
                        onClick = { adjustmentType = "INCREASE" },
                        label = { Text("Increase") }
                    )
                }

                if (selectableBatches.isEmpty()) {
                    Text(
                        if (adjustmentType == "DECREASE") {
                            "No batch has stock available to decrease."
                        } else {
                            "No batch exists. Add stock from the Restock screen first."
                        },
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Select batch", fontWeight = FontWeight.SemiBold)
                    selectableBatches.forEach { batch ->
                        FilterChip(
                            selected = selectedBatchId == batch.id,
                            onClick = { selectedBatchId = batch.id },
                            label = {
                                Text(
                                    "Batch #${batch.id} - ${batch.remainingQuantity} ${product.unit} - " +
                                        "Cost Rs.${String.format(Locale.getDefault(), "%.2f", batch.unitCost)} - " +
                                        "Sell Rs.${String.format(Locale.getDefault(), "%.2f", batch.sellingPrice)}"
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { quantityText = it },
                    label = { Text("Quantity *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason *") },
                    placeholder = { Text("Example: damaged, count correction, return") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    label = { Text("Notes (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                validationMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && selectableBatches.isNotEmpty(),
                onClick = {
                    val batch = selectableBatches.firstOrNull { it.id == selectedBatchId }
                    val quantity = quantityText.toDoubleOrNull()
                    validationMessage = when {
                        batch == null -> "Please select a stock batch."
                        quantity == null || !quantity.isFinite() || quantity <= 0 ->
                            "Please enter a quantity greater than zero."
                        adjustmentType == "DECREASE" && quantity > batch.remainingQuantity ->
                            "Only ${batch.remainingQuantity} ${product.unit} is available in this batch."
                        reasonText.isBlank() -> "Please enter a reason."
                        else -> null
                    }
                    if (validationMessage == null && batch != null && quantity != null) {
                        onConfirm(
                            batch.id,
                            adjustmentType,
                            quantity,
                            reasonText.trim(),
                            notesText.trim().ifBlank { null }
                        )
                    }
                }
            ) {
                Text(if (isSubmitting) "Saving..." else "Save Adjustment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProductBatchesDialog(
    product: ProductEntity,
    batches: List<InventoryBatchEntity>,
    showCostPrice: Boolean,
    onEditBatch: (InventoryBatchEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${product.name} Batches") },
        text = {
            if (batches.isEmpty()) {
                Text("No stock batches are recorded for this product.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = batches, key = { it.id }) { batch ->
                        val batchDate = remember(batch.receivedAt) {
                            SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                .format(Date(batch.receivedAt))
                        }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Batch #${batch.id}",
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (batch.remainingQuantity > 0) "ACTIVE" else "EXHAUSTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (batch.remainingQuantity > 0) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        }
                                    )
                                }
                                Text(
                                    text = "Remaining: ${batch.remainingQuantity} ${product.unit} • Total received: ${batch.originalQuantity} ${product.unit}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (showCostPrice) {
                                        "Cost Rs.${String.format(Locale.getDefault(), "%.2f", batch.unitCost)} • " +
                                            "Sell Rs.${String.format(Locale.getDefault(), "%.2f", batch.sellingPrice)}"
                                    } else {
                                        "Sell Rs.${String.format(Locale.getDefault(), "%.2f", batch.sellingPrice)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = "Last restocked: $batchDate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                if (showCostPrice && batch.remainingQuantity > 0) {
                                    TextButton(
                                        onClick = { onEditBatch(batch) },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Edit Prices")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun BatchPriceEditDialog(
    batch: InventoryBatchEntity,
    canEditUnitCost: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (unitCost: Double, sellingPrice: Double) -> Unit
) {
    var unitCostText by remember(batch.id) { mutableStateOf(batch.unitCost.toString()) }
    var sellingPriceText by remember(batch.id) { mutableStateOf(batch.sellingPrice.toString()) }
    var validationMessage by remember(batch.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Batch #${batch.id} Prices") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "These prices apply only to future sales from the remaining ${batch.remainingQuantity} units.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = unitCostText,
                    onValueChange = { if (canEditUnitCost) unitCostText = it },
                    label = { Text("Unit Cost (Rs.)") },
                    readOnly = !canEditUnitCost,
                    enabled = !isSubmitting,
                    supportingText = if (!canEditUnitCost) {
                        { Text("Locked because this batch has already been used in a sale.") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = sellingPriceText,
                    onValueChange = { sellingPriceText = it },
                    label = { Text("Selling Price (Rs.)") },
                    enabled = !isSubmitting,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Change quantities using Adjust Stock. Previous sales and profit remain unchanged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val unitCost = unitCostText.toDoubleOrNull()
                    val sellingPrice = sellingPriceText.toDoubleOrNull()
                    validationMessage = when {
                        unitCost == null || !unitCost.isFinite() || unitCost < 0 ->
                            "Enter a valid non-negative unit cost."
                        sellingPrice == null || !sellingPrice.isFinite() || sellingPrice < 0 ->
                            "Enter a valid non-negative selling price."
                        else -> null
                    }
                    if (validationMessage == null && unitCost != null && sellingPrice != null) {
                        onConfirm(unitCost, sellingPrice)
                    }
                }
            ) {
                Text(if (isSubmitting) "Saving..." else "Save Prices")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") }
        }
    )
}

private fun formatBatchPriceRange(prices: List<Double>): String? {
    val validPrices = prices.filter { it.isFinite() && it >= 0 }
    if (validPrices.isEmpty()) return null
    val minimum = validPrices.min()
    val maximum = validPrices.max()
    return if (minimum == maximum) {
        String.format(Locale.getDefault(), "Rs.%.2f", minimum)
    } else {
        String.format(Locale.getDefault(), "Rs.%.2f – Rs.%.2f", minimum, maximum)
    }
}

/**
 * Separate full-screen style dialog for adding or editing product records.
 * Provides entry fields for product photo preview, photo capture, name, category selection,
 * prices, stock level, measurement unit, low stock limit, and notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditProductDialog(
    product: ProductEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        categoryId: Int,
        name: String,
        buyingPrice: Double,
        sellingPrice: Double,
        stock: Double,
        unit: String,
        lowStockLimit: Double,
        photoPath: String?,
        notes: String?
    ) -> Unit
) {
    val context = LocalContext.current

    var name by remember { mutableStateOf(product?.name ?: "") }
    var selectedCategory by remember {
        mutableStateOf(categories.find { it.id == product?.categoryId } ?: categories.firstOrNull())
    }
    var buyingPriceText by remember { mutableStateOf(product?.buyingPrice?.toString() ?: "") }
    var sellingPriceText by remember { mutableStateOf(product?.sellingPrice?.toString() ?: "") }
    var stockText by remember { mutableStateOf(product?.stockQuantity?.toString() ?: "") }
    var unitText by remember { mutableStateOf(product?.unit ?: "pcs") }
    var lowStockLimitText by remember { mutableStateOf(product?.lowStockLimit?.toString() ?: "5.0") }
    var notesText by remember { mutableStateOf(product?.notes ?: "") }
    
    // Photo path state captured via CameraX
    var photoPathState by remember { mutableStateOf(product?.photoPath) }

    var showCameraPreview by remember { mutableStateOf(false) }
    var isImportingPhoto by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Launcher for requesting CAMERA permission before displaying camera preview
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Open camera preview screen (CameraPreviewScreen will handle permission rationale if needed)
        showCameraPreview = true
    }

    val photoFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { selectedPhotoUri ->
        if (selectedPhotoUri != null) {
            isImportingPhoto = true
            coroutineScope.launch {
                try {
                    photoPathState = withContext(Dispatchers.IO) {
                        copySelectedProductPhoto(context, selectedPhotoUri)
                    }
                    errorMessage = null
                } catch (exception: Exception) {
                    errorMessage = exception.localizedMessage ?: "Unable to import the selected photo."
                } finally {
                    isImportingPhoto = false
                }
            }
        }
    }

    val commonUnits = listOf("pcs", "kg", "g", "liters", "ml", "bags", "boxes", "packs", "m")

    if (showCameraPreview) {
        Dialog(
            onDismissRequest = { showCameraPreview = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CameraPreviewScreen(
                onPhotoCaptured = { uri ->
                    photoPathState = uri
                    showCameraPreview = false
                },
                onClose = {
                    showCameraPreview = false
                }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header Title
                Text(
                    text = if (product == null) "Add New Product" else "Edit Product",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Form Container
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Photo Preview and Take Photo Button Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val imageBitmap = rememberSampledImageBitmap(photoPathState, 110.dp)

                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageBitmap != null) {
                                Image(
                                    bitmap = imageBitmap,
                                    contentDescription = "Product Photo Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = "No Photo",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "No Photo",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    showCameraPreview = true
                                } else {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (photoPathState.isNullOrBlank()) "Take Photo" else "Retake Photo")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { photoFileLauncher.launch("image/*") },
                            enabled = !isImportingPhoto
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isImportingPhoto) "Importing..." else "Choose Photo")
                        }
                    }

                    // Product Name Field
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Category Selection Dropdown
                    if (categories.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No categories available. Please add a category first before adding products.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = categoryDropdownExpanded,
                            onExpandedChange = { categoryDropdownExpanded = !categoryDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCategory?.name ?: "Select Category",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category *") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = categoryDropdownExpanded,
                                onDismissRequest = { categoryDropdownExpanded = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat.name) },
                                        onClick = {
                                            selectedCategory = cat
                                            categoryDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (product == null) {
                        // Prices entered during creation belong to the opening batch.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = buyingPriceText,
                                onValueChange = { buyingPriceText = it },
                                label = { Text("Opening Batch Cost *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = sellingPriceText,
                                onValueChange = { sellingPriceText = it },
                                label = { Text("Opening Selling Price *") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Prices are managed separately for each stock batch. Use View Batches → Edit Prices.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }

                    // Stock & Unit Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = stockText,
                            onValueChange = { if (product == null) stockText = it },
                            label = {
                                Text(if (product == null) "Initial Stock *" else "Current Stock (batch-controlled)")
                            },
                            readOnly = product != null,
                            supportingText = if (product != null) {
                                { Text("Use Adjust Stock or Restock to change quantity.") }
                            } else {
                                null
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = unitText,
                            onValueChange = { unitText = it },
                            label = { Text("Unit (e.g. pcs, kg) *") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    // Unit Suggestion Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commonUnits.take(5).forEach { unitSuggestion ->
                            AssistChip(
                                onClick = { unitText = unitSuggestion },
                                label = { Text(unitSuggestion, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    // Low Stock Limit Field
                    OutlinedTextField(
                        value = lowStockLimitText,
                        onValueChange = { lowStockLimitText = it },
                        label = { Text("Low Stock Alert Limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Notes Field
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes (Optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    // Error Message Display
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            // Validation
                            val category = selectedCategory
                            val buyingPrice = if (product == null) {
                                buyingPriceText.toDoubleOrNull()
                            } else {
                                product.buyingPrice
                            }
                            val sellingPrice = if (product == null) {
                                sellingPriceText.toDoubleOrNull()
                            } else {
                                product.sellingPrice
                            }
                            val stock = if (product == null) {
                                stockText.toDoubleOrNull()
                            } else {
                                product.stockQuantity
                            }
                            val lowStockLimit = lowStockLimitText.toDoubleOrNull() ?: 5.0

                            when {
                                name.isBlank() -> {
                                    errorMessage = "Please enter product name."
                                }
                                category == null -> {
                                    errorMessage = "Please select a category."
                                }
                                buyingPrice == null || buyingPrice < 0 -> {
                                    errorMessage = "Please enter a valid buying price."
                                }
                                sellingPrice == null || sellingPrice < 0 -> {
                                    errorMessage = "Please enter a valid selling price."
                                }
                                stock == null || stock < 0 -> {
                                    errorMessage = "Please enter a valid stock quantity."
                                }
                                unitText.isBlank() -> {
                                    errorMessage = "Please enter a unit (e.g. pcs, kg)."
                                }
                                else -> {
                                    errorMessage = null
                                    onSave(
                                        category.id,
                                        name.trim(),
                                        buyingPrice,
                                        sellingPrice,
                                        stock,
                                        unitText.trim(),
                                        lowStockLimit,
                                        photoPathState?.ifBlank { null },
                                        notesText.ifBlank { null }
                                    )
                                }
                            }
                        }
                    ) {
                        Text(if (product == null) "Save Product" else "Update Product")
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberSampledImageBitmap(
    photoPath: String?,
    targetSize: Dp
): ImageBitmap? {
    val density = LocalDensity.current
    val targetSizePx = with(density) { targetSize.roundToPx() }.coerceAtLeast(1)
    val imageBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = photoPath,
        key2 = targetSizePx
    ) {
        value = withContext(Dispatchers.IO) {
            decodeSampledImageBitmap(photoPath, targetSizePx)
        }
    }
    return imageBitmap
}

private fun decodeSampledImageBitmap(
    photoPath: String?,
    targetSizePx: Int
): ImageBitmap? {
    if (photoPath.isNullOrBlank()) return null

    if (photoPath.startsWith("https://") || photoPath.startsWith("http://")) {
        return decodeRemoteImageBitmap(photoPath, targetSizePx)
    }

    val photoFile = File(photoPath)
    if (!photoFile.isFile) return null

    return try {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(photoFile.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
            bounds.outHeight / (sampleSize * 2) >= targetSizePx
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }
        BitmapFactory.decodeFile(photoFile.absolutePath, decodeOptions)?.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

private fun decodeRemoteImageBitmap(
    photoUrl: String,
    targetSizePx: Int
): ImageBitmap? {
    return try {
        val connection = URL(photoUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.instanceFollowRedirects = true

            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_REMOTE_IMAGE_BYTES) return null

            val output = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    if (totalBytes > MAX_REMOTE_IMAGE_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }

            val imageBytes = output.toByteArray()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (
                bounds.outWidth / (sampleSize * 2) >= targetSizePx &&
                bounds.outHeight / (sampleSize * 2) >= targetSizePx
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, decodeOptions)?.asImageBitmap()
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }
}

private const val MAX_REMOTE_IMAGE_BYTES = 10 * 1024 * 1024

private fun copySelectedProductPhoto(context: Context, sourceUri: Uri): String {
    val mimeType = context.contentResolver.getType(sourceUri)
    require(mimeType?.startsWith("image/") == true) { "Please select a valid image file." }

    val extension = MimeTypeMap.getSingleton()
        .getExtensionFromMimeType(mimeType)
        ?.takeIf(String::isNotBlank)
        ?: "jpg"
    val photoDirectory = File(context.filesDir, "product_photos").apply { mkdirs() }
    val destination = File(photoDirectory, "IMPORT_${System.currentTimeMillis()}_product.$extension")

    try {
        val inputStream = requireNotNull(context.contentResolver.openInputStream(sourceUri)) {
            "Unable to open the selected photo."
        }
        inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var totalBytes = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    totalBytes += count
                    require(totalBytes <= MAX_REMOTE_IMAGE_BYTES) {
                        "The selected photo must be smaller than 10 MB."
                    }
                    output.write(buffer, 0, count)
                }
            }
        }
        return destination.absolutePath
    } catch (exception: Exception) {
        destination.delete()
        throw exception
    }
}
