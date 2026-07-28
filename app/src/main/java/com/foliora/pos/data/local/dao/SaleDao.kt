package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foliora.pos.data.local.entity.SaleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for POS sale transactions and invoices.
 * Provides operations for querying sales by customer, status, date ranges, daily revenue totals, and pending counts.
 */
@Dao
@JvmSuppressWildcards
interface SaleDao {

    /**
     * Inserts a sale record into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: SaleEntity): Long

    /**
     * Updates an existing sale record.
     */
    @Update
    suspend fun updateSale(sale: SaleEntity): Int

    /**
     * Deletes a sale record from the database.
     */
    @Delete
    suspend fun deleteSale(sale: SaleEntity): Int

    /**
     * Retrieves a single sale record by unique ID.
     */
    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun getSaleById(id: Int): SaleEntity?

    /**
     * Observes all sales transactions ordered newest first.
     */
    @Query("SELECT * FROM sales ORDER BY createdAt DESC")
    fun getAllSales(): Flow<List<SaleEntity>>

    /**
     * Observes all sales for a specific customer ID.
     */
    @Query("SELECT * FROM sales WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getSalesByCustomer(customerId: Int): Flow<List<SaleEntity>>

    /**
     * Observes sales matching a specific status (e.g. 'COMPLETED', 'PENDING', 'CANCELLED').
     */
    @Query("SELECT * FROM sales WHERE status = :status ORDER BY createdAt DESC")
    fun getSalesByStatus(status: String): Flow<List<SaleEntity>>

    /**
     * Observes sales transactions created within the specified timestamp range.
     */
    @Query("SELECT * FROM sales WHERE createdAt BETWEEN :startDate AND :endDate ORDER BY createdAt DESC")
    fun getSalesByDateRange(startDate: Long, endDate: Long): Flow<List<SaleEntity>>

    /**
     * Calculates and observes total sale amounts recorded between startOfDay and endOfDay.
     */
    @Query("SELECT SUM(totalAmount) FROM sales WHERE createdAt BETWEEN :startOfDay AND :endOfDay")
    fun getTodaysSalesTotal(startOfDay: Long, endOfDay: Long): Flow<Double?>
    
    /**
     * Calculates and observes total profit for sales recorded between startOfDay and endOfDay.
     */
    @Query("SELECT SUM((si.sellingPrice - p.buyingPrice) * si.quantity) FROM sales s INNER JOIN sale_items si ON s.id = si.saleId INNER JOIN products p ON si.productId = p.id WHERE s.createdAt BETWEEN :startOfDay AND :endOfDay")
    fun getTodaysProfit(startOfDay: Long, endOfDay: Long): Flow<Double?>

    /**
     * Observes total count of sales currently in 'PENDING' status.
     */
    @Query("SELECT COUNT(*) FROM sales WHERE status = 'PENDING'")
    fun getPendingSalesCount(): Flow<Int>

    /**
     * Retrieves sales transactions that have not yet been synced to remote backend.
     */
    @Query("SELECT * FROM sales WHERE isSynced = 0")
    suspend fun getUnsyncedSales(): List<SaleEntity>
}
