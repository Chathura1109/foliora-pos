package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing user accounts in the Foliora POS system.
 * Handles roles such as OWNER or CASHIER and links to Firebase Authentication.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String = "",
    val role: String = "", // OWNER or CASHIER (validated in domain/UI code)
    val firebaseAuthUid: String = "", // Unique UID from Firebase Auth (not a password)
    val isActive: Boolean = true,
    
    // Sync tracking fields required across all Room entities
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
