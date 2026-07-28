package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foliora.pos.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for customer data management.
 * Handles customer profile insertion, updates, search filtering, and offline sync querying.
 */
@Dao
@JvmSuppressWildcards
interface CustomerDao {

    /**
     * Inserts a customer record into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    /**
     * Updates customer details.
     */
    @Update
    suspend fun updateCustomer(customer: CustomerEntity): Int

    /**
     * Deletes a customer record from the database.
     */
    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity): Int

    /**
     * Retrieves a single customer profile by customer ID.
     */
    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    /**
     * Observes all registered customers sorted by name.
     */
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    /**
     * Searches customers matching the given query string against name or phone number.
     */
    @Query("SELECT * FROM customers WHERE name LIKE '%' || :query || '%' OR phoneNumber LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    /**
     * Retrieves customer records that need remote synchronization.
     */
    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>
}
