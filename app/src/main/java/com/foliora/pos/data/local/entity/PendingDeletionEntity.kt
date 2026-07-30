package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A durable reminder that a local delete still needs to be applied to Firestore.
 */
@Entity(
    tableName = "pending_deletions",
    indices = [
        Index(value = ["collection", "firebaseId"], unique = true)
    ]
)
data class PendingDeletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val collection: String,
    val firebaseId: String
)
