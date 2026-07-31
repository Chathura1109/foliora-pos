package com.foliora.pos.data.repository

import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.PurchaseDao
import com.foliora.pos.data.local.dao.PurchaseItemDao
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing stock purchase transactions and purchase line items in Foliora POS.
 * Combines operations from [PurchaseDao], [PurchaseItemDao], and [ProductDao] to coordinate complex
 * purchase workflows such as stock incrementation upon purchase order completion.
 *
 * @property database Room database used to run multi-DAO workflows atomically.
 * @property purchaseDao Data access object for purchase transaction headers.
 * @property purchaseItemDao Data access object for purchase line items.
 * @property productDao Data access object for product inventory stock updates.
 */
class PurchaseRepository @Inject constructor(
    private val database: FolioraDatabase,
    private val purchaseDao: PurchaseDao,
    private val purchaseItemDao: PurchaseItemDao,
    private val productDao: ProductDao
) {

    // --- PurchaseDao Wrapped Methods ---

    /**
     * Inserts a purchase entity into the database with an updated timestamp.
     */
    suspend fun insertPurchase(purchase: PurchaseEntity): Long {
        return purchaseDao.insertPurchase(purchase.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing purchase record with an updated timestamp.
     */
    suspend fun updatePurchase(purchase: PurchaseEntity) {
        purchaseDao.updatePurchase(purchase.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a purchase record from the database.
     */
    suspend fun deletePurchase(purchase: PurchaseEntity) {
        purchaseDao.deletePurchase(purchase)
    }

    /**
     * Retrieves a purchase header by ID.
     */
    suspend fun getPurchaseById(id: Int): PurchaseEntity? {
        return purchaseDao.getPurchaseById(id)
    }

    /**
     * Observes all purchase orders sorted newest first.
     */
    fun getAllPurchases(): Flow<List<PurchaseEntity>> {
        return purchaseDao.getAllPurchases()
    }

    /**
     * Observes purchases associated with a specific supplier.
     */
    fun getPurchasesBySupplier(supplierId: Int): Flow<List<PurchaseEntity>> {
        return purchaseDao.getPurchasesBySupplier(supplierId)
    }

    /**
     * Observes purchases filtered by status (e.g. 'PENDING', 'COMPLETED').
     */
    fun getPurchasesByStatus(status: String): Flow<List<PurchaseEntity>> {
        return purchaseDao.getPurchasesByStatus(status)
    }

    /**
     * Observes total count of pending purchase orders.
     */
    fun getPendingPurchasesCount(): Flow<Int> {
        return purchaseDao.getPendingPurchasesCount()
    }

    /**
     * Retrieves purchase records pending remote cloud synchronization.
     */
    suspend fun getUnsyncedPurchases(): List<PurchaseEntity> {
        return purchaseDao.getUnsyncedPurchases()
    }

    // --- PurchaseItemDao Wrapped Methods ---

    /**
     * Inserts a single purchase line item with an updated timestamp.
     */
    suspend fun insertPurchaseItem(item: PurchaseItemEntity): Long {
        return purchaseItemDao.insertPurchaseItem(item.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Batch inserts a list of purchase line items with updated timestamps.
     */
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>): List<Long> {
        val now = System.currentTimeMillis()
        return purchaseItemDao.insertPurchaseItems(items.map { it.copy(updatedAt = now) })
    }

    /**
     * Updates an existing purchase line item with an updated timestamp.
     */
    suspend fun updatePurchaseItem(item: PurchaseItemEntity) {
        purchaseItemDao.updatePurchaseItem(item.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a purchase line item from the database.
     */
    suspend fun deletePurchaseItem(item: PurchaseItemEntity) {
        purchaseItemDao.deletePurchaseItem(item)
    }

    /**
     * Observes all line items linked to a specific purchase ID.
     */
    fun getItemsByPurchaseId(purchaseId: Int): Flow<List<PurchaseItemEntity>> {
        return purchaseItemDao.getItemsByPurchaseId(purchaseId)
    }

    /**
     * Retrieves purchase line items pending remote cloud synchronization.
     */
    suspend fun getUnsyncedPurchaseItems(): List<PurchaseItemEntity> {
        return purchaseItemDao.getUnsyncedPurchaseItems()
    }

    // --- Special Workflow Method ---

    /**
     * Completes a purchase transaction sequentially:
     * a. Inserts the purchase header entity and obtains the generated primary key ID.
     * b. Updates each purchase item with the auto-generated purchase ID.
     * c. Batch inserts all line items into the database.
     * d. For each line item, reads the corresponding product and INCREASES its stock quantity
     *    by the item's purchased quantity.
     * e. Updates the purchase entity status to "COMPLETED".
     *
     * @param purchase The purchase entity header containing supplier, cost, and user details.
     * @param items The line items included in this purchase order.
     * @return The auto-generated purchase ID.
     */
    suspend fun completePurchase(
        purchase: PurchaseEntity,
        items: List<PurchaseItemEntity>
    ): Long = database.withTransaction {
        val now = System.currentTimeMillis()

        // Step a: Insert initial purchase header into database to get generated ID
        val initialPurchase = purchase.copy(
            updatedAt = now
        )
        val purchaseIdLong = purchaseDao.insertPurchase(initialPurchase)
        val generatedPurchaseId = purchaseIdLong.toInt()

        // Step b: Associate each line item with the generated purchase ID
        val itemsWithPurchaseId = items.map { item ->
            item.copy(
                purchaseId = generatedPurchaseId,
                updatedAt = now
            )
        }

        // Step c: Batch insert all purchase line items
        purchaseItemDao.insertPurchaseItems(itemsWithPurchaseId)

        // Step d: STOCK INCREMENTATION LOGIC
        // Iterate through each purchase line item to update product inventory.
        // For each item in the order:
        // 1. Fetch the product entity from ProductDao using productId.
        // 2. Add item.quantity to the product's existing stockQuantity.
        //    (e.g., if existing stock is 10.0 and we purchase 5.0, new stock is 15.0).
        // 3. Persist the updated stock level using productDao.updateStockQuantity.
        for (item in itemsWithPurchaseId) {
            val product = productDao.getProductById(item.productId)
            if (product != null) {
                val newStockQuantity = product.stockQuantity + item.quantity
                productDao.updateStockQuantity(product.id, newStockQuantity, now)
            }
        }

        // Step e: Update purchase status to "COMPLETED" and update timestamp
        val completedPurchase = initialPurchase.copy(
            id = generatedPurchaseId,
            status = "COMPLETED",
            updatedAt = System.currentTimeMillis()
        )
        purchaseDao.updatePurchase(completedPurchase)

        purchaseIdLong
    }
}
