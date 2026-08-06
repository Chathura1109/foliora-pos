package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.SettingDao
import com.foliora.pos.data.local.entity.SettingEntity
import javax.inject.Inject

/**
 * Repository class for managing application settings and shop configuration in Foliora POS.
 * Delegated calls to [SettingDao] and provides initialization of default shop settings.
 *
 * @property settingDao Data access object for setting entity operations.
 */
class SettingRepository @Inject constructor(
    private val settingDao: SettingDao
) {

    /**
     * Inserts application settings into the database after updating timestamp.
     */
    suspend fun insertSetting(setting: SettingEntity): Long {
        return settingDao.insertSetting(setting.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates application settings after updating timestamp.
     */
    suspend fun updateSetting(setting: SettingEntity) {
        settingDao.updateSetting(setting.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Retrieves the single application settings entity if present.
     */
    suspend fun getSettings(): SettingEntity? {
        return settingDao.getSettings()
    }

    /**
     * Retrieves settings entities pending remote cloud synchronization.
     */
    suspend fun getUnsyncedSettings(): List<SettingEntity> {
        return settingDao.getUnsyncedSettings()
    }

    /**
     * Checks if settings exist in the local database, and if not, inserts default settings entity.
     *
     * @return Existing or newly initialized [SettingEntity].
     */
    suspend fun initializeSettings(): SettingEntity {
        val existingSettings = settingDao.getSettings()
        return if (existingSettings != null) {
            existingSettings
        } else {
            val defaultSettings = SettingEntity(
                isSynced = true,
                firebaseId = SETTINGS_DOCUMENT_ID
            )
            val id = settingDao.insertSetting(defaultSettings)
            defaultSettings.copy(id = id.toInt())
        }
    }

    private companion object {
        private const val SETTINGS_DOCUMENT_ID = "shop"
    }
}
