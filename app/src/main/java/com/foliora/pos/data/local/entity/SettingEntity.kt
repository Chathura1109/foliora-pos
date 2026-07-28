package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing POS app configuration settings and shop metadata.
 */
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val shopName: String = "Foliora",
    val address: String = "",
    val phoneNumber: String = "",
    val receiptMessage: String = "Thank you for your purchase!",
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
