package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.foliora.pos.data.local.entity.SettingEntity

/**
 * Data Access Object (DAO) for application settings and configuration parameters.
 * Manages fetching and updating global application configuration.
 */
@Dao
@JvmSuppressWildcards
interface SettingDao {

    /**
     * Inserts application settings into the database.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity): Long

    /**
     * Updates application settings.
     */
    @Update
    suspend fun updateSetting(setting: SettingEntity): Int

    /**
     * Retrieves the single application settings record if present.
     */
    @Query("SELECT * FROM settings LIMIT 1")
    suspend fun getSettings(): SettingEntity?

    /**
     * Retrieves setting records that are unsynced with remote database.
     */
    @Query("SELECT * FROM settings WHERE isSynced = 0")
    suspend fun getUnsyncedSettings(): List<SettingEntity>
}
