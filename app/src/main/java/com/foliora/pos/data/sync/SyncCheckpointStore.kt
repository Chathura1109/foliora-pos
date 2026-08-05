package com.foliora.pos.data.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Stores the last fully successful sync time and builds incremental pull queries. */
@Singleton
class SyncCheckpointStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun queryFor(firestore: FirebaseFirestore, collection: String): Query {
        val lastSuccessfulSync = preferences.getLong(KEY_LAST_SUCCESSFUL_SYNC, 0L)
        if (lastSuccessfulSync == 0L) return firestore.collection(collection)

        val lowerBound = (lastSuccessfulSync - CHECKPOINT_OVERLAP_MILLIS).coerceAtLeast(0L)
        return firestore.collection(collection)
            .whereGreaterThanOrEqualTo(UPDATED_AT_FIELD, lowerBound)
    }

    @Synchronized
    fun markSuccessfulSync(syncStartedAt: Long) {
        val currentCheckpoint = preferences.getLong(KEY_LAST_SUCCESSFUL_SYNC, 0L)
        if (syncStartedAt > currentCheckpoint) {
            preferences.edit().putLong(KEY_LAST_SUCCESSFUL_SYNC, syncStartedAt).commit()
        }
    }

    private companion object {
        private const val PREFERENCES_NAME = "foliora_sync_checkpoints"
        private const val KEY_LAST_SUCCESSFUL_SYNC = "last_successful_sync_started_at"
        private const val UPDATED_AT_FIELD = "updatedAt"
        private const val CHECKPOINT_OVERLAP_MILLIS = 5 * 60 * 1000L
    }
}
