package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foliora.pos.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing product inventory items.
 * Provides query methods for retrieving active products, low-stock alerts, search filtering, and stock adjustments.
 */
@Dao
@JvmSuppressWildcards
interface ProductDao {

    /**
     * Inserts a product entity into the database.
     * Replaces the row on conflict.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity): Long

    /**
     * Updates an existing product entity.
     */
    @Update
    suspend fun updateProduct(product: ProductEntity): Int

    /**
     * Deletes a product entity from the database.
     */
    @Delete
    suspend fun deleteProduct(product: ProductEntity): Int

    /**
     * Fetches a single product by its primary key ID.
     */
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getProductById(id: Int): ProductEntity?

    /**
     * Observes all products in the system.
     */
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductEntity>>

    /**
     * Observes only active products available for sale or inventory selection.
     */
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ProductEntity>>

    /**
     * Observes products belonging to a specific category.
     */
    @Query("SELECT * FROM products WHERE categoryId = :categoryId ORDER BY name ASC")
    fun getProductsByCategory(categoryId: Int): Flow<List<ProductEntity>>

    /**
     * Observes active products where current stock level is less than or equal to low stock limit.
     */
    @Query("SELECT * FROM products WHERE stockQuantity <= lowStockLimit AND isActive = 1 ORDER BY name ASC")
    fun getLowStockProducts(): Flow<List<ProductEntity>>

    /**
     * Searches active or all products by matching name substring (case-insensitive in SQLite standard LIKE).
     */
    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchProducts(query: String): Flow<List<ProductEntity>>

    /**
     * Directly updates the stock quantity of a specified product.
     */
    @Query("UPDATE products SET stockQuantity = :newQuantity WHERE id = :productId")
    suspend fun updateStockQuantity(productId: Int, newQuantity: Double): Int

    /**
     * Retrieves all product records that have pending offline changes requiring sync.
     */
    @Query("SELECT * FROM products WHERE isSynced = 0")
    suspend fun getUnsyncedProducts(): List<ProductEntity>
}
