package com.foliora.pos.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.PurchaseItemEntity
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.data.local.entity.priceToCents
import com.foliora.pos.data.repository.InventoryBatchRepository
import com.foliora.pos.data.repository.ProductRepository
import com.foliora.pos.data.repository.PurchaseRepository
import com.foliora.pos.data.repository.SupplierRepository
import com.foliora.pos.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Data class representing an item placed inside the restocking purchase order cart.
 *
 * @property product The [ProductEntity] selected for restock purchase.
 * @property quantity The quantity unit amount being purchased.
 * @property buyingPrice The custom unit buying price for this specific restock batch.
 * @property sellingPrice The unit selling price for this specific restock batch.
 */
data class PurchaseCartItem(
    val product: ProductEntity,
    val batchId: Int?,
    val quantity: Double,
    val buyingPrice: Double,
    val sellingPrice: Double
) {
    val lineKey: String
        get() = batchId?.let { "batch:$it" }
            ?: "price:${product.id}:${priceToCents(buyingPrice)}:${priceToCents(sellingPrice)}"

    /**
     * Line item subtotal calculated as quantity multiplied by the custom buying price.
     */
    val subtotal: Double
        get() = quantity * buyingPrice
}

/**
 * ViewModel managing the stock purchase/restocking workflow in Foliora POS.
 * Connects UI elements to [ProductRepository], [SupplierRepository], [PurchaseRepository], and [UserRepository].
 * Handles active product queries, supplier selection, cart updates with custom unit buying prices,
 * and purchase order execution with automated stock quantity incrementation.
 *
 * @property productRepository Repository for inventory product records.
 * @property supplierRepository Repository for vendor/supplier profile records.
 * @property purchaseRepository Repository for purchase order transactions and stock incrementation.
 * @property userRepository Repository for user profile records and foreign key safety checks.
 */
@HiltViewModel
class NewPurchaseViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val supplierRepository: SupplierRepository,
    private val purchaseRepository: PurchaseRepository,
    private val inventoryBatchRepository: InventoryBatchRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val firebaseAuth = FirebaseAuth.getInstance()

    /**
     * Active products available in inventory for selection and restocking.
     */
    val products: StateFlow<List<ProductEntity>> = productRepository.getActiveProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * All registered suppliers for selecting the purchase order vendor.
     */
    val suppliers: StateFlow<List<SupplierEntity>> = supplierRepository.getAllSuppliers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** All current and exhausted price batches available for restock selection. */
    val inventoryBatches: StateFlow<List<InventoryBatchEntity>> =
        inventoryBatchRepository.getAllBatches()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _cartItems = MutableStateFlow<List<PurchaseCartItem>>(emptyList())
    /**
     * Current list of line items added to the restock cart.
     */
    val cartItems: StateFlow<List<PurchaseCartItem>> = _cartItems.asStateFlow()

    private val _selectedSupplier = MutableStateFlow<SupplierEntity?>(null)
    /**
     * Selected supplier entity for this purchase order.
     */
    val selectedSupplier: StateFlow<SupplierEntity?> = _selectedSupplier.asStateFlow()

    private val _isProcessing = MutableStateFlow<Boolean>(false)
    /**
     * Indicates whether restocking transaction is currently processing.
     */
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    /**
     * Error message displayed during checkout validation or process failures.
     */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Selects the supplier for this purchase transaction.
     *
     * @param supplier The [SupplierEntity] selected by user, or null.
     */
    fun selectSupplier(supplier: SupplierEntity?) {
        _selectedSupplier.value = supplier
    }

    /**
     * Adds a product to the restock cart with specified quantity and custom buying price.
     * Allows overriding the default product buying price for this specific restock batch.
     * If product already exists in cart, updates quantity and updates buying price to custom value.
     *
     * @param product Product to restock.
     * @param quantity Quantity amount to add.
     * @param customBuyingPrice Custom unit buying price for this purchase.
     */
    fun addToCart(
        product: ProductEntity,
        selectedBatch: InventoryBatchEntity?,
        quantity: Double,
        customBuyingPrice: Double,
        customSellingPrice: Double
    ) {
        if (!quantity.isFinite() || quantity <= 0) return
        if (!customBuyingPrice.isFinite() || customBuyingPrice < 0) return
        if (!customSellingPrice.isFinite() || customSellingPrice < 0) return

        val batchId = selectedBatch
            ?.takeIf { batch ->
                batch.productId == product.id &&
                    batch.unitCostCents == priceToCents(customBuyingPrice) &&
                    batch.sellingPriceCents == priceToCents(customSellingPrice)
            }
            ?.id
        val newItem = PurchaseCartItem(
            product = product,
            batchId = batchId,
            quantity = quantity,
            buyingPrice = customBuyingPrice,
            sellingPrice = customSellingPrice
        )
        val currentList = _cartItems.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.lineKey == newItem.lineKey }

        if (existingIndex >= 0) {
            val existingItem = currentList[existingIndex]
            val updatedQuantity = existingItem.quantity + quantity
            currentList[existingIndex] = existingItem.copy(
                quantity = updatedQuantity,
                buyingPrice = customBuyingPrice,
                sellingPrice = customSellingPrice
            )
        } else {
            currentList.add(newItem)
        }

        _cartItems.value = currentList
    }

    /**
     * Removes a product entirely from the restock cart.
     *
     * @param product Product to remove.
     */
    fun removeFromCart(cartItem: PurchaseCartItem) {
        _cartItems.value = _cartItems.value.filter { it.lineKey != cartItem.lineKey }
    }

    /**
     * Updates the exact quantity of a product in the restock cart.
     * If quantity is 0 or less, removes the item from the cart.
     *
     * @param product Product to update.
     * @param quantity New quantity value.
     */
    fun updateQuantity(cartItem: PurchaseCartItem, quantity: Double) {
        if (quantity <= 0) {
            removeFromCart(cartItem)
            return
        }

        _cartItems.value = _cartItems.value.map { item ->
            if (item.lineKey == cartItem.lineKey) {
                item.copy(quantity = quantity)
            } else {
                item
            }
        }
    }

    /**
     * Clears all items from the current restock cart.
     */
    fun clearCart() {
        _cartItems.value = emptyList()
    }

    /**
     * Clears active error messages.
     */
    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    /**
     * Completes the restocking purchase order:
     * 1. Validates that a supplier is selected and cart is not empty.
     * 2. Resolves the authenticated Firebase account to its local user record.
     * 3. Creates [PurchaseEntity] header and maps cart items to [PurchaseItemEntity] instances.
     * 4. Invokes `purchaseRepository.completePurchase(purchase, items)` to save order and increment inventory stock.
     * 5. Triggers [onSuccess] callback upon completion.
     *
     * @param onSuccess Callback triggered after successful transaction completion.
     */
    fun checkout(onSuccess: () -> Unit = {}) {
        val currentCart = _cartItems.value
        val supplier = _selectedSupplier.value

        if (supplier == null) {
            _errorMessage.value = "Please select a supplier for this restock order."
            return
        }

        if (currentCart.isEmpty()) {
            _errorMessage.value = "Cart is empty. Please select products to restock."
            return
        }

        viewModelScope.launch {
            try {
                _isProcessing.value = true
                val firebaseUser = checkNotNull(firebaseAuth.currentUser) {
                    "You must be logged in to complete a purchase"
                }
                val creator = checkNotNull(
                    userRepository.getUserByFirebaseUid(firebaseUser.uid)
                ) {
                    "Your user profile is unavailable. Sign in again"
                }
                check(creator.isActive) { "Your user account is inactive" }

                val currentTime = System.currentTimeMillis()
                val grandTotal = currentCart.sumOf { it.subtotal }

                // Create Purchase header record
                val purchase = PurchaseEntity(
                    id = 0,
                    supplierId = supplier.id,
                    date = currentTime,
                    totalCost = grandTotal,
                    status = "COMPLETED",
                    createdBy = creator.id,
                    isSynced = false,
                    firebaseId = null,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )

                // Map restock cart items to PurchaseItem line entities
                val items = currentCart.map { cartItem ->
                    PurchaseItemEntity(
                        id = 0,
                        purchaseId = 0, // Auto-assigned during repository completePurchase
                        productId = cartItem.product.id,
                        batchId = cartItem.batchId,
                        quantity = cartItem.quantity,
                        buyingPrice = cartItem.buyingPrice,
                        sellingPrice = cartItem.sellingPrice,
                        subtotal = cartItem.subtotal,
                        isSynced = false,
                        firebaseId = null,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                }

                // Execute completePurchase transaction (persists order header, items, and increments stock)
                purchaseRepository.completePurchase(purchase, items)

                _cartItems.value = emptyList()
                _selectedSupplier.value = null
                _isProcessing.value = false
                onSuccess()
            } catch (e: Exception) {
                _isProcessing.value = false
                _errorMessage.value = e.localizedMessage ?: "Failed to process restock purchase."
            }
        }
    }
}
