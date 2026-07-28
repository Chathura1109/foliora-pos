package com.foliora.pos

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.foliora.pos.data.sync.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The Application class is the very first thing Android creates when your app starts.
 *
 * @HiltAndroidApp is REQUIRED for Hilt to work — it triggers Hilt's code generation
 * at compile time. Without this annotation, none of the dependency injection in the
 * app will function.
 *
 * Think of this as the "ignition key" for the entire DI system.
 */
@HiltAndroidApp
class FolioraApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Schedule the background sync to run periodically
        SyncManager.schedulePeriodicSync(this)
    }
}
