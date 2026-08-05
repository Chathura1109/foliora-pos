package com.foliora.pos.ui.screens.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import com.foliora.pos.data.local.entity.SettingEntity
import com.foliora.pos.data.local.entity.UserEntity
import com.foliora.pos.data.repository.CustomerRepository
import com.foliora.pos.data.repository.ProductRepository
import com.foliora.pos.data.repository.SaleRepository
import com.foliora.pos.data.repository.SettingRepository
import com.foliora.pos.data.repository.UserRepository
import com.foliora.pos.ui.viewmodel.launchCrudCatching
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
 * ViewModel for managing sales history state and operations in Foliora POS.
 * Injects [SaleRepository] and [CustomerRepository] to expose state flows of sales history
 * and customer profiles for UI lookup, and handles sale deletion.
 */
@HiltViewModel
class SalesViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val productRepository: ProductRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    private val _currentUserRole = MutableStateFlow<String>("CASHIER") // Default to Cashier for safety
    val currentUserRole: StateFlow<String> = _currentUserRole.asStateFlow()

    private val _settings = MutableStateFlow<SettingEntity?>(null)
    val settings: StateFlow<SettingEntity?> = _settings.asStateFlow()

    init {
        fetchCurrentUserRole()
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settings.value = settingRepository.getSettings()
        }
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
     * StateFlow exposing all sales transactions ordered newest first.
     * Uses [SharingStarted.WhileSubscribed] to manage resource lifecycle efficiently.
     */
    val sales: StateFlow<List<SaleEntity>> = saleRepository.getAllSales()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * StateFlow exposing all registered customers so the UI can map customerId to a name.
     */
    val customers: StateFlow<List<CustomerEntity>> = customerRepository.getAllCustomers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * StateFlow exposing all products for UI lookup.
     */
    val products: StateFlow<List<ProductEntity>> = productRepository.getAllProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /** Users are used only to show the saved cashier name on historical receipts. */
    val users: StateFlow<List<UserEntity>> = userRepository.getAllUsers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Fetch line items for a specific sale transaction.
     */
    fun getSaleItems(saleId: Int) = saleRepository.getItemsBySaleId(saleId)

    /**
     * Deletes a sale transaction record from the database.
     *
     * @param sale The [SaleEntity] instance to delete.
     */
    fun deleteSale(sale: SaleEntity) {
        launchCrudCatching("Unable to delete sale") {
            saleRepository.deleteSale(sale)
        }
    }

    /**
     * Marks a pending sale as PAID.
     */
    fun markSaleAsPaid(sale: SaleEntity) {
        launchCrudCatching("Unable to mark sale as paid") {
            val updatedSale = sale.copy(
                status = "PAID",
                isSynced = false
            )
            saleRepository.updateSale(updatedSale)
        }
    }
}
