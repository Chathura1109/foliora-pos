package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for supplier and vendor management.
 * Provides operations to create, update, delete, search, and list suppliers.
 */
@Dao
@JvmSuppressWildcards
interface SupplierDao {

    /**
     * Inserts a supplier entity into the database.
     */
    @Upsert
    suspend fun insertSupplier(supplier: SupplierEntity): Long

    /**
     * Updates an existing supplier record.
     */
    @Update
    suspend fun updateSupplier(supplier: SupplierEntity): Int

    /**
     * Deletes a supplier from the database.
     */
    @Delete
    suspend fun deleteSupplier(supplier: SupplierEntity): Int

    /**
     * Retrieves a single supplier by unique supplier ID.
     */
    @Query("SELECT * FROM suppliers WHERE id = :id")
    suspend fun getSupplierById(id: Int): SupplierEntity?

    /**
     * Observes all suppliers in the database ordered alphabetically by name.
     */
    @Query("SELECT * FROM suppliers ORDER BY name ASC")
    fun getAllSuppliers(): Flow<List<SupplierEntity>>

    /**
     * Searches suppliers matching a query string in name or phone number fields.
     */
    @Query("SELECT * FROM suppliers WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchSuppliers(query: String): Flow<List<SupplierEntity>>

    /**
     * Retrieves suppliers that have unsynchronized local changes.
     */
    @Query("SELECT * FROM suppliers WHERE isSynced = 0")
    suspend fun getUnsyncedSuppliers(): List<SupplierEntity>
}
