package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing sales transactions processed in the POS system.
 * Foreign keys reference optional [CustomerEntity] (for walk-in sales) and [UserEntity] (cashier).
 */
@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = CustomerEntity::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["cashierId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["cashierId"])
    ]
)
data class SaleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val customerId: Int? = null, // Nullable to support anonymous/walk-in customer sales
    val cashierId: Int = 0, // ID of user who completed the sale
    val date: Long = 0, // Epoch millis timestamp
    val totalAmount: Double = 0.0,
    val paymentMethod: String = "", // CASH, CARD, or BANK
    val status: String = "", // PAID or PENDING
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
