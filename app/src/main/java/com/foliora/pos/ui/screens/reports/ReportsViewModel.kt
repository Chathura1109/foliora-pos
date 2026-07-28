package com.foliora.pos.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.repository.ProductRepository
import com.foliora.pos.data.repository.SaleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

/**
 * ViewModel responsible for managing reports and analytics data in Foliora POS.
 * Connects to [SaleRepository] for daily revenue and pending transaction metrics,
 * and [ProductRepository] for low stock inventory alert flows.
 *
 * @property saleRepository Repository providing sale transaction and aggregate revenue data.
 * @property productRepository Repository providing inventory product details and stock alert queries.
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val saleRepository: SaleRepository,
    private val productRepository: ProductRepository
) : ViewModel() {

    private val startOfDay: Long
    private val endOfDay: Long

    init {
        // Calculate start and end timestamps (milliseconds) for the current calendar day
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        endOfDay = calendar.timeInMillis
    }

    /**
     * StateFlow emitting today's total sales revenue (Double).
     * Maps null SQL sum query results from [SaleRepository.getTodaysSalesTotal] to 0.0.
     */
    val todaysSalesTotal: StateFlow<Double> = saleRepository.getTodaysSalesTotal(startOfDay, endOfDay)
        .map { it ?: 0.0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    /**
     * StateFlow emitting today's total profit.
     */
    val todaysProfit: StateFlow<Double> = saleRepository.getTodaysProfit(startOfDay, endOfDay)
        .map { it ?: 0.0 }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0.0
        )

    /**
     * StateFlow emitting total count of pending sales transactions.
     */
    val pendingSalesCount: StateFlow<Int> = saleRepository.getPendingSalesCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    /**
     * StateFlow emitting active products whose stock levels are at or below their low stock threshold.
     */
    val lowStockProducts: StateFlow<List<ProductEntity>> = productRepository.getLowStockProducts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
