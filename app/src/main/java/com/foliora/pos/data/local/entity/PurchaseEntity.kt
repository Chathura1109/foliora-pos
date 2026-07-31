package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing purchase transactions made to suppliers.
 * Foreign keys reference [SupplierEntity] and [UserEntity].
 */
@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = SupplierEntity::class,
            parentColumns = ["id"],
            childColumns = ["supplierId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["createdBy"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["supplierId"]),
        Index(value = ["createdBy"])
    ]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val supplierId: Int = 0,
    val date: Long = 0, // Epoch millis timestamp
    val totalCost: Double = 0.0,
    val status: String = "", // COMPLETED or PENDING
    val createdBy: Int = 0, // User ID of creator/cashier/owner
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
