package com.foliora.pos.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.PendingDeletionDao
import com.foliora.pos.data.local.entity.*
import com.foliora.pos.data.repository.*
import com.google.firebase.firestore.FirebaseFirestore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.UUID

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
    private val settingRepository: SettingRepository,
    private val database: FolioraDatabase,
    private val pendingDeletionDao: PendingDeletionDao
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting WorkManager sync execution...")
        val firestore = FirebaseFirestore.getInstance()

        try {
            processPendingDeletions(firestore)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not sync pending deletions: ${e.localizedMessage}", e)
            return Result.retry()
        }

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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fatal failure during sync execution: ${e.localizedMessage}", e)
            Result.retry()
        }
    }

    private suspend fun processPendingDeletions(firestore: FirebaseFirestore) {
        for (deletion in pendingDeletionDao.getAll()) {
            firestore.collection(deletion.collection)
                .document(deletion.firebaseId)
                .delete()
                .await()
            pendingDeletionDao.delete(deletion)
        }
    }

    // ==========================================
    // PUSH LOGIC (Local to Cloud)
    // ==========================================

    private suspend fun pushSales(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_SALES,
            items = saleRepository.getUnsyncedSales(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = saleRepository::updateSale,
            markSynced = { saleRepository.updateSale(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushSaleItems(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_SALE_ITEMS,
            items = saleRepository.getUnsyncedSaleItems(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = saleRepository::updateSaleItem,
            markSynced = { saleRepository.updateSaleItem(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushProducts(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_PRODUCTS,
            items = productRepository.getUnsyncedProducts(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = productRepository::updateProduct,
            markSynced = { productRepository.updateProduct(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushCustomers(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_CUSTOMERS,
            items = customerRepository.getUnsyncedCustomers(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = customerRepository::updateCustomer,
            markSynced = { customerRepository.updateCustomer(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushUsers(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_USERS,
            items = userRepository.getUnsyncedUsers(),
            getLocalId = { it.id },
            getFirebaseId = {
                it.firebaseId?.takeIf(String::isNotBlank)
                    ?: it.firebaseAuthUid.takeIf(String::isNotBlank)
            },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = userRepository::updateUser,
            markSynced = { userRepository.updateUser(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushCategories(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_CATEGORIES,
            items = categoryRepository.getUnsyncedCategories(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = categoryRepository::updateCategory,
            markSynced = { categoryRepository.updateCategory(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushSuppliers(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_SUPPLIERS,
            items = supplierRepository.getUnsyncedSuppliers(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = supplierRepository::updateSupplier,
            markSynced = { supplierRepository.updateSupplier(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushPurchases(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_PURCHASES,
            items = purchaseRepository.getUnsyncedPurchases(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = purchaseRepository::updatePurchase,
            markSynced = { purchaseRepository.updatePurchase(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushPurchaseItems(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_PURCHASE_ITEMS,
            items = purchaseRepository.getUnsyncedPurchaseItems(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = purchaseRepository::updatePurchaseItem,
            markSynced = { purchaseRepository.updatePurchaseItem(it.copy(isSynced = true)) }
        )
    }

    private suspend fun pushSettings(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_SETTINGS,
            items = settingRepository.getUnsyncedSettings(),
            getLocalId = { it.id },
            getFirebaseId = { it.firebaseId },
            withFirebaseId = { item, firebaseId -> item.copy(firebaseId = firebaseId) },
            persistAssignedId = settingRepository::updateSetting,
            markSynced = { settingRepository.updateSetting(it.copy(isSynced = true)) }
        )
    }

    private suspend fun <T : Any> pushCollection(
        firestore: FirebaseFirestore,
        collection: String,
        items: List<T>,
        getLocalId: (T) -> Int,
        getFirebaseId: (T) -> String?,
        withFirebaseId: (T, String) -> T,
        persistAssignedId: suspend (T) -> Unit,
        markSynced: suspend (T) -> Unit
    ) {
        for (item in items) {
            try {
                val existingFirebaseId = getFirebaseId(item)?.takeIf(String::isNotBlank)
                val documentId = existingFirebaseId ?: UUID.randomUUID().toString()
                val itemWithFirebaseId = withFirebaseId(item, documentId)

                if (existingFirebaseId == null) {
                    persistAssignedId(itemWithFirebaseId)
                }

                firestore.collection(collection).document(documentId).set(itemWithFirebaseId).await()
                markSynced(itemWithFirebaseId)
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error syncing $collection local ID ${getLocalId(item)}: ${e.localizedMessage}",
                    e
                )
                throw e
            }
        }
    }

    // ==========================================
    // PULL LOGIC (Cloud to Local)
    // ==========================================

    private suspend fun pullSales(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SALES).get().await().documents) { doc.toObject(SaleEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_SALES, doc.id) { saleRepository.insertSale(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling sales: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullSaleItems(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SALE_ITEMS).get().await().documents) { doc.toObject(SaleItemEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_SALE_ITEMS, doc.id) { saleRepository.insertSaleItem(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling sale items: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullProducts(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PRODUCTS).get().await().documents) { doc.toObject(ProductEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_PRODUCTS, doc.id) { productRepository.insertProduct(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling products: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullCustomers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_CUSTOMERS).get().await().documents) { doc.toObject(CustomerEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_CUSTOMERS, doc.id) { customerRepository.insertCustomer(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling customers: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullUsers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_USERS).get().await().documents) { doc.toObject(UserEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_USERS, doc.id) { userRepository.insertUser(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling users: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullCategories(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_CATEGORIES).get().await().documents) { doc.toObject(CategoryEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_CATEGORIES, doc.id) { categoryRepository.insertCategory(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling categories: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullSuppliers(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SUPPLIERS).get().await().documents) { doc.toObject(SupplierEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_SUPPLIERS, doc.id) { supplierRepository.insertSupplier(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling suppliers: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullPurchases(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PURCHASES).get().await().documents) { doc.toObject(PurchaseEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_PURCHASES, doc.id) { purchaseRepository.insertPurchase(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling purchases: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullPurchaseItems(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_PURCHASE_ITEMS).get().await().documents) { doc.toObject(PurchaseItemEntity::class.java)?.let { item -> insertPulledIfNotPending(COLLECTION_PURCHASE_ITEMS, doc.id) { purchaseRepository.insertPurchaseItem(item.copy(firebaseId = doc.id, isSynced = true)) } } } } catch (e: Exception) { Log.e(TAG, "Error pulling purchase items: ${e.localizedMessage}"); throw e }
    }

    private suspend fun pullSettings(firestore: FirebaseFirestore) {
        try { for (doc in firestore.collection(COLLECTION_SETTINGS).get().await().documents) { doc.toObject(SettingEntity::class.java)?.let { settingRepository.insertSetting(it.copy(firebaseId = doc.id, isSynced = true)) } } } catch (e: Exception) { Log.e(TAG, "Error pulling settings: ${e.localizedMessage}"); throw e }
    }

    private suspend fun insertPulledIfNotPending(
        collection: String,
        firebaseId: String,
        insert: suspend () -> Unit
    ) {
        database.withTransaction {
            if (!pendingDeletionDao.exists(collection, firebaseId)) {
                insert()
            }
        }
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
