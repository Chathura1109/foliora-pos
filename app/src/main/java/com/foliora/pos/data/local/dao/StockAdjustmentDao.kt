package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.StockAdjustmentEntity

@Dao
@JvmSuppressWildcards
interface StockAdjustmentDao {

    @Upsert
    suspend fun insertAdjustment(adjustment: StockAdjustmentEntity): Long

    @Query("SELECT * FROM stock_adjustments WHERE firebaseId = :firebaseId LIMIT 1")
    suspend fun getByFirebaseId(firebaseId: String): StockAdjustmentEntity?

    @Query("SELECT * FROM stock_adjustments WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsyncedAdjustments(): List<StockAdjustmentEntity>
}
