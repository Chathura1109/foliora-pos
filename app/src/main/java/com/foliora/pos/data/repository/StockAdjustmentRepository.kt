package com.foliora.pos.data.repository

import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.InventoryBatchDao
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.StockAdjustmentDao
import com.foliora.pos.data.local.entity.StockAdjustmentEntity
import javax.inject.Inject

class StockAdjustmentRepository @Inject constructor(
    private val database: FolioraDatabase,
    private val productDao: ProductDao,
    private val inventoryBatchDao: InventoryBatchDao,
    private val stockAdjustmentDao: StockAdjustmentDao
) {
    suspend fun adjustBatchStock(
        productId: Int,
        batchId: Int,
        adjustedBy: Int,
        adjustmentType: String,
        quantity: Double,
        reason: String,
        notes: String?
    ) {
        require(quantity.isFinite() && quantity > 0) { "Adjustment quantity must be greater than zero" }
        require(adjustmentType == TYPE_INCREASE || adjustmentType == TYPE_DECREASE) {
            "Invalid stock adjustment type"
        }
        require(reason.isNotBlank()) { "Please enter an adjustment reason" }

        database.withTransaction {
            val product = requireNotNull(productDao.getProductById(productId)) {
                "Product no longer exists"
            }
            val batch = requireNotNull(inventoryBatchDao.getBatchById(batchId)) {
                "Stock batch no longer exists"
            }
            require(batch.productId == product.id) { "Selected batch does not belong to this product" }
            require(batch.remainingQuantity.isFinite() && batch.remainingQuantity >= 0) {
                "Current batch quantity is invalid"
            }

            val signedQuantity = if (adjustmentType == TYPE_INCREASE) quantity else -quantity
            val updatedBatchQuantity = batch.remainingQuantity + signedQuantity
            require(updatedBatchQuantity >= 0) {
                "Only ${batch.remainingQuantity} ${product.unit} is available in this batch"
            }

            val now = System.currentTimeMillis()
            inventoryBatchDao.updateBatch(
                batch.copy(
                    remainingQuantity = updatedBatchQuantity,
                    isSynced = false,
                    updatedAt = now
                )
            )

            val updatedProductQuantity = inventoryBatchDao.getTotalRemainingForProduct(product.id)
            productDao.updateStockQuantity(product.id, updatedProductQuantity, now)

            stockAdjustmentDao.insertAdjustment(
                StockAdjustmentEntity(
                    productId = product.id,
                    batchId = batch.id,
                    adjustedBy = adjustedBy,
                    adjustmentType = adjustmentType,
                    quantity = quantity,
                    reason = reason.trim(),
                    notes = notes?.trim()?.ifBlank { null },
                    resultingBatchQuantity = updatedBatchQuantity,
                    resultingProductQuantity = updatedProductQuantity,
                    isSynced = false,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    suspend fun insertAdjustment(adjustment: StockAdjustmentEntity): Long =
        stockAdjustmentDao.insertAdjustment(adjustment.copy(updatedAt = System.currentTimeMillis()))

    suspend fun getUnsyncedAdjustments(): List<StockAdjustmentEntity> =
        stockAdjustmentDao.getUnsyncedAdjustments()

    companion object {
        const val TYPE_INCREASE = "INCREASE"
        const val TYPE_DECREASE = "DECREASE"
    }
}
