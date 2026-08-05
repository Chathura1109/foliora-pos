package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.PurchaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for stock purchasing transactions.
 * Manages purchase order headers, status updates, supplier filtering, and pending purchase counts.
 */
@Dao
@JvmSuppressWildcards
interface PurchaseDao {

    /**
     * Inserts a purchase header record.
     */
    @Upsert
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    /**
     * Updates an existing purchase record.
     */
    @Update
    suspend fun updatePurchase(purchase: PurchaseEntity): Int

    /**
     * Deletes a purchase record from the database.
     */
    @Delete
    suspend fun deletePurchase(purchase: PurchaseEntity): Int

    /**
     * Retrieves a purchase record by ID.
     */
    @Query("SELECT * FROM purchases WHERE id = :id")
    suspend fun getPurchaseById(id: Int): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getPurchaseByFirebaseId(firebaseId: String): PurchaseEntity?

    /**
     * Observes all purchase transactions ordered by creation timestamp descending.
     */
    @Query("SELECT * FROM purchases ORDER BY createdAt DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    /**
     * Observes purchases associated with a specific supplier.
     */
    @Query("SELECT * FROM purchases WHERE supplierId = :supplierId ORDER BY createdAt DESC")
    fun getPurchasesBySupplier(supplierId: Int): Flow<List<PurchaseEntity>>

    /**
     * Observes purchases filtered by status (e.g., 'PENDING', 'COMPLETED', 'CANCELLED').
     */
    @Query("SELECT * FROM purchases WHERE status = :status ORDER BY createdAt DESC")
    fun getPurchasesByStatus(status: String): Flow<List<PurchaseEntity>>

    /**
     * Observes the total count of pending purchase orders.
     */
    @Query("SELECT COUNT(*) FROM purchases WHERE status = 'PENDING'")
    fun getPendingPurchasesCount(): Flow<Int>

    /**
     * Retrieves purchase records pending remote cloud synchronization.
     */
    @Query("SELECT * FROM purchases WHERE isSynced = 0")
    suspend fun getUnsyncedPurchases(): List<PurchaseEntity>
}
