package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing inventory products in Foliora POS.
 * Handles product creation, inventory querying, stock alerts, search filtering, and stock adjustments via [ProductDao].
 *
 * @property productDao Data access object for product inventory operations.
 */
class ProductRepository @Inject constructor(
    private val productDao: ProductDao
) {

    /**
     * Inserts a product entity into the database with an updated timestamp.
     */
    suspend fun insertProduct(product: ProductEntity): Long {
        return productDao.insertProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing product entity with an updated timestamp.
     */
    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a product entity from the database.
     */
    suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProduct(product)
    }

    /**
     * Retrieves a single product entity by its primary key ID.
     */
    suspend fun getProductById(id: Int): ProductEntity? {
        return productDao.getProductById(id)
    }

    /**
     * Observes all products in the database ordered by name.
     */
    fun getAllProducts(): Flow<List<ProductEntity>> {
        return productDao.getAllProducts()
    }

    /**
     * Observes active products available for sale.
     */
    fun getActiveProducts(): Flow<List<ProductEntity>> {
        return productDao.getActiveProducts()
    }

    /**
     * Observes products belonging to a specific category ID.
     */
    fun getProductsByCategory(categoryId: Int): Flow<List<ProductEntity>> {
        return productDao.getProductsByCategory(categoryId)
    }

    /**
     * Observes active products where stock levels are at or below the low stock threshold.
     */
    fun getLowStockProducts(): Flow<List<ProductEntity>> {
        return productDao.getLowStockProducts()
    }

    /**
     * Searches products matching the given query string.
     */
    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productDao.searchProducts(query)
    }

    /**
     * Directly updates the stock quantity of a specified product.
     */
    suspend fun updateStockQuantity(productId: Int, newQuantity: Double) {
        productDao.updateStockQuantity(productId, newQuantity, System.currentTimeMillis())
    }

    /**
     * Retrieves product records that require remote cloud synchronization.
     */
    suspend fun getUnsyncedProducts(): List<ProductEntity> {
        return productDao.getUnsyncedProducts()
    }
}
