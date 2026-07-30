package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import com.foliora.pos.data.local.entity.PendingDeletionEntity

@Dao
@JvmSuppressWildcards
interface PendingDeletionDao {

    @Query("SELECT * FROM pending_deletions ORDER BY id ASC")
    suspend fun getAll(): List<PendingDeletionEntity>

    @Query(
        """
        SELECT EXISTS(
            SELECT 1
            FROM pending_deletions
            WHERE collection = :collection AND firebaseId = :firebaseId
        )
        """
    )
    suspend fun exists(collection: String, firebaseId: String): Boolean

    @Delete
    suspend fun delete(deletion: PendingDeletionEntity): Int
}
