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
 * Because:
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
        "inventory_batches" to "inventory_batches",
        "stock_adjustments" to "stock_adjustments",
        "sales" to "sales",
        "sale_items" to "sale_items"
    )

    private fun createDeletionTriggers(database: SupportSQLiteDatabase) {
        deletionSources.forEach { (table, collection) ->
            val tableExists = database.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                arrayOf(table)
            ).use { cursor -> cursor.moveToFirst() }
            if (!tableExists) return@forEach

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

    private val migration2To3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE `products_backup` AS
                SELECT `id`, `categoryId`, `name`, `buyingPrice`, `sellingPrice`,
                       `stockQuantity`, `unit`, `lowStockLimit`, `photoPath`, `notes`,
                       `isActive`, `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `products`
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE `sale_items_backup` AS
                SELECT `id`, `saleId`, `productId`, `quantity`, `sellingPrice`, `subtotal`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `sale_items`
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
            database.execSQL("DROP TABLE `sale_items`")
            database.execSQL("DROP TABLE `purchase_items`")
            database.execSQL("DROP TABLE `products`")
            database.execSQL(
                """
                CREATE TABLE `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `buyingPrice` REAL NOT NULL,
                    `sellingPrice` REAL NOT NULL,
                    `stockQuantity` REAL NOT NULL,
                    `unit` TEXT NOT NULL,
                    `lowStockLimit` REAL NOT NULL,
                    `photoPath` TEXT,
                    `notes` TEXT,
                    `isActive` INTEGER NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `products`
                SELECT `id`, `categoryId`, `name`, `buyingPrice`, `sellingPrice`,
                       `stockQuantity`, `unit`, `lowStockLimit`, `photoPath`, `notes`,
                       `isActive`, `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `products_backup`
                """.trimIndent()
            )
            database.execSQL(
                """
                CREATE TABLE `sale_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `saleId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `quantity` REAL NOT NULL,
                    `sellingPrice` REAL NOT NULL,
                    `subtotal` REAL NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `sale_items`
                SELECT `id`, `saleId`, `productId`, `quantity`, `sellingPrice`, `subtotal`,
                       `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                FROM `sale_items_backup`
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
            database.execSQL("DROP TABLE `products_backup`")
            database.execSQL("DROP TABLE `sale_items_backup`")
            database.execSQL("DROP TABLE `purchase_items_backup`")
            database.execSQL("CREATE INDEX `index_products_categoryId` ON `products` (`categoryId`)")
            database.execSQL("CREATE INDEX `index_sale_items_saleId` ON `sale_items` (`saleId`)")
            database.execSQL("CREATE INDEX `index_sale_items_productId` ON `sale_items` (`productId`)")
            database.execSQL("CREATE INDEX `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
            database.execSQL("CREATE INDEX `index_purchase_items_productId` ON `purchase_items` (`productId`)")
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

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inventory_batches` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `purchaseItemId` INTEGER,
                    `originalQuantity` REAL NOT NULL,
                    `remainingQuantity` REAL NOT NULL,
                    `unitCost` REAL NOT NULL,
                    `receivedAt` INTEGER NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`purchaseItemId`) REFERENCES `purchase_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_batches_productId` " +
                    "ON `inventory_batches` (`productId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_batches_purchaseItemId` " +
                    "ON `inventory_batches` (`purchaseItemId`)"
            )
            database.execSQL(
                """
                INSERT INTO `inventory_batches` (
                    `productId`, `purchaseItemId`, `originalQuantity`, `remainingQuantity`,
                    `unitCost`, `receivedAt`, `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                )
                SELECT
                    `id`, NULL, `stockQuantity`, `stockQuantity`, `buyingPrice`, `createdAt`, 0,
                    CASE
                        WHEN `firebaseId` IS NOT NULL AND TRIM(`firebaseId`) != ''
                        THEN 'opening_' || `firebaseId`
                        ELSE NULL
                    END,
                    `createdAt`, `updatedAt`
                FROM `products`
                WHERE `stockQuantity` > 0
                """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE `sale_items_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `saleId` INTEGER NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `batchId` INTEGER,
                    `quantity` REAL NOT NULL,
                    `sellingPrice` REAL NOT NULL,
                    `unitCost` REAL NOT NULL,
                    `subtotal` REAL NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`saleId`) REFERENCES `sales`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`batchId`) REFERENCES `inventory_batches`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                INSERT INTO `sale_items_new` (
                    `id`, `saleId`, `productId`, `batchId`, `quantity`, `sellingPrice`,
                    `unitCost`, `subtotal`, `isSynced`, `firebaseId`, `createdAt`, `updatedAt`
                )
                SELECT
                    si.`id`, si.`saleId`, si.`productId`, NULL, si.`quantity`, si.`sellingPrice`,
                    p.`buyingPrice`, si.`subtotal`, si.`isSynced`, si.`firebaseId`,
                    si.`createdAt`, si.`updatedAt`
                FROM `sale_items` si
                INNER JOIN `products` p ON p.`id` = si.`productId`
                """.trimIndent()
            )
            database.execSQL("DROP TABLE `sale_items`")
            database.execSQL("ALTER TABLE `sale_items_new` RENAME TO `sale_items`")
            database.execSQL("CREATE INDEX `index_sale_items_saleId` ON `sale_items` (`saleId`)")
            database.execSQL("CREATE INDEX `index_sale_items_productId` ON `sale_items` (`productId`)")
            database.execSQL("CREATE INDEX `index_sale_items_batchId` ON `sale_items` (`batchId`)")
            createDeletionTriggers(database)
        }
    }

    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `purchase_items` " +
                    "ADD COLUMN `sellingPrice` REAL NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                UPDATE `purchase_items`
                SET `sellingPrice` = COALESCE(
                    (SELECT p.`sellingPrice` FROM `products` p
                     WHERE p.`id` = `purchase_items`.`productId`),
                    0
                )
                """.trimIndent()
            )
            database.execSQL(
                "ALTER TABLE `inventory_batches` " +
                    "ADD COLUMN `sellingPrice` REAL NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                UPDATE `inventory_batches`
                SET `sellingPrice` = COALESCE(
                    (SELECT p.`sellingPrice` FROM `products` p
                     WHERE p.`id` = `inventory_batches`.`productId`),
                    0
                )
                """.trimIndent()
            )
            database.execSQL(
                """
                UPDATE `products`
                SET `buyingPrice` = COALESCE(
                    (SELECT b.`unitCost` FROM `inventory_batches` b
                     WHERE b.`productId` = `products`.`id`
                     ORDER BY b.`receivedAt` DESC, b.`id` DESC
                     LIMIT 1),
                    `buyingPrice`
                )
                WHERE EXISTS (
                    SELECT 1 FROM `inventory_batches` b
                    WHERE b.`productId` = `products`.`id`
                )
                """.trimIndent()
            )
        }
    }

    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `purchase_items` ADD COLUMN `batchId` INTEGER"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_purchase_items_batchId` " +
                    "ON `purchase_items` (`batchId`)"
            )
            database.execSQL(
                """
                UPDATE `purchase_items`
                SET `batchId` = (
                    SELECT b.`id` FROM `inventory_batches` b
                    WHERE b.`purchaseItemId` = `purchase_items`.`id`
                    ORDER BY b.`id` DESC
                    LIMIT 1
                )
                WHERE EXISTS (
                    SELECT 1 FROM `inventory_batches` b
                    WHERE b.`purchaseItemId` = `purchase_items`.`id`
                )
                """.trimIndent()
            )
            database.execSQL(
                "ALTER TABLE `inventory_batches` " +
                    "ADD COLUMN `unitCostCents` INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                "ALTER TABLE `inventory_batches` " +
                    "ADD COLUMN `sellingPriceCents` INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                """
                UPDATE `inventory_batches`
                SET `unitCostCents` = CAST(ROUND(`unitCost` * 100.0) AS INTEGER),
                    `sellingPriceCents` = CAST(ROUND(`sellingPrice` * 100.0) AS INTEGER)
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inventory_batches_productId_unitCostCents_sellingPriceCents` " +
                    "ON `inventory_batches` (`productId`, `unitCostCents`, `sellingPriceCents`)"
            )
        }
    }

    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `stock_adjustments` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `productId` INTEGER NOT NULL,
                    `batchId` INTEGER NOT NULL,
                    `adjustedBy` INTEGER NOT NULL,
                    `adjustmentType` TEXT NOT NULL,
                    `quantity` REAL NOT NULL,
                    `reason` TEXT NOT NULL,
                    `notes` TEXT,
                    `resultingBatchQuantity` REAL NOT NULL,
                    `resultingProductQuantity` REAL NOT NULL,
                    `isSynced` INTEGER NOT NULL,
                    `firebaseId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`batchId`) REFERENCES `inventory_batches`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                    FOREIGN KEY(`adjustedBy`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_productId` " +
                    "ON `stock_adjustments` (`productId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_batchId` " +
                    "ON `stock_adjustments` (`batchId`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_stock_adjustments_adjustedBy` " +
                    "ON `stock_adjustments` (`adjustedBy`)"
            )
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
            .addMigrations(
                migration1To2,
                migration2To3,
                migration3To4,
                migration4To5,
                migration5To6,
                migration6To7,
                migration7To8
            )
            .addCallback(databaseCallback)
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
    fun provideInventoryBatchDao(database: FolioraDatabase): InventoryBatchDao =
        database.inventoryBatchDao()

    @Provides
    fun provideStockAdjustmentDao(database: FolioraDatabase): StockAdjustmentDao =
        database.stockAdjustmentDao()

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
