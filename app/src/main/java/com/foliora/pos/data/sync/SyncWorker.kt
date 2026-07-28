package com.foliora.pos.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foliora.pos.data.local.entity.*
import com.foliora.pos.data.repository.*
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.tasks.await

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val saleRepository: SaleRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: SupplierRepository,
    private val purchaseRepository: PurchaseRepository,
    private val settingRepository: SettingRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting WorkManager sync execution...")
        val firestore = FirebaseFirestore.getInstance()

        return try {
            // Push local changes to cloud first
            pushSales(firestore)
            pushSaleItems(firestore)
            pushProducts(firestore)
            pushCustomers(firestore)
            pushUsers(firestore)
            pushCategories(firestore)
            pushSuppliers(firestore)
            pushPurchases(firestore)
            pushPurchaseItems(firestore)
            pushSettings(firestore)

            // Pull cloud changes to local DB
            pullUsers(firestore)
            pullCategories(firestore)
            pullSuppliers(firestore)
            pullProducts(firestore)
            pullCustomers(firestore)
            pullPurchases(firestore)
            pullPurchaseItems(firestore)
            pullSales(firestore)
            pullSaleItems(firestore)
            pullSettings(firestore)

            Log.d(TAG, "WorkManager sync completed successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Fatal failure during sync execution: ${e.localizedMessage}", e)
            Result.success()
        }
    }

    // ==========================================
    // PUSH LOGIC (Local to Cloud)
    // ==========================================

    private suspend fun pushSales(firestore: FirebaseFirestore) {
        for (item in saleRepository.getUnsyncedSales()) {
            try { firestore.collection(COLLECTION_SALES).document(item.id.toString()).set(item).await(); saleRepository.updateSale(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing sale ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushSaleItems(firestore: FirebaseFirestore) {
        for (item in saleRepository.getUnsyncedSaleItems()) {
            try { firestore.collection(COLLECTION_SALE_ITEMS).document(item.id.toString()).set(item).await(); saleRepository.updateSaleItem(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing sale item ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushProducts(firestore: FirebaseFirestore) {
        for (item in productRepository.getUnsyncedProducts()) {
            try { firestore.collection(COLLECTION_PRODUCTS).document(item.id.toString()).set(item).await(); productRepository.updateProduct(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing product ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushCustomers(firestore: FirebaseFirestore) {
        for (item in customerRepository.getUnsyncedCustomers()) {
            try { firestore.collection(COLLECTION_CUSTOMERS).document(item.id.toString()).set(item).await(); customerRepository.updateCustomer(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing customer ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushUsers(firestore: FirebaseFirestore) {
        for (item in userRepository.getUnsyncedUsers()) {
            try { firestore.collection(COLLECTION_USERS).document(item.id.toString()).set(item).await(); userRepository.updateUser(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing user ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushCategories(firestore: FirebaseFirestore) {
        for (item in categoryRepository.getUnsyncedCategories()) {
            try { firestore.collection(COLLECTION_CATEGORIES).document(item.id.toString()).set(item).await(); categoryRepository.updateCategory(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing category ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushSuppliers(firestore: FirebaseFirestore) {
        for (item in supplierRepository.getUnsyncedSuppliers()) {
            try { firestore.collection(COLLECTION_SUPPLIERS).document(item.id.toString()).set(item).await(); supplierRepository.updateSupplier(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing supplier ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushPurchases(firestore: FirebaseFirestore) {
        for (item in purchaseRepository.getUnsyncedPurchases()) {
            try { firestore.collection(COLLECTION_PURCHASES).document(item.id.toString()).set(item).await(); purchaseRepository.updatePurchase(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing purchase ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushPurchaseItems(firestore: FirebaseFirestore) {
        for (item in purchaseRepository.getUnsyncedPurchaseItems()) {
            try { firestore.collection(COLLECTION_PURCHASE_ITEMS).document(item.id.toString()).set(item).await(); purchaseRepository.updatePurchaseItem(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing purchase item ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    private suspend fun pushSettings(firestore: FirebaseFirestore) {
        for (item in settingRepository.getUnsyncedSettings()) {
            try { firestore.collection(COLLECTION_SETTINGS).document(item.id.toString()).set(item).await(); settingRepository.updateSetting(item.copy(isSynced = true)) } catch (e: Exception) { Log.e(TAG, "Error syncing setting ID ${item.id}: ${e.localizedMessage}") }
        }
    }

    // ==========================================
    // PULL LOGIC (Cloud to Local)
    // ==========================================

    private suspend fun pullSales(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SALES).get().await().documents) { doc.toObject(SaleEntity::class.java)?.let { saleRepository.insertSale(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling sales: ${e.localizedMessage}") }
    }

    private suspend fun pullSaleItems(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SALE_ITEMS).get().await().documents) { doc.toObject(SaleItemEntity::class.java)?.let { saleRepository.insertSaleItem(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling sale items: ${e.localizedMessage}") }
    }

    private suspend fun pullProducts(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PRODUCTS).get().await().documents) { doc.toObject(ProductEntity::class.java)?.let { productRepository.insertProduct(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling products: ${e.localizedMessage}") }
    }

    private suspend fun pullCustomers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_CUSTOMERS).get().await().documents) { doc.toObject(CustomerEntity::class.java)?.let { customerRepository.insertCustomer(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling customers: ${e.localizedMessage}") }
    }

    private suspend fun pullUsers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_USERS).get().await().documents) { doc.toObject(UserEntity::class.java)?.let { userRepository.insertUser(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling users: ${e.localizedMessage}") }
    }

    private suspend fun pullCategories(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_CATEGORIES).get().await().documents) { doc.toObject(CategoryEntity::class.java)?.let { categoryRepository.insertCategory(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling categories: ${e.localizedMessage}") }
    }

    private suspend fun pullSuppliers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SUPPLIERS).get().await().documents) { doc.toObject(SupplierEntity::class.java)?.let { supplierRepository.insertSupplier(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling suppliers: ${e.localizedMessage}") }
    }

    private suspend fun pullPurchases(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PURCHASES).get().await().documents) { doc.toObject(PurchaseEntity::class.java)?.let { purchaseRepository.insertPurchase(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling purchases: ${e.localizedMessage}") }
    }

    private suspend fun pullPurchaseItems(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PURCHASE_ITEMS).get().await().documents) { doc.toObject(PurchaseItemEntity::class.java)?.let { purchaseRepository.insertPurchaseItem(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling purchase items: ${e.localizedMessage}") }
    }

    private suspend fun pullSettings(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SETTINGS).get().await().documents) { doc.toObject(SettingEntity::class.java)?.let { settingRepository.insertSetting(it.copy(isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling settings: ${e.localizedMessage}") }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val COLLECTION_SALES = "sales"
        private const val COLLECTION_SALE_ITEMS = "sale_items"
        private const val COLLECTION_PRODUCTS = "products"
        private const val COLLECTION_CUSTOMERS = "customers"
        private const val COLLECTION_USERS = "users"
        private const val COLLECTION_CATEGORIES = "categories"
        private const val COLLECTION_SUPPLIERS = "suppliers"
        private const val COLLECTION_PURCHASES = "purchases"
        private const val COLLECTION_PURCHASE_ITEMS = "purchase_items"
        private const val COLLECTION_SETTINGS = "settings"
    }
}
