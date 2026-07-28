package com.foliora.pos.di

import android.content.Context
import androidx.room.Room
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt Dependency Injection module for the database layer.
 *
 * HOW HILT / DEPENDENCY INJECTION WORKS (for your viva):
 * Instead of creating objects yourself with "val db = Room.databaseBuilder(...).build()",
 * Hilt does it for you. You tell Hilt HOW to create things (in this module), and then
 * anywhere else in the app you just say "I need a ProductDao" and Hilt hands you one.
 *
 * Why bother? Because:
 *   1. The database should only be created ONCE (Singleton) — Hilt guarantees this.
 *   2. Your ViewModels and Repositories don't need to know HOW the database is built.
 *   3. It makes testing easier — you can swap in a fake database for tests.
 *
 * @Module tells Hilt "this class contains instructions for creating things."
 * @InstallIn(SingletonComponent) means these objects live for the entire app lifetime.
 * @Provides on a function means "here's how to create this type."
 * @Singleton means "only create one instance, reuse it everywhere."
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Creates the single Room database instance for the whole app.
     * "foliora_database" is the filename for the SQLite database on disk.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): FolioraDatabase {
        return Room.databaseBuilder(
            context,
            FolioraDatabase::class.java,
            "foliora_database"
        )
            // fallbackToDestructiveMigration() means: if the schema changes and there's
            // no migration defined, wipe the database and start fresh. Fine for development,
            // but in production you'd write proper migrations to preserve user data.
            .fallbackToDestructiveMigration()
            .build()
    }

    // --- Each function below extracts one DAO from the database instance ---
    // Hilt calls provideDatabase() first (since it's a dependency), then passes
    // the result to these functions to get the individual DAOs.

    @Provides
    fun provideUserDao(database: FolioraDatabase): UserDao = database.userDao()

    @Provides
    fun provideCategoryDao(database: FolioraDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideProductDao(database: FolioraDatabase): ProductDao = database.productDao()

    @Provides
    fun provideCustomerDao(database: FolioraDatabase): CustomerDao = database.customerDao()

    @Provides
    fun provideSupplierDao(database: FolioraDatabase): SupplierDao = database.supplierDao()

    @Provides
    fun providePurchaseDao(database: FolioraDatabase): PurchaseDao = database.purchaseDao()

    @Provides
    fun providePurchaseItemDao(database: FolioraDatabase): PurchaseItemDao = database.purchaseItemDao()

    @Provides
    fun provideSaleDao(database: FolioraDatabase): SaleDao = database.saleDao()

    @Provides
    fun provideSaleItemDao(database: FolioraDatabase): SaleItemDao = database.saleItemDao()

    @Provides
    fun provideSettingDao(database: FolioraDatabase): SettingDao = database.settingDao()
}
