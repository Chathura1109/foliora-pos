package com.foliora.pos.data.repository

import com.foliora.pos.data.local.dao.UserDao
import com.foliora.pos.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Repository class for managing user accounts and authentication data in Foliora POS.
 * Serves as the single source of truth for user domain operations by delegating calls to [UserDao].
 *
 * @property userDao Data access object for user database operations.
 */
class UserRepository @Inject constructor(
    private val userDao: UserDao
) {

    /**
     * Inserts a new user entity into the local database after setting [updatedAt].
     */
    suspend fun insertUser(user: UserEntity): Long {
        return userDao.insertUser(user.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Updates an existing user record with an updated timestamp.
     */
    suspend fun updateUser(user: UserEntity) {
        userDao.updateUser(user.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Deletes a user record from the local database.
     */
    suspend fun deleteUser(user: UserEntity) {
        userDao.deleteUser(user)
    }

    /**
     * Retrieves a user profile by local database primary key ID.
     */
    suspend fun getUserById(id: Int): UserEntity? {
        return userDao.getUserById(id)
    }

    /**
     * Retrieves a user profile by Firebase Authentication UID.
     */
    suspend fun getUserByFirebaseUid(uid: String): UserEntity? {
        return userDao.getUserByFirebaseUid(uid)
    }

    /**
     * Observes all active user accounts reactively sorted by name.
     */
    fun getAllUsers(): Flow<List<UserEntity>> {
        return userDao.getAllUsers()
    }

    /**
     * Retrieves user records that require remote cloud synchronization.
     */
    suspend fun getUnsyncedUsers(): List<UserEntity> {
        return userDao.getUnsyncedUsers()
    }
}
