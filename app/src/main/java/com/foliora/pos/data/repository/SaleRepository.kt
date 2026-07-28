package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.SaleDao
import com.foliora.pos.data.local.dao.SaleItemDao
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing point-of-sale transactions and sale cart items in Foliora POS.
 * Integrates [SaleDao], [SaleItemDao], and [ProductDao] to execute checkout workflows, total revenue calculations,
 * and automatic inventory stock deduction.
 *
 * @property saleDao Data access object for sale transaction headers.
 * @property saleItemDao Data access object for sale line items.
 * @property productDao Data access object for product inventory stock deduction.
 */
class SaleRepository @Inject constructor(
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val productDao: ProductDao
) {

    // --- SaleDao Wrapped Methods ---

    /**
     * Inserts a sale entity into the database with an updated timestamp.
     */
    suspend fun insertSale(sale: SaleEntity): Long {
        return saleDao.insertSale(sale.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing sale record with an updated timestamp.
     */
    suspend fun updateSale(sale: SaleEntity) {
        saleDao.updateSale(sale.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a sale record from the database.
     */
    suspend fun deleteSale(sale: SaleEntity) {
        saleDao.deleteSale(sale)
    }

    /**
     * Retrieves a single sale header by unique ID.
     */
    suspend fun getSaleById(id: Int): SaleEntity? {
        return saleDao.getSaleById(id)
    }

    /**
     * Observes all sales transactions ordered newest first.
     */
    fun getAllSales(): Flow<List<SaleEntity>> {
        return saleDao.getAllSales()
    }

    /**
     * Observes all sales linked to a specific customer ID.
     */
    fun getSalesByCustomer(customerId: Int): Flow<List<SaleEntity>> {
        return saleDao.getSalesByCustomer(customerId)
    }

    /**
     * Observes sales filtered by status (e.g. 'PAID', 'PENDING', 'CANCELLED').
     */
    fun getSalesByStatus(status: String): Flow<List<SaleEntity>> {
        return saleDao.getSalesByStatus(status)
    }

    /**
     * Observes sales transactions created within the specified timestamp range.
     */
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>> {
        return saleDao.getSalesByDateRange(startDate, endDate)
    }

    /**
     * Calculates and observes total sales revenue recorded between startOfDay and endOfDay timestamps.
     */
    fun getTodaysSalesTotal(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return saleDao.getTodaysSalesTotal(startOfDay, endOfDay)
    }

    /**
     * Calculates total profit for sales recorded between startOfDay and endOfDay.
     */
    fun getTodaysProfit(startOfDay: Long, endOfDay: Long): Flow<Double?> {
        return saleDao.getTodaysProfit(startOfDay, endOfDay)
    }

    /**
     * Observes total count of sales currently in 'PENDING' status.
     */
    fun getPendingSalesCount(): Flow<Int> {
        return saleDao.getPendingSalesCount()
    }

    /**
     * Retrieves sales transactions that have not yet been synced to remote cloud backend.
     */
    suspend fun getUnsyncedSales(): List<SaleEntity> {
        return saleDao.getUnsyncedSales()
    }

    // --- SaleItemDao Wrapped Methods ---

    /**
     * Inserts a single sale line item with an updated timestamp.
     */
    suspend fun insertSaleItem(item: SaleItemEntity): Long {
        return saleItemDao.insertSaleItem(item.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Batch inserts a list of sale line items with updated timestamps.
     */
    suspend fun insertSaleItems(items: List<SaleItemEntity>): List<Long> {
        val now = System.currentTimeMillis()
        return saleItemDao.insertSaleItems(items.map { it.copy(updatedAt = now) })
    }

    /**
     * Updates an existing sale line item with an updated timestamp.
     */
    suspend fun updateSaleItem(item: SaleItemEntity) {
        saleItemDao.updateSaleItem(item.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a sale line item from the database.
     */
    suspend fun deleteSaleItem(item: SaleItemEntity) {
        saleItemDao.deleteSaleItem(item)
    }

    /**
     * Observes line items linked to a specific sale ID.
     */
    fun getItemsBySaleId(saleId: Int): Flow<List<SaleItemEntity>> {
        return saleItemDao.getItemsBySaleId(saleId)
    }

    /**
     * Retrieves sale items pending remote cloud synchronization.
     */
    suspend fun getUnsyncedSaleItems(): List<SaleItemEntity> {
        return saleItemDao.getUnsyncedSaleItems()
    }

    // --- Special Workflow Method ---

    /**
     * Completes a checkout sale transaction sequentially:
     * a. Inserts the sale header entity into the database and retrieves the auto-generated sale ID.
     * b. Assigns the auto-generated sale ID to each line item in the cart.
     * c. Batch inserts all line items into the database.
     * d. For each line item, reads the corresponding product from inventory and DECREASES its stock
     *    quantity by the quantity sold in the transaction.
     * e. Updates the sale entity status to "PAID".
     *
     * @param sale The sale header entity containing customer, cashier, and payment details.
     * @param items The line items in the cart for this sale transaction.
     * @return The auto-generated sale ID.
     */
    suspend fun completeSale(sale: SaleEntity, items: List<SaleItemEntity>): Long {
        val now = System.currentTimeMillis()

        // Step a: Insert initial sale header into database to obtain auto-generated primary key ID
        val initialSale = sale.copy(
            updatedAt = now
        )
        val saleIdLong = saleDao.insertSale(initialSale)
        val generatedSaleId = saleIdLong.toInt()

        // Step b: Set each item's saleId to the generated ID and update timestamp
        val itemsWithSaleId = items.map { item ->
            item.copy(
                saleId = generatedSaleId,
                updatedAt = now
            )
        }

        // Step c: Batch insert all cart line items
        saleItemDao.insertSaleItems(itemsWithSaleId)

        // Step d: STOCK DEDUCTION LOGIC
        // Iterate through each item sold in the checkout transaction:
        // 1. Retrieve current product details from ProductDao using productId.
        // 2. Subtract item.quantity from product.stockQuantity to deduct sold quantity from inventory.
        //    (e.g., if current stock is 20.0 and 3.0 items are sold, remaining stock is 17.0).
        // 3. Ensure remaining stock level does not fall below zero (using coerceAtLeast(0.0)).
        // 4. Update the new inventory stock quantity in the database using productDao.updateStockQuantity.
        for (item in itemsWithSaleId) {
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                val newStockQuantity = (product.stockQuantity - item.quantity).coerceAtLeast(0.0)
                productDao.updateStockQuantity(product.id, newStockQuantity)
            }
        }

        // Step e: Update sale status with correct ID and save updated timestamp
        val completedSale = initialSale.copy(
            id = generatedSaleId,
            updatedAt = System.currentTimeMillis()
        )
        saleDao.updateSale(completedSale)

        return saleIdLong
    }
}
