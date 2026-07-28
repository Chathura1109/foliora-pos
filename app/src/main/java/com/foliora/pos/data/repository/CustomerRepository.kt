package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.CustomerDao
import com.foliora.pos.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing customer records in Foliora POS.
 * Handles customer profile management, search functionality, and sync tracking via [CustomerDao].
 *
 * @property customerDao Data access object for customer operations.
 */
class CustomerRepository @Inject constructor(
    private val customerDao: CustomerDao
) {

    /**
     * Inserts a customer entity into the database with an updated timestamp.
     */
    suspend fun insertCustomer(customer: CustomerEntity): Long {
        return customerDao.insertCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing customer profile with an updated timestamp.
     */
    suspend fun updateCustomer(customer: CustomerEntity) {
        customerDao.updateCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a customer record from the database.
     */
    suspend fun deleteCustomer(customer: CustomerEntity) {
        customerDao.deleteCustomer(customer)
    }

    /**
     * Retrieves a single customer profile by customer ID.
     */
    suspend fun getCustomerById(id: Int): CustomerEntity? {
        return customerDao.getCustomerById(id)
    }

    /**
     * Observes all registered customers sorted alphabetically by name.
     */
    fun getAllCustomers(): Flow<List<CustomerEntity>> {
        return customerDao.getAllCustomers()
    }

    /**
     * Searches customers matching a query string.
     */
    fun searchCustomers(query: String): Flow<List<CustomerEntity>> {
        return customerDao.searchCustomers(query)
    }

    /**
     * Retrieves customer records with unsynchronized local changes.
     */
    suspend fun getUnsyncedCustomers(): List<CustomerEntity> {
        return customerDao.getUnsyncedCustomers()
    }
}
