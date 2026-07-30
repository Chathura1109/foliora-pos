package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing product categories.
 * Provides database operations for category creation, retrieval, updates, and sync operations.
 */
@Dao
@JvmSuppressWildcards
interface CategoryDao {

    /**
     * Inserts a category entity into the database.
     * Updates the existing record without deleting it if the ID conflicts.
     */
    @Upsert
    suspend fun insertCategory(category: CategoryEntity): Long

    /**
     * Updates an existing category entity.
     */
    @Update
    suspend fun updateCategory(category: CategoryEntity): Int

    /**
     * Deletes a category entity from the database.
     */
    @Delete
    suspend fun deleteCategory(category: CategoryEntity): Int

    /**
     * Fetches a category by its local primary key ID.
     */
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): CategoryEntity?

    /**
     * Observes all categories sorted alphabetically by name.
     */
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    /**
     * Retrieves all category entities that are not yet synced to the remote database.
     */
    @Query("SELECT * FROM categories WHERE isSynced = 0")
    suspend fun getUnsyncedCategories(): List<CategoryEntity>
}
