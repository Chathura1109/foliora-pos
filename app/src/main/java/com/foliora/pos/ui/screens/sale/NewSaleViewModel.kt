package com.foliora.pos.ui.screens.sale

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import com.foliora.pos.data.repository.CustomerRepository
import com.foliora.pos.data.repository.InventoryBatchRepository
import com.foliora.pos.data.repository.ProductRepository
import com.foliora.pos.data.repository.SaleRepository
import com.foliora.pos.data.repository.SettingRepository
import com.foliora.pos.data.repository.UserRepository
import com.foliora.pos.data.sync.SyncManager
import com.foliora.pos.ui.receipt.ReceiptData
import com.foliora.pos.ui.receipt.ReceiptLineItem
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Data class representing an item placed inside the checkout shopping cart.
 *
 * @property product The [ProductEntity] selected for sale.
 * @property quantity The quantity unit amount being purchased.
 */
data class CartItem(
    val product: ProductEntity,
    val batch: InventoryBatchEntity,
    val quantity: Double
) {
    /**
     * Line item total calculated using the selected batch selling price.
     */
    val subtotal: Double
        get() = batch.sellingPrice * quantity
}

data class InventorySyncStatus(
    val isInProgress: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false
)

/**
 * ViewModel managing the point-of-sale checkout process in Foliora POS.
 * Connects the UI to [ProductRepository], [CustomerRepository], and [SaleRepository]
 * to maintain cart state, calculate subtotals/totals, handle customer/payment selection,
 * and execute sale transactions with automatic stock deduction.
 *
 * @property productRepository Repository handling product inventory data.
 * @property customerRepository Repository handling customer profile records.
 * @property saleRepository Repository handling sales records and transaction checkout.
 */
@HiltViewModel
class NewSaleViewModel @Inject constructor(
    @ApplicationContext private val applicationContext: Context,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val inventoryBatchRepository: InventoryBatchRepository,
    private val saleRepository: SaleRepository,
    private val settingRepository: SettingRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()

    private val _currentUserRole = MutableStateFlow("CASHIER")
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val pendingInventorySync = combine(
        productRepository.observePendingSyncCount(),
        inventoryBatchRepository.observePendingSyncCount()
    ) { productCount, batchCount ->
        productCount > 0 || batchCount > 0
    }

    val hasPendingInventorySync: StateFlow<Boolean> = pendingInventorySync.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    private val _inventorySyncStatus = MutableStateFlow(InventorySyncStatus())
    val inventorySyncStatus: StateFlow<InventorySyncStatus> = _inventorySyncStatus.asStateFlow()

    private val _completedReceipt = MutableStateFlow<ReceiptData?>(null)
    val completedReceipt: StateFlow<ReceiptData?> = _completedReceipt.asStateFlow()

    init {
        viewModelScope.launch {
            val firebaseUid = firebaseAuth.currentUser?.uid ?: return@launch
            _currentUserRole.value = userRepository.getUserByFirebaseUid(firebaseUid)?.role ?: "CASHIER"
        }
        // Refresh once whenever a new POS ViewModel is opened so cloud stock changes are pulled too.
        syncInventory()
    }

    fun syncInventory() {
        if (_inventorySyncStatus.value.isInProgress) return

        val workId = SyncManager.triggerImmediateSync(applicationContext)
        _inventorySyncStatus.value = InventorySyncStatus(
            isInProgress = true,
            message = "Waiting for internet connection..."
        )

        viewModelScope.launch {
            WorkManager.getInstance(applicationContext)
                .getWorkInfoByIdFlow(workId)
                .filterNotNull()
                .first { workInfo ->
                    _inventorySyncStatus.value = when (workInfo.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.BLOCKED -> InventorySyncStatus(
                            isInProgress = true,
                            message = if (workInfo.runAttemptCount > 0) {
                                "Inventory sync will retry automatically..."
                            } else {
                                "Waiting for internet connection..."
                            }
                        )
                        WorkInfo.State.RUNNING -> InventorySyncStatus(
                            isInProgress = true,
                            message = "Synchronising inventory..."
                        )
                        WorkInfo.State.SUCCEEDED -> InventorySyncStatus(
                            message = "Inventory synchronised successfully."
                        )
                        WorkInfo.State.FAILED -> InventorySyncStatus(
                            message = "Inventory sync failed. Please try again.",
                            isError = true
                        )
                        WorkInfo.State.CANCELLED -> InventorySyncStatus(
                            message = "Inventory sync was cancelled. Please try again.",
                            isError = true
                        )
                    }
                    workInfo.state.isFinished
                }
        }
    }

    /**
     * Active products available in inventory for selection.
     */
    val products: StateFlow<List<ProductEntity>> = productRepository.getActiveProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * All registered customers for customer selection during checkout.
     */
    val customers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Non-exhausted batches available for manual selection. */
    val availableBatches: StateFlow<List<InventoryBatchEntity>> =
        inventoryBatchRepository.getAvailableBatches()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    /**
     * Current list of line items added to the checkout cart.
     */
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    /**
     * Customer entity associated with the current sale transaction (null for walk-in customer).
     */
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomer.asStateFlow()

    private val _paymentMethod = MutableStateFlow<String>("CASH")
    /**
     * Selected payment method (e.g. CASH, CARD, BANK).
     */
    val paymentMethod: StateFlow<String> = _paymentMethod.asStateFlow()

    private val _saleStatus = MutableStateFlow<String>("PAID")
    /**
     * Selected status (PAID, PENDING).
     */
    val saleStatus: StateFlow<String> = _saleStatus.asStateFlow()

    private val _isProcessing = MutableStateFlow<Boolean>(false)
    /**
     * Indicates whether checkout process is currently running.
     */
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /**
     * Error message string for displaying checkout validation issues.
     */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Adds a product to the cart with the given quantity.
     * If the product is already in the cart, increments the existing quantity.
     *
     * @param product The product to add.
     * @param quantity The quantity to add.
     */
    fun addToCart(product: ProductEntity, batch: InventoryBatchEntity, quantity: Double) {
        if (!quantity.isFinite() || quantity <= 0) {
            _errorMessage.value = "Enter a valid quantity greater than zero"
            return
        }

        if (batch.productId != product.id) {
            _errorMessage.value = "Selected stock batch does not belong to ${product.name}"
            return
        }

        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.batch.id == batch.id }
        val requestedQuantity = if (existingIndex >= 0) {
            currentList[existingIndex].quantity + quantity
        } else {
            quantity
        }

        if (!isQuantityAvailable(product, batch, requestedQuantity)) return

        if (existingIndex >= 0) {
            // Update existing cart item quantity by adding requested amount
            val existingItem = currentList[existingIndex]
            currentList[existingIndex] = existingItem.copy(quantity = requestedQuantity)
        } else {
            // Add new cart item entry
            currentList.add(CartItem(product = product, batch = batch, quantity = quantity))
        }

        _cartItems.value = currentList
    }

    /**
     * Removes a product entirely from the checkout cart.
     *
     * @param product Product to remove.
     */
    fun removeFromCart(cartItem: CartItem) {
        _cartItems.value = _cartItems.value.filter { it.batch.id != cartItem.batch.id }
    }

    /**
     * Updates the exact quantity of a product in the cart.
     * If quantity is 0 or less, removes the item from the cart.
     *
     * @param product Product to update.
     * @param quantity New quantity value.
     */
    fun updateQuantity(cartItem: CartItem, quantity: Double) {
        if (!quantity.isFinite()) {
            _errorMessage.value = "Enter a valid quantity"
            return
        }

        if (quantity <= 0) {
            removeFromCart(cartItem)
            return
        }

        if (!isQuantityAvailable(cartItem.product, cartItem.batch, quantity)) return

        _cartItems.value = _cartItems.value.map { item ->
            if (item.batch.id == cartItem.batch.id) {
                item.copy(quantity = quantity)
            } else {
                item
            }
        }
    }

    private fun isQuantityAvailable(
        product: ProductEntity,
        batch: InventoryBatchEntity,
        requestedQuantity: Double
    ): Boolean {
        val availableQuantity = batch.remainingQuantity
        if (!availableQuantity.isFinite() || availableQuantity < 0) {
            _errorMessage.value = "Invalid stock quantity for the selected ${product.name} batch"
            return false
        }
        if (!requestedQuantity.isFinite() || requestedQuantity > availableQuantity) {
            _errorMessage.value =
                "Only $availableQuantity ${product.unit} remains in the selected batch"
            return false
        }
        return true
    }

    /**
     * Selects the customer associated with the sale.
     *
     * @param customer Customer entity, or null for anonymous/walk-in sales.
     */
    fun selectCustomer(customer: CustomerEntity?) {
        _selectedCustomer.value = customer
    }

    /**
     * Sets the payment method for the transaction.
     *
     * @param method Payment method string (CASH, CARD, BANK).
     */
    fun setPaymentMethod(method: String) {
        _paymentMethod.value = method
    }

    /**
     * Sets the status of the sale (PAID or PENDING).
     */
    fun setSaleStatus(status: String) {
        _saleStatus.value = status
    }

    /**
     * Clears any active error messages.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Clears all items from the current cart.
     */
    fun clearCart() {
        _cartItems.value = emptyList()
    }

    /** Removes the completed receipt after the cashier chooses Done. */
    fun clearCompletedReceipt() {
        _completedReceipt.value = null
    }

    /**
     * Completes the checkout transaction:
     * 1. Validates that cart is not empty.
     * 2. Builds [SaleEntity] and maps cart items to [SaleItemEntity].
     * 3. Calls `saleRepository.completeSale` to persist transaction and deduct inventory stock.
     * 4. Exposes a saved receipt snapshot for preview and printing.
     */
    fun checkout() {
        val currentCart = _cartItems.value
        if (currentCart.isEmpty()) {
            _errorMessage.value = "Cart is empty. Please add items to checkout."
            return
        }
        if (
            hasPendingInventorySync.value ||
            _inventorySyncStatus.value.isInProgress ||
            _inventorySyncStatus.value.isError
        ) {
            _errorMessage.value = "Synchronise inventory successfully before checkout."
            return
        }
        if (!_isProcessing.compareAndSet(expect = false, update = true)) return

        viewModelScope.launch {
            try {
                val firebaseUser = checkNotNull(firebaseAuth.currentUser) {
                    "You must be logged in to complete a sale"
                }
                val cashier = checkNotNull(
                    userRepository.getUserByFirebaseUid(firebaseUser.uid)
                ) {
                    "Your user profile is unavailable. Sign in again"
                }
                check(cashier.isActive) { "Your user account is inactive" }

                val currentTime = System.currentTimeMillis()
                val grandTotal = currentCart.sumOf { it.subtotal }

                // Create Sale header record
                val sale = SaleEntity(
                    id = 0,
                    customerId = _selectedCustomer.value?.id,
                    cashierId = cashier.id,
                    date = currentTime,
                    totalAmount = grandTotal,
                    paymentMethod = _paymentMethod.value,
                    status = _saleStatus.value,
                    isSynced = false,
                    firebaseId = null,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )

                // Map cart line items to SaleItem entities
                val items = currentCart.map { cartItem ->
                    SaleItemEntity(
                        id = 0,
                        saleId = 0, // Assigned inside completeSale
                        productId = cartItem.product.id,
                        batchId = cartItem.batch.id,
                        quantity = cartItem.quantity,
                        sellingPrice = cartItem.batch.sellingPrice,
                        unitCost = cartItem.batch.unitCost,
                        subtotal = cartItem.subtotal,
                        isSynced = false,
                        firebaseId = null,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                }

                val completedSaleId = saleRepository.completeSale(sale, items).toInt()
                val completedSale = checkNotNull(saleRepository.getSaleById(completedSaleId)) {
                    "Completed sale could not be loaded for its receipt"
                }
                val completedItems = saleRepository.getItemsBySaleId(completedSaleId)
                    .first { savedItems -> savedItems.isNotEmpty() }
                val settings = settingRepository.getSettings()
                val productNames = currentCart.associate { it.product.id to it.product.name }

                _completedReceipt.value = ReceiptData(
                    saleId = completedSale.id,
                    date = completedSale.date,
                    shopName = settings?.shopName ?: "Foliora",
                    shopAddress = settings?.address.orEmpty(),
                    shopPhone = settings?.phoneNumber.orEmpty(),
                    cashierName = cashier.name,
                    customerName = _selectedCustomer.value?.name,
                    paymentMethod = completedSale.paymentMethod,
                    status = completedSale.status,
                    items = completedItems.map { item ->
                        ReceiptLineItem(
                            productName = productNames[item.productId] ?: "Product #${item.productId}",
                            quantity = item.quantity,
                            sellingPrice = item.sellingPrice,
                            subtotal = item.subtotal
                        )
                    },
                    totalAmount = completedSale.totalAmount,
                    receiptMessage = settings?.receiptMessage.orEmpty()
                )

                // Reset the cart only after the saved receipt snapshot has been prepared.
                _cartItems.value = emptyList()
                _isProcessing.value = false
            } catch (e: Exception) {
                _isProcessing.value = false
                _errorMessage.value = e.localizedMessage ?: "Failed to process sale."
            }
        }
    }
}
