package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.InventoryBatchDao
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InventoryBatchRepository @Inject constructor(
    private val inventoryBatchDao: InventoryBatchDao
) {
    fun getAllBatches(): Flow<List<InventoryBatchEntity>> =
        inventoryBatchDao.getAllBatches()

    fun getAvailableBatches(): Flow<List<InventoryBatchEntity>> =
        inventoryBatchDao.getAvailableBatches()

    suspend fun getBatchById(id: Int): InventoryBatchEntity? =
        inventoryBatchDao.getBatchById(id)

    suspend fun getUnsyncedBatches(): List<InventoryBatchEntity> =
        inventoryBatchDao.getUnsyncedBatches()

    fun observePendingSyncCount(): Flow<Int> =
        inventoryBatchDao.observePendingSyncCount()

    suspend fun insertBatch(batch: InventoryBatchEntity): Long =
        inventoryBatchDao.insertBatch(batch.copy(updatedAt = System.currentTimeMillis()))

    suspend fun updateBatch(batch: InventoryBatchEntity) {
        inventoryBatchDao.updateBatch(batch.copy(updatedAt = System.currentTimeMillis()))
    }
}
