package com.foliora.pos.data.sync

import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/** Allows only one foreground or WorkManager synchronization to run at a time. */
@Singleton
class SyncExecutionGuard @Inject constructor() {
    private val mutex = Mutex()

    val isRunning: Boolean
        get() = mutex.isLocked

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
