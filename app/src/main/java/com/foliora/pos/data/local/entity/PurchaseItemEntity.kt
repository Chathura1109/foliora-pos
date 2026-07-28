package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing individual line items within a purchase order.
 * Foreign keys reference [PurchaseEntity] and [ProductEntity].
 */
@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseId"]),
        Index(value = ["productId"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val purchaseId: Int = 0,
    val productId: Int = 0,
    val quantity: Double = 0.0,
    val buyingPrice: Double = 0.0,
    val subtotal: Double = 0.0, // Calculated as quantity * buyingPrice
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
