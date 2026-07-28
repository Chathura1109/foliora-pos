package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foliora.pos.data.local.entity.SaleItemEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for sales line items.
 * Handles individual and batch insertion of cart/sale items and queries line items for sales.
 */
@Dao
@JvmSuppressWildcards
interface SaleItemDao {

    /**
     * Inserts a single sale line item.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItem(item: SaleItemEntity): Long

    /**
     * Inserts a list of sale line items in batch.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleItems(items: List<SaleItemEntity>): List<Long>

    /**
     * Updates an existing sale line item.
     */
    @Update
    suspend fun updateSaleItem(item: SaleItemEntity): Int

    /**
     * Deletes a sale line item from the database.
     */
    @Delete
    suspend fun deleteSaleItem(item: SaleItemEntity): Int

    /**
     * Observes line items linked to a specific sale ID.
     */
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    fun getItemsBySaleId(saleId: Int): Flow<List<SaleItemEntity>>

    /**
     * Retrieves sale items that are pending sync.
     */
    @Query("SELECT * FROM sale_items WHERE isSynced = 0")
    suspend fun getUnsyncedSaleItems(): List<SaleItemEntity>
}
