package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface InventoryBatchDao {

    @Upsert
    suspend fun insertBatch(batch: InventoryBatchEntity): Long

    @Update
    suspend fun updateBatch(batch: InventoryBatchEntity): Int

    @Query("SELECT * FROM inventory_batches WHERE id = :id")
    suspend fun getBatchById(id: Int): InventoryBatchEntity?

    @Query("SELECT * FROM inventory_batches WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getBatchByFirebaseId(firebaseId: String): InventoryBatchEntity?

    @Query(
        "SELECT * FROM inventory_batches " +
            "WHERE productId = :productId AND unitCostCents = :unitCostCents " +
            "AND sellingPriceCents = :sellingPriceCents " +
            "ORDER BY receivedAt DESC, id DESC LIMIT 1"
    )
    suspend fun findBatchByPrice(
        productId: Int,
        unitCostCents: Long,
        sellingPriceCents: Long
    ): InventoryBatchEntity?

    @Query("SELECT * FROM inventory_batches ORDER BY receivedAt DESC, id DESC")
    fun getAllBatches(): Flow<List<InventoryBatchEntity>>

    @Query(
        "SELECT * FROM inventory_batches " +
            "WHERE remainingQuantity > 0 AND isSynced = 1 ORDER BY receivedAt DESC, id DESC"
    )
    fun getAvailableBatches(): Flow<List<InventoryBatchEntity>>

    @Query(
        "SELECT * FROM inventory_batches " +
            "WHERE productId = :productId AND remainingQuantity > 0 AND isSynced = 1 " +
            "ORDER BY receivedAt DESC, id DESC"
    )
    suspend fun getAvailableBatchesForProduct(productId: Int): List<InventoryBatchEntity>

    @Query(
        "SELECT * FROM inventory_batches WHERE productId = :productId " +
            "ORDER BY receivedAt DESC, id DESC"
    )
    suspend fun getBatchesForProduct(productId: Int): List<InventoryBatchEntity>

    @Query(
        "SELECT COALESCE(SUM(remainingQuantity), 0.0) FROM inventory_batches " +
            "WHERE productId = :productId"
    )
    suspend fun getTotalRemainingForProduct(productId: Int): Double

    @Query("SELECT * FROM inventory_batches WHERE isSynced = 0")
    suspend fun getUnsyncedBatches(): List<InventoryBatchEntity>

    @Query(
        "SELECT COUNT(*) FROM inventory_batches " +
            "WHERE isSynced = 0 OR firebaseId IS NULL OR TRIM(firebaseId) = ''"
    )
    fun observePendingSyncCount(): Flow<Int>
}
