package com.foliora.pos.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.foliora.pos.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for managing user accounts and authentication data.
 * Provides database operations for inserting, updating, deleting, and querying users.
 */
@Dao
@JvmSuppressWildcards
interface UserDao {

    /**
     * Inserts a user entity into the database.
     * Updates the existing row without deleting it if a conflict occurs.
     */
    @Upsert
    suspend fun insertUser(user: UserEntity): Long

    /**
     * Updates an existing user record.
     */
    @Update
    suspend fun updateUser(user: UserEntity): Int

    /**
     * Deletes a user record from the database.
     */
    @Delete
    suspend fun deleteUser(user: UserEntity): Int

    /**
     * Retrieves a user by their local database ID.
     */
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Int): UserEntity?

    /**
     * Retrieves a user by their Firebase Unique Identifier (UID).
     */
    @Query("SELECT * FROM users WHERE firebaseId = :uid OR firebaseAuthUid = :uid")
    suspend fun getUserByFirebaseUid(uid: String): UserEntity?

    /**
     * Observes all users in the system reactively ordered by name.
     */
    @Query("SELECT * FROM users ORDER BY name ASC")
    fun getAllUsers(): Flow<List<UserEntity>>

    /**
     * Retrieves all user records that have not been synced with Firebase remote database.
     */
    @Query("SELECT * FROM users WHERE isSynced = 0")
    suspend fun getUnsyncedUsers(): List<UserEntity>
}
