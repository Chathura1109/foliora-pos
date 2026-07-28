package com.foliora.pos.ui.screens.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.ui.components.ErrorDialog
import com.foliora.pos.ui.components.FolioraTopAppBar
import java.util.Locale

/**
 * Stateful screen composable for POS New Sale (Checkout).
 * Connects to [NewSaleViewModel] to manage product selection, cart items,
 * customer binding, payment method, and checkout submission.
 *
 * @param onBackClick Callback for navigating back.
 * @param onCheckoutSuccess Callback triggered after a successful checkout.
 * @param viewModel Hilt-injected instance of [NewSaleViewModel].
 */
@Composable
fun NewSaleScreen(
    onBackClick: () -> Unit,
    onCheckoutSuccess: () -> Unit = onBackClick,
    viewModel: NewSaleViewModel = hiltViewModel()
) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val paymentMethod by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val saleStatus by viewModel.saleStatus.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    NewSaleScreenContent(
        products = products,
        customers = customers,
        cartItems = cartItems,
        selectedCustomer = selectedCustomer,
        paymentMethod = paymentMethod,
        saleStatus = saleStatus,
        isProcessing = isProcessing,
        errorMessage = errorMessage,
        onBackClick = onBackClick,
        onAddToCart = { product, qty -> viewModel.addToCart(product, qty) },
        onRemoveFromCart = { product -> viewModel.removeFromCart(product) },
        onUpdateQuantity = { product, qty -> viewModel.updateQuantity(product, qty) },
        onSelectCustomer = { customer -> viewModel.selectCustomer(customer) },
        onSelectPaymentMethod = { method -> viewModel.setPaymentMethod(method) },
        onSelectSaleStatus = { status -> viewModel.setSaleStatus(status) },
        onCheckout = { viewModel.checkout(cashierId = 1, onSuccess = onCheckoutSuccess) },
        onDismissError = { viewModel.clearErrorMessage() }
    )
}

/**
 * Stateless UI container for the New Sale screen.
 * Displays top product list grid and bottom checkout cart breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSaleScreenContent(
    products: List<ProductEntity>,
    customers: List<CustomerEntity>,
    cartItems: List<CartItem>,
    selectedCustomer: CustomerEntity?,
    paymentMethod: String,
    saleStatus: String,
    isProcessing: Boolean,
    errorMessage: String?,
    onBackClick: () -> Unit,
    onAddToCart: (ProductEntity, Double) -> Unit,
    onRemoveFromCart: (ProductEntity) -> Unit,
    onUpdateQuantity: (ProductEntity, Double) -> Unit,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    onSelectPaymentMethod: (String) -> Unit,
    onSelectSaleStatus: (String) -> Unit,
    onCheckout: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var productForQuantityDialog by remember { mutableStateOf<ProductEntity?>(null) }

    // Filter active products matching search query
    val filteredProducts = remember(products, searchQuery) {
        if (searchQuery.isBlank()) {
            products
        } else {
            products.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Calculate grand total of current cart
    val grandTotal = remember(cartItems) {
        cartItems.sumOf { it.subtotal }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FolioraTopAppBar(
                title = "New Sale (POS)",
                onBackClick = onBackClick
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // TOP SECTION: Products Selection Grid (Weight 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Search Bar for Filtering Products
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search products to add...") },
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
                            POSProductCard(
                                product = product,
                                onClick = { productForQuantityDialog = product }
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
                            text = "Current Cart (${cartItems.size} items)",
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

            // BOTTOM SECTION: Cart, Customer & Payment Options, Checkout Button (Weight 1f)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                // Customer & Payment Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Customer Dropdown Box
                    Box(modifier = Modifier.weight(1f)) {
                        CustomerDropdownMenu(
                            customers = customers,
                            selectedCustomer = selectedCustomer,
                            onSelectCustomer = onSelectCustomer
                        )
                    }
                }

                // Payment Method Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Payment:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf(
                        "CASH" to Icons.Default.Money,
                        "CARD" to Icons.Default.CreditCard,
                        "BANK" to Icons.Default.Payments
                    ).forEach { (method, icon) ->
                        val isSelected = paymentMethod.equals(method, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectPaymentMethod(method) },
                            label = { Text(method) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                // Sale Status Selector Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Status:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    listOf(
                        "PAID" to Icons.Default.Check,
                        "PENDING" to Icons.Default.Remove
                    ).forEach { (status, icon) ->
                        val isSelected = saleStatus.equals(status, ignoreCase = true)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelectSaleStatus(status) },
                            label = { Text(status) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (status == "PAID") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                selectedLabelColor = if (status == "PAID") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                            )
                        )
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
                            text = "Tap a product above to add items to cart",
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
                            CartItemRow(
                                cartItem = item,
                                onUpdateQuantity = { qty -> onUpdateQuantity(item.product, qty) },
                                onRemove = { onRemoveFromCart(item.product) }
                            )
                        }
                    }
                }

                // Checkout Footer Action Card
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
                                text = "Grand Total",
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
                            enabled = cartItems.isNotEmpty() && !isProcessing,
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Processing...")
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Checkout",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Product Quantity Input Dialog
    if (productForQuantityDialog != null) {
        AddToCartQuantityDialog(
            product = productForQuantityDialog!!,
            onDismiss = { productForQuantityDialog = null },
            onConfirm = { quantity ->
                onAddToCart(productForQuantityDialog!!, quantity)
                productForQuantityDialog = null
            }
        )
    }

    // Error Alert Dialog
    if (!errorMessage.isNullOrEmpty()) {
        ErrorDialog(
            title = "Checkout Error",
            message = errorMessage,
            onDismiss = onDismissError
        )
    }
}

/**
 * Grid card composable representing a product available for sale.
 */
@Composable
private fun POSProductCard(
    product: ProductEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLowStock = product.stockQuantity <= product.lowStockLimit
    val formattedPrice = remember(product.sellingPrice) {
        String.format(Locale.getDefault(), "Rs.%.2f", product.sellingPrice)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedPrice,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isLowStock) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                ) {
                    Text(
                        text = "${product.stockQuantity.toInt()} ${product.unit}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLowStock) Color(0xFFD32F2F) else Color(0xFF388E3C),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/**
 * Row item composable for a product placed inside the checkout cart.
 */
@Composable
private fun CartItemRow(
    cartItem: CartItem,
    onUpdateQuantity: (Double) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val formattedSubtotal = remember(cartItem.subtotal) {
        String.format(Locale.getDefault(), "Rs.%.2f", cartItem.subtotal)
    }
    val formattedUnitPrice = remember(cartItem.product.sellingPrice) {
        String.format(Locale.getDefault(), "Rs.%.2f", cartItem.product.sellingPrice)
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
            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cartItem.product.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$formattedUnitPrice / ${cartItem.product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // Quantity adjusters (- / count / +)
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

            // Subtotal
            Text(
                text = formattedSubtotal,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Remove button
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
 * Dropdown menu composable for selecting a customer or walk-in customer.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDropdownMenu(
    customers: List<CustomerEntity>,
    selectedCustomer: CustomerEntity?,
    onSelectCustomer: (CustomerEntity?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedCustomer?.name ?: "Walk-in Customer",
            onValueChange = {},
            readOnly = true,
            label = { Text("Customer") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Walk-in Customer (Anonymous)",
                        fontWeight = if (selectedCustomer == null) FontWeight.Bold else FontWeight.Normal
                    )
                },
                onClick = {
                    onSelectCustomer(null)
                    expanded = false
                }
            )

            customers.forEach { customer ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = customer.name,
                                fontWeight = if (selectedCustomer?.id == customer.id) FontWeight.Bold else FontWeight.Normal
                            )
                            if (customer.phoneNumber.isNotEmpty()) {
                                Text(
                                    text = customer.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelectCustomer(customer)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Dialog prompting user to enter or adjust quantity when adding a product to cart.
 */
@Composable
private fun AddToCartQuantityDialog(
    product: ProductEntity,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var quantityText by remember { mutableStateOf("1") }
    var errorText by remember { mutableStateOf<String?>(null) }

    val formattedPrice = remember(product.sellingPrice) {
        String.format(Locale.getDefault(), "Rs.%.2f", product.sellingPrice)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add to Cart")
        },
        text = {
            Column {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Price: $formattedPrice / ${product.unit}  |  Available Stock: ${product.stockQuantity} ${product.unit}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            val currentVal = quantityText.toDoubleOrNull() ?: 1.0
                            val newVal = (currentVal - 1.0).coerceAtLeast(1.0)
                            quantityText = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease")
                    }

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = {
                            quantityText = it
                            errorText = null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .width(100.dp)
                            .padding(horizontal = 8.dp),
                        singleLine = true
                    )

                    IconButton(
                        onClick = {
                            val currentVal = quantityText.toDoubleOrNull() ?: 0.0
                            val newVal = currentVal + 1.0
                            quantityText = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else newVal.toString()
                        }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase")
                    }
                }

                if (errorText != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = quantityText.toDoubleOrNull()
                    if (parsed == null || parsed <= 0.0) {
                        errorText = "Enter a valid quantity greater than 0"
                    } else {
                        onConfirm(parsed)
                    }
                }
            ) {
                Text(text = "Add to Cart")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
