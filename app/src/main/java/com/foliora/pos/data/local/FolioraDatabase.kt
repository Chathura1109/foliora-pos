package com.foliora.pos.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.foliora.pos.data.local.dao.*
import com.foliora.pos.data.local.entity.*

/**
 * The central Room database for the entire Foliora POS app.
 *
 * HOW ROOM WORKS (for your viva):
 * Room is a wrapper around SQLite that lets you define tables as Kotlin data classes
 * (Entities) and queries as interface methods (DAOs). At compile time, Room uses KSP
 * to auto-generate all the actual SQL and database boilerplate code.
 *
 * This @Database annotation tells Room:
 *   1. Which entities (tables) exist in this database
 *   2. The version number (increment this when you change the schema)
 *   3. Whether to export the schema for migration tracking
 *
 * The abstract functions below tell Room which DAOs (query interfaces) to implement.
 * Room generates a concrete subclass of this at compile time.
 */
@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        CustomerEntity::class,
        SupplierEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        SettingEntity::class
    ],
    version = 1,
    exportSchema = false // Set to true in production for migration tracking
)
abstract class FolioraDatabase : RoomDatabase() {

    // Each abstract function returns a DAO — Room auto-generates the implementation
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseItemDao(): PurchaseItemDao
    abstract fun saleDao(): SaleDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun settingDao(): SettingDao
}
