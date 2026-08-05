package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stock_adjustments",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = InventoryBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["adjustedBy"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["batchId"]),
        Index(value = ["adjustedBy"])
    ]
)
data class StockAdjustmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val productId: Int = 0,
    val batchId: Int = 0,
    val adjustedBy: Int = 0,
    val adjustmentType: String = "",
    val quantity: Double = 0.0,
    val reason: String = "",
    val notes: String? = null,
    val resultingBatchQuantity: Double = 0.0,
    val resultingProductQuantity: Double = 0.0,
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
