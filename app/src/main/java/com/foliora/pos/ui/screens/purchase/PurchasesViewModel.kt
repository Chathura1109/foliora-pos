package com.foliora.pos.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.data.repository.PurchaseRepository
import com.foliora.pos.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for managing purchases history state and operations in Foliora POS.
 * Injects [PurchaseRepository] and [SupplierRepository] to expose state flows of stock purchase records
 * and supplier details for UI lookup, and handles purchase deletion.
 */
@HiltViewModel
class PurchasesViewModel @Inject constructor(
    private val purchaseRepository: PurchaseRepository,
    private val supplierRepository: SupplierRepository
) : ViewModel() {

    /**
     * StateFlow exposing all purchase orders sorted newest first.
     */
    val purchases: StateFlow<List<PurchaseEntity>> = purchaseRepository.getAllPurchases()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * StateFlow exposing all registered suppliers so the UI can map supplierId to a supplier name.
     */
    val suppliers: StateFlow<List<SupplierEntity>> = supplierRepository.getAllSuppliers()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Deletes a purchase transaction record from the local database.
     *
     * @param purchase The [PurchaseEntity] instance to delete.
     */
    fun deletePurchase(purchase: PurchaseEntity) {
        viewModelScope.launch {
            purchaseRepository.deletePurchase(purchase)
        }
    }
}
