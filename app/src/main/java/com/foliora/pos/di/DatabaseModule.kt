package com.foliora.pos.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
 * HOW HILT / DEPENDENCY INJECTION WORKS:
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

    private val deletionSources = mapOf(
        "users" to "users",
        "categories" to "categories",
        "products" to "products",
        "customers" to "customers",
        "suppliers" to "suppliers",
        "purchases" to "purchases",
        "purchase_items" to "purchase_items",
        "sales" to "sales",
        "sale_items" to "sale_items"
    )

    private fun createDeletionTriggers(database: SupportSQLiteDatabase) {
        deletionSources.forEach { (table, collection) ->
            database.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `queue_${table}_deletion`
                AFTER DELETE ON `$table`
                WHEN OLD.firebaseId IS NOT NULL AND TRIM(OLD.firebaseId) != ''
                BEGIN
                    INSERT OR IGNORE INTO `pending_deletions` (`collection`, `firebaseId`)
                    VALUES ('$collection', OLD.firebaseId);
                END
                """.trimIndent()
            )
        }
    }

    private val migration1To2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_deletions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `collection` TEXT NOT NULL,
                    `firebaseId` TEXT NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                `index_pending_deletions_collection_firebaseId`
                ON `pending_deletions` (`collection`, `firebaseId`)
                """.trimIndent()
            )
            createDeletionTriggers(database)
        }
    }

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE `purchases_backup` AS
                SELECT `id`, `supplierId`, `date`, `totalCost`, `status`, `createdBy`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `purchases`
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE `purchase_items_backup` AS
                SELECT `id`, `purchaseId`, `productId`, `quantity`, `buyingPrice`, `subtotal`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `purchase_items`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `purchase_items`")
            database.execSQL("DROP TABLE `purchases`")
            database.execSQL(
                """
                CREATE TABLE `purchases` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `supplierId` INTEGER NOT NULL,
                    `date` INTEGER NOT NULL,
                    `totalCost` REAL NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdBy` INTEGER NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`supplierId`) REFERENCES `suppliers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`createdBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `purchases`
                SELECT `id`, `supplierId`, `date`, `totalCost`, `status`, `createdBy`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `purchases_backup`
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE `purchase_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `purchaseId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `quantity` REAL NOT NULL,
                    `buyingPrice` REAL NOT NULL,
                    `subtotal` REAL NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `purchase_items`
                SELECT `id`, `purchaseId`, `productId`, `quantity`, `buyingPrice`, `subtotal`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `purchase_items_backup`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `purchase_items_backup`")
            database.execSQL("DROP TABLE `purchases_backup`")
            database.execSQL("CREATE INDEX `index_purchases_supplierId` ON `purchases` (`supplierId`)")
            database.execSQL("CREATE INDEX `index_purchases_createdBy` ON `purchases` (`createdBy`)")
            database.execSQL("CREATE INDEX `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
            database.execSQL("CREATE INDEX `index_purchase_items_productId` ON `purchase_items` (`productId`)")
            createDeletionTriggers(database)
        }
    }

    private val databaseCallback = object : RoomDatabase.Callback() {
        override fun onCreate(database: SupportSQLiteDatabase) {
            super.onCreate(database)
            createDeletionTriggers(database)
        }

        override fun onOpen(database: SupportSQLiteDatabase) {
            super.onOpen(database)
            createDeletionTriggers(database)
        }
    }

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
            .addMigrations(migration1To2, migration3To4)
            .addCallback(databaseCallback)
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

    @Provides
    fun providePendingDeletionDao(database: FolioraDatabase): PendingDeletionDao =
        database.pendingDeletionDao()
}
