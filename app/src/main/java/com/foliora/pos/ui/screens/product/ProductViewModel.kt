package com.foliora.pos.ui.screens.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.CategoryEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.repository.CategoryRepository
import com.foliora.pos.data.repository.ProductRepository
import com.foliora.pos.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing product inventory in Foliora POS.
 * Connects UI layer with [ProductRepository] for product CRUD operations
 * and [CategoryRepository] for category selection options.
 *
 * @property productRepository Repository handling local database product operations.
 * @property categoryRepository Repository handling local database category operations.
 */
@HiltViewModel
class ProductViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentUserRole = MutableStateFlow<String>("CASHIER") // Default to Cashier for safety
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    init {
        fetchCurrentUserRole()
    }

    private fun fetchCurrentUserRole() {
        viewModelScope.launch {
            val firebaseUser = FirebaseAuth.getInstance().currentUser
            if (firebaseUser != null) {
                val dbUser = userRepository.getUserByFirebaseUid(firebaseUser.uid)
                if (dbUser != null) {
                    _currentUserRole.value = dbUser.role
                }
            }
        }
    }

    /**
     * Exposes the active list of products observed from the Room database.
     */
    val products: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Exposes all product categories available for dropdown selection when creating/editing products.
     */
    val categories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Adds a new product entity to the database with individual parameter fields.
     *
     * @param categoryId ID of the category this product belongs to.
     * @param name Name of the product.
     * @param buyingPrice Cost price per unit.
     * @param sellingPrice Retail price per unit.
     * @param stockQuantity Current inventory stock count.
     * @param unit Unit of measurement (e.g., pcs, kg, liters).
     * @param lowStockLimit Quantity threshold for low stock alert.
     * @param photoPath Local photo URI or file path (defaults to placeholder).
     * @param notes Additional details or notes.
     */
    fun addProduct(
        categoryId: Int,
        name: String,
        buyingPrice: Double,
        sellingPrice: Double,
        stockQuantity: Double,
        unit: String,
        lowStockLimit: Double = 5.0,
        photoPath: String? = null,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val newProduct = ProductEntity(
                categoryId = categoryId,
                name = name,
                buyingPrice = buyingPrice,
                sellingPrice = sellingPrice,
                stockQuantity = stockQuantity,
                unit = unit,
                lowStockLimit = lowStockLimit,
                photoPath = photoPath ?: "placeholder_photo_path",
                notes = notes,
                isActive = true,
                isSynced = false,
                firebaseId = null,
                createdAt = currentTime,
                updatedAt = currentTime
            )
            productRepository.insertProduct(newProduct)
        }
    }

    /**
     * Inserts a complete [ProductEntity] object into the repository.
     */
    fun addProduct(product: ProductEntity) {
        viewModelScope.launch {
            productRepository.insertProduct(
                product.copy(
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Updates an existing product entity in the database.
     * Marks the record as unsynced for future remote sync.
     *
     * @param product Product entity containing updated fields.
     */
    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch {
            productRepository.updateProduct(
                product.copy(
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Deletes a product entity from the database.
     *
     * @param product Product entity to delete.
     */
    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(product)
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Unable to delete product"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}
