package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.CategoryDao
import com.foliora.pos.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing product categories in Foliora POS.
 * Encapsulates data access and provides category management methods by delegating to [CategoryDao].
 *
 * @property categoryDao Data access object for category operations.
 */
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao
) {

    /**
     * Inserts a category entity into the local database with an updated timestamp.
     */
    suspend fun insertCategory(category: CategoryEntity): Long {
        return categoryDao.insertCategory(category.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing category entity with an updated timestamp.
     */
    suspend fun updateCategory(category: CategoryEntity) {
        categoryDao.updateCategory(category.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a category entity from the database.
     */
    suspend fun deleteCategory(category: CategoryEntity) {
        val productCount = categoryDao.getProductCount(category.id)
        require(productCount == 0) {
            "Move or delete the $productCount product(s) in ${category.name} before deleting this category"
        }
        categoryDao.deleteCategory(category)
    }

    /**
     * Retrieves a category by its unique primary key ID.
     */
    suspend fun getCategoryById(id: Int): CategoryEntity? {
        return categoryDao.getCategoryById(id)
    }

    /**
     * Observes all categories ordered alphabetically by name.
     */
    fun getAllCategories(): Flow<List<CategoryEntity>> {
        return categoryDao.getAllCategories()
    }

    /**
     * Retrieves category records that have pending unsynced changes.
     */
    suspend fun getUnsyncedCategories(): List<CategoryEntity> {
        return categoryDao.getUnsyncedCategories()
    }
}
