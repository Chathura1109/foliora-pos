package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing suppliers/vendors in the Foliora POS system.
 * Includes optional GPS coordinates for vendor location tracking.
 */
@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val notes: String? = null,
    val latitude: Double? = null, // GPS coordinate latitude
    val longitude: Double? = null, // GPS coordinate longitude
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
