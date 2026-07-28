package com.foliora.pos.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.foliora.pos.data.local.entity.CategoryEntity
import com.foliora.pos.data.local.entity.ProductEntity

/**
 * Represents a Category together with all its Products.
 *
 * Useful for displaying a category with its product count,
 * or for expanding a category to show its products.
 */
data class CategoryWithProducts(
    @Embedded val category: CategoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId"
    )
    val products: List<ProductEntity>
)
