package com.foliora.pos.data.repository

import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.SaleDao
import com.foliora.pos.data.local.dao.SaleItemDao
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
    private val productDao: ProductDao
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
                item.quantity
            }.also { totalQuantity ->
                require(totalQuantity.isFinite()) {
                    "Total quantity for product $productId must be a valid number"
                }
            }
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

        val confirmedStockByProduct = confirmAndDeductFirestoreStock(
            productsById = productsById,
            quantitiesByProduct = quantitiesByProduct,
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
                item.copy(
                    saleId = generatedSaleId,
                    updatedAt = now
                )
            }

            // Step c: Batch insert all cart line items
            saleItemDao.insertSaleItems(itemsWithSaleId)

            // Step d: Store the exact stock quantities committed by Firestore.
            for ((productId, confirmedStock) in confirmedStockByProduct) {
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
        updatedAt: Long
    ): Map<Int, Double> {
        val referencesByProductId = productsById.mapValues { (_, product) ->
            firestore.collection(PRODUCTS_COLLECTION).document(requireNotNull(product.firebaseId))
        }

        return try {
            firestore.runTransaction { transaction ->
                // Firestore requires every read to happen before the first write.
                val snapshotsByProductId = referencesByProductId.mapValues { (_, reference) ->
                    transaction.get(reference)
                }

                val confirmedStockByProduct = mutableMapOf<Int, Double>()
                for ((productId, snapshot) in snapshotsByProductId) {
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

                for ((productId, confirmedStock) in confirmedStockByProduct) {
                    transaction.update(
                        referencesByProductId.getValue(productId),
                        mapOf<String, Any>(
                            "stockQuantity" to confirmedStock,
                            "updatedAt" to updatedAt
                        )
                    )
                }

                confirmedStockByProduct.toMap()
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
    }
}
