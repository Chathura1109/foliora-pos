package com.foliora.pos.data.repository

import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.InventoryBatchDao
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.SaleDao
import com.foliora.pos.data.local.dao.SaleItemDao
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/**
 * Repository class for managing point-of-sale transactions and sale cart items in Foliora POS.
 * Integrates [SaleDao], [SaleItemDao], and [ProductDao] to execute checkout workflows, total revenue calculations,
 * and automatic inventory stock deduction.
 *
 * @property database Room database used to run multi-DAO workflows atomically.
 * @property saleDao Data access object for sale transaction headers.
 * @property saleItemDao Data access object for sale line items.
 * @property productDao Data access object for product inventory stock deduction.
 */
class SaleRepository @Inject constructor(
    private val database: FolioraDatabase,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao,
    private val productDao: ProductDao,
    private val inventoryBatchDao: InventoryBatchDao
) {
    private val firestore = FirebaseFirestore.getInstance()

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
     * Completes an online checkout:
     * a. Atomically validates and deducts stock in Firestore.
     * b. Inserts the confirmed sale, items, and stock values into Room.
     *
     * @param sale The sale header entity containing customer, cashier, and payment details.
     * @param items The line items in the cart for this sale transaction.
     * @return The auto-generated sale ID.
     */
    suspend fun completeSale(
        sale: SaleEntity,
        items: List<SaleItemEntity>
    ): Long {
        val now = System.currentTimeMillis()

        require(items.isNotEmpty()) { "A sale must contain at least one item" }
        require(sale.totalAmount.isFinite() && sale.totalAmount >= 0) {
            "Sale total must be a valid number"
        }

        val quantitiesByProduct = items.groupBy { it.productId }.mapValues { (productId, productItems) ->
            productItems.sumOf { item ->
                require(item.quantity.isFinite() && item.quantity > 0) {
                    "Quantity for product $productId must be a valid number greater than zero"
                }
                require(item.sellingPrice.isFinite() && item.sellingPrice >= 0) {
                    "Selling price for product $productId must be a valid number"
                }
                require(item.subtotal.isFinite() && item.subtotal >= 0) {
                    "Subtotal for product $productId must be a valid number"
                }
                require(item.unitCost.isFinite() && item.unitCost >= 0) {
                    "Cost for product $productId must be a valid number"
                }
                item.quantity
            }.also { totalQuantity ->
                require(totalQuantity.isFinite()) {
                    "Total quantity for product $productId must be a valid number"
                }
            }
        }

        val quantitiesByBatch = items.groupBy { item ->
            requireNotNull(item.batchId) { "Select a stock batch for every cart item" }
        }.mapValues { (batchId, batchItems) ->
            batchItems.sumOf { it.quantity }.also { totalQuantity ->
                require(totalQuantity.isFinite() && totalQuantity > 0) {
                    "Quantity for batch $batchId must be valid"
                }
            }
        }

        val batchesById = mutableMapOf<Int, InventoryBatchEntity>()
        for ((batchId, requestedQuantity) in quantitiesByBatch) {
            val batch = requireNotNull(inventoryBatchDao.getBatchById(batchId)) {
                "Selected stock batch $batchId no longer exists"
            }
            require(batch.remainingQuantity.isFinite() && batch.remainingQuantity >= 0) {
                "Stock batch $batchId has an invalid remaining quantity"
            }
            require(requestedQuantity <= batch.remainingQuantity) {
                "Only ${batch.remainingQuantity} units remain in the selected batch"
            }
            require(batch.unitCost.isFinite() && batch.unitCost >= 0) {
                "Selected stock batch has an invalid cost"
            }
            require(batch.sellingPrice.isFinite() && batch.sellingPrice >= 0) {
                "Selected stock batch has an invalid selling price"
            }
            require(!batch.firebaseId.isNullOrBlank() && batch.isSynced) {
                "Selected stock batch is not synced. Sync inventory before checkout"
            }
            val itemProductIds = items.filter { it.batchId == batchId }.map { it.productId }.distinct()
            require(itemProductIds.size == 1 && itemProductIds.single() == batch.productId) {
                "Selected batch does not belong to the cart product"
            }
            batchesById[batchId] = batch
        }

        val productsById = mutableMapOf<Int, ProductEntity>()
        for ((productId, requestedQuantity) in quantitiesByProduct) {
            val product = requireNotNull(productDao.getProductById(productId)) {
                "Product $productId no longer exists"
            }
            require(product.stockQuantity.isFinite() && product.stockQuantity >= 0) {
                "Stock quantity for ${product.name} is invalid"
            }
            require(requestedQuantity <= product.stockQuantity) {
                "Only ${product.stockQuantity} ${product.unit} of ${product.name} is available"
            }
            require(!product.firebaseId.isNullOrBlank()) {
                "${product.name} is not synced to Firebase. Sync products before checkout"
            }
            require(product.isSynced) {
                "${product.name} has pending changes. Sync products before checkout"
            }
            productsById[productId] = product
        }

        val confirmedInventory = confirmAndDeductFirestoreStock(
            productsById = productsById,
            quantitiesByProduct = quantitiesByProduct,
            batchesById = batchesById,
            quantitiesByBatch = quantitiesByBatch,
            updatedAt = now
        )

        return database.withTransaction {
            // Step a: Insert initial sale header into database to obtain auto-generated primary key ID
            val initialSale = sale.copy(
                updatedAt = now
            )
            val saleIdLong = saleDao.insertSale(initialSale)
            val generatedSaleId = saleIdLong.toInt()

            // Step b: Set each item's saleId to the generated ID and update timestamp
            val itemsWithSaleId = items.map { item ->
                val batch = batchesById.getValue(requireNotNull(item.batchId))
                item.copy(
                    saleId = generatedSaleId,
                    unitCost = batch.unitCost,
                    sellingPrice = batch.sellingPrice,
                    subtotal = batch.sellingPrice * item.quantity,
                    updatedAt = now
                )
            }

            // Step c: Batch insert all cart line items
            saleItemDao.insertSaleItems(itemsWithSaleId)

            // Step d: Store the exact stock quantities committed by Firestore.
            for ((batchId, confirmedRemaining) in confirmedInventory.batchRemaining) {
                val batch = requireNotNull(inventoryBatchDao.getBatchById(batchId)) {
                    "Stock batch $batchId no longer exists"
                }
                inventoryBatchDao.updateBatch(
                    batch.copy(
                        remainingQuantity = confirmedRemaining,
                        isSynced = true,
                        updatedAt = now
                    )
                )
            }

            for ((productId, confirmedStock) in confirmedInventory.productStock) {
                val product = requireNotNull(productDao.getProductById(productId)) {
                    "Product $productId no longer exists"
                }
                productDao.updateProduct(
                    product.copy(
                        stockQuantity = confirmedStock,
                        isSynced = true,
                        updatedAt = now
                    )
                )
            }

            // Step e: Update sale status with correct ID and save updated timestamp
            val completedSale = initialSale.copy(
                id = generatedSaleId,
                updatedAt = System.currentTimeMillis()
            )
            saleDao.updateSale(completedSale)

            saleIdLong
        }
    }

    private suspend fun confirmAndDeductFirestoreStock(
        productsById: Map<Int, ProductEntity>,
        quantitiesByProduct: Map<Int, Double>,
        batchesById: Map<Int, InventoryBatchEntity>,
        quantitiesByBatch: Map<Int, Double>,
        updatedAt: Long
    ): ConfirmedInventory {
        val referencesByProductId = productsById.mapValues { (_, product) ->
            firestore.collection(PRODUCTS_COLLECTION).document(requireNotNull(product.firebaseId))
        }
        val referencesByBatchId = batchesById.mapValues { (_, batch) ->
            firestore.collection(BATCHES_COLLECTION).document(requireNotNull(batch.firebaseId))
        }

        return try {
            firestore.runTransaction { transaction ->
                // Firestore requires every read to happen before the first write.
                val productSnapshots = referencesByProductId.mapValues { (_, reference) ->
                    transaction.get(reference)
                }
                val batchSnapshots = referencesByBatchId.mapValues { (_, reference) ->
                    transaction.get(reference)
                }

                val confirmedStockByProduct = mutableMapOf<Int, Double>()
                for ((productId, snapshot) in productSnapshots) {
                    val product = productsById.getValue(productId)
                    require(snapshot.exists()) {
                        "${product.name} does not exist in Firebase. Sync products before checkout"
                    }

                    val cloudStock = (snapshot.get("stockQuantity") as? Number)?.toDouble()
                    require(cloudStock != null && cloudStock.isFinite() && cloudStock >= 0) {
                        "Firebase stock quantity for ${product.name} is invalid"
                    }

                    val requestedQuantity = quantitiesByProduct.getValue(productId)
                    require(requestedQuantity <= cloudStock) {
                        "Only $cloudStock ${product.unit} of ${product.name} is available"
                    }
                    confirmedStockByProduct[productId] = cloudStock - requestedQuantity
                }

                val confirmedRemainingByBatch = mutableMapOf<Int, Double>()
                for ((batchId, snapshot) in batchSnapshots) {
                    val batch = batchesById.getValue(batchId)
                    require(snapshot.exists()) {
                        "Selected stock batch does not exist in Firebase. Sync inventory first"
                    }
                    val cloudRemaining = (snapshot.get("remainingQuantity") as? Number)?.toDouble()
                    require(cloudRemaining != null && cloudRemaining.isFinite() && cloudRemaining >= 0) {
                        "Firebase stock batch quantity is invalid"
                    }
                    val expectedProductFirebaseId = productsById.getValue(batch.productId).firebaseId
                    require(snapshot.getString("productFirebaseId") == expectedProductFirebaseId) {
                        "Firebase stock batch belongs to a different product"
                    }
                    val requestedQuantity = quantitiesByBatch.getValue(batchId)
                    require(requestedQuantity <= cloudRemaining) {
                        "Only $cloudRemaining units remain in the selected stock batch"
                    }
                    confirmedRemainingByBatch[batchId] = cloudRemaining - requestedQuantity
                }

                for ((productId, confirmedStock) in confirmedStockByProduct) {
                    transaction.update(
                        referencesByProductId.getValue(productId),
                        mapOf<String, Any>(
                            "stockQuantity" to confirmedStock,
                            "updatedAt" to updatedAt
                        )
                    )
                }

                for ((batchId, confirmedRemaining) in confirmedRemainingByBatch) {
                    transaction.update(
                        referencesByBatchId.getValue(batchId),
                        mapOf<String, Any>(
                            "remainingQuantity" to confirmedRemaining,
                            "updatedAt" to updatedAt
                        )
                    )
                }

                ConfirmedInventory(
                    productStock = confirmedStockByProduct.toMap(),
                    batchRemaining = confirmedRemainingByBatch.toMap()
                )
            }.await()
        } catch (e: FirebaseFirestoreException) {
            val message = when (e.code) {
                FirebaseFirestoreException.Code.UNAVAILABLE,
                FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Internet connection is required to confirm stock and complete checkout"

                FirebaseFirestoreException.Code.UNAUTHENTICATED,
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "Firebase rejected checkout. Sign in and try again"

                else -> "Could not confirm inventory with Firebase. Please try again"
            }
            throw IllegalStateException(message, e)
        }
    }

    private companion object {
        const val PRODUCTS_COLLECTION = "products"
        const val BATCHES_COLLECTION = "inventory_batches"
    }

    private data class ConfirmedInventory(
        val productStock: Map<Int, Double>,
        val batchRemaining: Map<Int, Double>
    )
}
