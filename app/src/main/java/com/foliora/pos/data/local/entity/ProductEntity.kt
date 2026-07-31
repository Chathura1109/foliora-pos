package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing products in inventory.
 * Foreign key references [CategoryEntity].
 */
@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"])
    ]
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val categoryId: Int = 0,
    val name: String = "",
    val buyingPrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val stockQuantity: Double = 0.0, // Double supports fractional quantities e.g. kg/liters
    val unit: String = "", // kg, pcs, liters, bags, etc.
    val lowStockLimit: Double = 0.0,
    val photoPath: String? = null, // Local URI or file path captured via camera
    val notes: String? = null,
    val isActive: Boolean = true,
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
