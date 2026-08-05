package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for line items belonging to purchase transactions.
 * Supports batch insertion, individual updates, and retrieval per purchase order.
 */
@Dao
@JvmSuppressWildcards
interface PurchaseItemDao {

    /**
     * Inserts a single purchase line item.
     */
    @Upsert
    suspend fun insertPurchaseItem(item: PurchaseItemEntity): Long

    /**
     * Inserts a list of purchase line items in a single batch operation.
     */
    @Upsert
    suspend fun insertPurchaseItems(items: List<PurchaseItemEntity>): List<Long>

    /**
     * Updates a purchase line item.
     */
    @Update
    suspend fun updatePurchaseItem(item: PurchaseItemEntity): Int

    /**
     * Deletes a purchase line item from the database.
     */
    @Delete
    suspend fun deletePurchaseItem(item: PurchaseItemEntity): Int

    /**
     * Observes all line items associated with a specific purchase ID.
     */
    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId")
    fun getItemsByPurchaseId(purchaseId: Int): Flow<List<PurchaseItemEntity>>

    @Query("SELECT * FROM purchase_items WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getPurchaseItemByFirebaseId(firebaseId: String): PurchaseItemEntity?

    @Query("SELECT * FROM purchase_items WHERE id = :id")
    suspend fun getPurchaseItemById(id: Int): PurchaseItemEntity?

    /**
     * Retrieves purchase item records that need remote synchronization.
     */
    @Query("SELECT * FROM purchase_items WHERE isSynced = 0")
    suspend fun getUnsyncedPurchaseItems(): List<PurchaseItemEntity>
}
