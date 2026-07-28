package com.foliora.pos.ui.screens.supplier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing supplier state and CRUD operations in Foliora POS.
 * Interacts with [SupplierRepository] to provide reactive supplier lists and handle entity operations.
 *
 * @property repository Repository providing access to supplier database operations.
 */
@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repository: SupplierRepository
) : ViewModel() {

    /**
     * Exposes all suppliers in the database ordered alphabetically by name as a StateFlow.
     */
    val suppliers: StateFlow<List<SupplierEntity>> = repository.getAllSuppliers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Adds a new supplier to the local database.
     *
     * @param name Name of the supplier.
     * @param phoneNumber Contact phone number.
     * @param address Physical address.
     * @param notes Optional notes regarding the supplier.
     * @param latitude Optional GPS latitude coordinate.
     * @param longitude Optional GPS longitude coordinate.
     */
    fun addSupplier(
        name: String,
        phoneNumber: String,
        address: String,
        notes: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        viewModelScope.launch {
            val supplier = SupplierEntity(
                name = name.trim(),
                phoneNumber = phoneNumber.trim(),
                address = address.trim(),
                notes = notes?.trim()?.ifEmpty { null },
                latitude = latitude,
                longitude = longitude
            )
            repository.insertSupplier(supplier)
        }
    }

    /**
     * Overloaded method to add a new [SupplierEntity] directly.
     *
     * @param supplier Supplier entity instance to insert.
     */
    fun addSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            repository.insertSupplier(supplier)
        }
    }

    /**
     * Updates an existing supplier record in the local database.
     *
     * @param supplier Updated supplier entity instance.
     */
    fun updateSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            repository.updateSupplier(supplier)
        }
    }

    /**
     * Deletes a supplier record from the local database.
     *
     * @param supplier Supplier entity instance to delete.
     */
    fun deleteSupplier(supplier: SupplierEntity) {
        viewModelScope.launch {
            repository.deleteSupplier(supplier)
        }
    }
}
