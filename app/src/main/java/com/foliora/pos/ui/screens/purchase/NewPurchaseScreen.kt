@file:OptIn(ExperimentalMaterial3Api::class)

package com.foliora.pos.ui.screens.purchase

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.ui.components.ErrorDialog
import com.foliora.pos.ui.components.FolioraTopAppBar
import java.util.Locale

/**
 * Stateful composable for the New Purchase (Restocking) Screen.
 * Connects to [NewPurchaseViewModel] to manage product selection, supplier binding,
 * restock cart modifications with custom buying prices, and complete purchase submission.
 *
 * @param onBackClick Callback for navigating back to previous screen.
 * @param onCheckoutSuccess Callback invoked when restocking is completed successfully.
 * @param viewModel Hilt-injected instance of [NewPurchaseViewModel].
 */
@Composable
fun NewPurchaseScreen(
    onBackClick: () -> Unit,
    onCheckoutSuccess: () -> Unit = onBackClick,
    viewModel: NewPurchaseViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val suppliers by viewModel.suppliers.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val selectedSupplier by viewModel.selectedSupplier.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    NewPurchaseScreenContent(
        products = products,
        suppliers = suppliers,
        cartItems = cartItems,
        selectedSupplier = selectedSupplier,
        isProcessing = isProcessing,
        errorMessage = errorMessage,
        onBackClick = onBackClick,
        onAddToCart = { product, qty, buyingPrice -> viewModel.addToCart(product, qty, buyingPrice) },
        onRemoveFromCart = { product -> viewModel.removeFromCart(product) },
        onUpdateQuantity = { product, qty -> viewModel.updateQuantity(product, qty) },
        onSelectSupplier = { supplier -> viewModel.selectSupplier(supplier) },
        onCheckout = { viewModel.checkout(userId = 1, onSuccess = onCheckoutSuccess) },
        onDismissError = { viewModel.clearErrorMessage() }
    )
}

/**
 * Stateless UI container for the New Purchase (Restocking) Screen.
 * Top section displays product grid to pick items for restock.
 * Bottom section displays supplier selector, restock cart line items, and Grand Total action bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPurchaseScreenContent(
    products: List<ProductEntity>,
    suppliers: List<SupplierEntity>,
    cartItems: List<PurchaseCartItem>,
    selectedSupplier: SupplierEntity?,
    isProcessing: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onAddToCart: (ProductEntity, Double, Double) -> Unit,
    onRemoveFromCart: (ProductEntity) -> Unit,
    onUpdateQuantity: (ProductEntity, Double) -> Unit,
    onSelectSupplier: (SupplierEntity?) -> Unit,
    onCheckout: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var productForRestockDialog by remember { mutableStateOf<ProductEntity?>(null) }

    // Filter active products matching search query
    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        (it.notes?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    // Calculate grand total of restock order
    val grandTotal = remember(cartItems) {
        cartItems.sumOf { it.subtotal }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "New Purchase (Restocking)",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TOP SECTION: Products Grid for Restocking Selection (Weight 1.1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Search Bar for Product Filtering
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search products to restock...") },
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

                Spacer(modifier = Modifier.height(8.dp))

                // Product Grid View
                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (products.isEmpty()) "No active products in inventory" else "No matching products found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = filteredProducts,
                            key = { it.id }
                        ) { product ->
                            val inCart = cartItems.any { it.product.id == product.id }
                            RestockProductCard(
                                product = product,
                                isInCart = inCart,
                                onClick = { productForRestockDialog = product }
                            )
                        }
                    }
                }
            }

            // MIDDLE SEPARATOR / HEADER
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Restock Cart (${cartItems.size} items)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (cartItems.isNotEmpty()) {
                        Text(
                            text = String.format(Locale.getDefault(), "Rs.%.2f", grandTotal),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // BOTTOM SECTION: Supplier Selection, Cart Items List, and Checkout (Weight 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Supplier Dropdown Selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    SupplierDropdownMenu(
                        suppliers = suppliers,
                        selectedSupplier = selectedSupplier,
                        onSelectSupplier = onSelectSupplier
                    )
                }

                // Supplier Quick Chips Row (If available)
                if (suppliers.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Quick:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        suppliers.take(3).forEach { supplier ->
                            val isSelected = selectedSupplier?.id == supplier.id
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectSupplier(if (isSelected) null else supplier) },
                                label = {
                                    Text(
                                        text = supplier.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // Cart Line Items List
                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tap a product above to add to restock cart",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = cartItems,
                            key = { it.product.id }
                        ) { item ->
                            PurchaseCartItemRow(
                                cartItem = item,
                                onUpdateQuantity = { qty -> onUpdateQuantity(item.product, qty) },
                                onRemove = { onRemoveFromCart(item.product) }
                            )
                        }
                    }
                }

                // Complete Restock Action Footer Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Grand Total Cost",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = String.format(Locale.getDefault(), "Rs.%.2f", grandTotal),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(
                            onClick = onCheckout,
                            enabled = cartItems.isNotEmpty() && selectedSupplier != null && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Saving...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Complete Restock",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Product Restock Dialog for Quantity and Custom Buying Price Input
    if (productForRestockDialog != null) {
        val existingCartItem = cartItems.find { it.product.id == productForRestockDialog!!.id }
        val initialBuyingPrice = existingCartItem?.buyingPrice ?: productForRestockDialog!!.buyingPrice

        AddToRestockDialog(
            product = productForRestockDialog!!,
            initialBuyingPrice = initialBuyingPrice,
            onDismiss = { productForRestockDialog = null },
            onConfirm = { quantity, buyingPrice ->
                onAddToCart(productForRestockDialog!!, quantity, buyingPrice)
                productForRestockDialog = null
            }
        )
    }

    // Error Dialog
    if (!errorMessage.isNullOrEmpty()) {
        ErrorDialog(
            title = "Restock Error",
            message = errorMessage,
            onDismiss = onDismissError
        )
    }
}

/**
 * Grid card composable representing a product available for restocking.
 */
@Composable
private fun RestockProductCard(
    product: ProductEntity,
    isInCart: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedBuyingPrice = remember(product.buyingPrice) {
        String.format(Locale.getDefault(), "Rs.%.2f", product.buyingPrice)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isInCart) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isInCart) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "IN CART",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Buy: $formattedBuyingPrice / ${product.unit}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Current Stock: ${product.stockQuantity.toInt()} ${product.unit}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * Row composable displaying a line item added to the restocking purchase order cart.
 */
@Composable
private fun PurchaseCartItemRow(
    cartItem: PurchaseCartItem,
    onUpdateQuantity: (Double) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedSubtotal = remember(cartItem.subtotal) {
        String.format(Locale.getDefault(), "Rs.%.2f", cartItem.subtotal)
    }
    val formattedBuyingPrice = remember(cartItem.buyingPrice) {
        String.format(Locale.getDefault(), "Rs.%.2f", cartItem.buyingPrice)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Product Name & Unit Cost Breakdown
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "@ $formattedBuyingPrice / ${cartItem.product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Quantity Controls (- / count / +)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onUpdateQuantity((cartItem.quantity - 1.0).coerceAtLeast(0.0)) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease Quantity",
                        modifier = Modifier.size(16.dp)
                    )
                }

                Text(
                    text = if (cartItem.quantity % 1.0 == 0.0) cartItem.quantity.toInt().toString() else cartItem.quantity.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = { onUpdateQuantity(cartItem.quantity + 1.0) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase Quantity",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Subtotal amount
            Text(
                text = formattedSubtotal,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Remove item button
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove Item",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Dropdown menu composable for selecting a vendor/supplier for restock purchase orders.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupplierDropdownMenu(
    suppliers: List<SupplierEntity>,
    selectedSupplier: SupplierEntity?,
    onSelectSupplier: (SupplierEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedSupplier?.name ?: "Select Supplier *",
            onValueChange = {},
            readOnly = true,
            label = { Text("Supplier / Vendor *") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            isError = selectedSupplier == null
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (suppliers.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No registered suppliers found", color = MaterialTheme.colorScheme.outline) },
                    onClick = { expanded = false }
                )
            } else {
                suppliers.forEach { supplier ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = supplier.name,
                                    fontWeight = if (selectedSupplier?.id == supplier.id) FontWeight.Bold else FontWeight.Normal
                                )
                                if (supplier.phoneNumber.isNotEmpty() || supplier.address.isNotEmpty()) {
                                    Text(
                                        text = listOf(supplier.phoneNumber, supplier.address).filter { it.isNotEmpty() }.joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        },
                        onClick = {
                            onSelectSupplier(supplier)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/**
 * Dialog for entering quantity AND overriding custom unit buying price when adding/editing a product in restock cart.
 */
@Composable
private fun AddToRestockDialog(
    product: ProductEntity,
    initialBuyingPrice: Double,
    onDismiss: () -> Unit,
    onConfirm: (quantity: Double, buyingPrice: Double) -> Unit
) {
    var quantityText by remember { mutableStateOf("1") }
    var buyingPriceText by remember { mutableStateOf(String.format(Locale.US, "%.2f", initialBuyingPrice)) }
    var inputError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add to Restock Order")
        },
        text = {
            Column {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Current Stock: ${product.stockQuantity} ${product.unit}  | Default Buying Price: Rs.${String.format(Locale.getDefault(), "%.2f", product.buyingPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
                )

                // Restock Quantity Input Field
                OutlinedTextField(
                    value = quantityText,
                    onValueChange = {
                        quantityText = it
                        inputError = null
                    },
                    label = { Text("Restock Quantity (${product.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Custom Buying Price Input Field (Override default)
                OutlinedTextField(
                    value = buyingPriceText,
                    onValueChange = {
                        buyingPriceText = it
                        inputError = null
                    },
                    label = { Text("Unit Buying Price (Rs.)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                if (inputError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = inputError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = quantityText.toDoubleOrNull()
                    val price = buyingPriceText.toDoubleOrNull()

                    if (qty == null || qty <= 0) {
                        inputError = "Please enter a valid quantity > 0"
                        return@Button
                    }
                    if (price == null || price < 0) {
                        inputError = "Please enter a valid buying price >= 0"
                        return@Button
                    }

                    onConfirm(qty, price)
                }
            ) {
                Text("Add to Restock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
