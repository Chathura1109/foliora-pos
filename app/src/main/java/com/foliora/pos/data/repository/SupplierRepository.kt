package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.SupplierDao
import com.foliora.pos.data.local.entity.SupplierEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing suppliers and vendors in Foliora POS.
 * Encapsulates operations for vendor management, search, and sync tracking via [SupplierDao].
 *
 * @property supplierDao Data access object for supplier operations.
 */
class SupplierRepository @Inject constructor(
    private val supplierDao: SupplierDao
) {

    /**
     * Inserts a new supplier entity into the database with an updated timestamp.
     */
    suspend fun insertSupplier(supplier: SupplierEntity): Long {
        return supplierDao.insertSupplier(supplier.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing supplier record with an updated timestamp.
     */
    suspend fun updateSupplier(supplier: SupplierEntity) {
        supplierDao.updateSupplier(supplier.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a supplier entity from the database.
     */
    suspend fun deleteSupplier(supplier: SupplierEntity) {
        supplierDao.deleteSupplier(supplier)
    }

    /**
     * Retrieves a supplier by unique supplier ID.
     */
    suspend fun getSupplierById(id: Int): SupplierEntity? {
        return supplierDao.getSupplierById(id)
    }

    /**
     * Observes all suppliers ordered alphabetically by name.
     */
    fun getAllSuppliers(): Flow<List<SupplierEntity>> {
        return supplierDao.getAllSuppliers()
    }

    /**
     * Searches suppliers matching a query string.
     */
    fun searchSuppliers(query: String): Flow<List<SupplierEntity>> {
        return supplierDao.searchSuppliers(query)
    }

    /**
     * Retrieves supplier records that have pending unsynced changes.
     */
    suspend fun getUnsyncedSuppliers(): List<SupplierEntity> {
        return supplierDao.getUnsyncedSuppliers()
    }
}
