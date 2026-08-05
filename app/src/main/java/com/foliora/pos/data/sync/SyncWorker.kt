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
import com.google.firebase.firestore.DocumentSnapshot
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
    private val pendingDeletionDao: PendingDeletionDao,
    private val syncCheckpointStore: SyncCheckpointStore,
    private val syncExecutionGuard: SyncExecutionGuard,
    private val syncRelationshipMapper: SyncRelationshipMapper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = syncExecutionGuard.runExclusive {
        performSync()
    }

    private suspend fun performSync(): Result {
        Log.d(TAG, "Starting WorkManager sync execution...")
        val firestore = FirebaseFirestore.getInstance()
        val syncStartedAt = System.currentTimeMillis()

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
            pushUsers(firestore)
            pushCategories(firestore)
            pushSuppliers(firestore)
            pushCustomers(firestore)
            pushProducts(firestore)
            pushPurchases(firestore)
            pushPurchaseItems(firestore)
            pushSales(firestore)
            pushSaleItems(firestore)
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

            syncCheckpointStore.markSuccessfulSync(syncStartedAt)
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
        for (deletions in pendingDeletionDao.getAll().chunked(MAX_FIRESTORE_BATCH_OPERATIONS)) {
            val batch = firestore.batch()
            for (deletion in deletions) {
                batch.delete(
                    firestore.collection(deletion.collection).document(deletion.firebaseId)
                )
            }
            batch.commit().await()
            for (deletion in deletions) {
                pendingDeletionDao.delete(deletion)
            }
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
            markSynced = { saleRepository.updateSale(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::saleToCloud
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
            markSynced = { saleRepository.updateSaleItem(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::saleItemToCloud
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
            markSynced = { productRepository.updateProduct(it.copy(isSynced = true)) },
            prepareForCloud = { product ->
                syncRelationshipMapper.productToCloud(
                    productRepository.prepareProductForCloud(product)
                )
            }
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
            markSynced = { customerRepository.updateCustomer(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::customerToCloud
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
            markSynced = { userRepository.updateUser(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::userToCloud
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
            markSynced = { categoryRepository.updateCategory(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::categoryToCloud
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
            markSynced = { supplierRepository.updateSupplier(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::supplierToCloud
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
            markSynced = { purchaseRepository.updatePurchase(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::purchaseToCloud
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
            markSynced = { purchaseRepository.updatePurchaseItem(it.copy(isSynced = true)) },
            prepareForCloud = syncRelationshipMapper::purchaseItemToCloud
        )
    }

    private suspend fun pushSettings(firestore: FirebaseFirestore) {
        pushCollection(
            firestore = firestore,
            collection = COLLECTION_SETTINGS,
            items = settingRepository.getUnsyncedSettings(),
            getLocalId = { it.id },
            getFirebaseId = { SETTINGS_DOCUMENT_ID },
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
        markSynced: suspend (T) -> Unit,
        prepareForCloud: suspend (T) -> Any = { it }
    ) {
        for (itemBatch in items.chunked(MAX_FIRESTORE_BATCH_OPERATIONS)) {
            try {
                val firestoreBatch = firestore.batch()
                val preparedLocalItems = mutableListOf<T>()

                for (item in itemBatch) {
                    val existingFirebaseId = getFirebaseId(item)?.takeIf(String::isNotBlank)
                    val documentId = existingFirebaseId ?: UUID.randomUUID().toString()
                    val itemWithFirebaseId = withFirebaseId(item, documentId)

                    if (existingFirebaseId == null) {
                        persistAssignedId(itemWithFirebaseId)
                    }

                    val cloudItem = prepareForCloud(itemWithFirebaseId)
                    firestoreBatch.set(
                        firestore.collection(collection).document(documentId),
                        cloudItem
                    )
                    preparedLocalItems.add(itemWithFirebaseId)
                }

                firestoreBatch.commit().await()
                for (preparedItem in preparedLocalItems) {
                    markSynced(preparedItem)
                }
            } catch (e: Exception) {
                Log.e(
                    TAG,
                    "Error syncing $collection batch starting at local ID " +
                        "${getLocalId(itemBatch.first())}: ${e.localizedMessage}",
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
        pullMappedCollection(firestore, COLLECTION_SALES, syncRelationshipMapper::saleFromCloud) {
            saleRepository.insertSale(it)
        }
    }

    private suspend fun pullSaleItems(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_SALE_ITEMS, syncRelationshipMapper::saleItemFromCloud) {
            saleRepository.insertSaleItem(it)
        }
    }

    private suspend fun pullProducts(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_PRODUCTS, syncRelationshipMapper::productFromCloud) {
            productRepository.insertProduct(it)
        }
    }

    private suspend fun pullCustomers(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_CUSTOMERS, syncRelationshipMapper::customerFromCloud) {
            customerRepository.insertCustomer(it)
        }
    }

    private suspend fun pullUsers(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_USERS, syncRelationshipMapper::userFromCloud) {
            userRepository.insertUser(it)
        }
    }

    private suspend fun pullCategories(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_CATEGORIES, syncRelationshipMapper::categoryFromCloud) {
            categoryRepository.insertCategory(it)
        }
    }

    private suspend fun pullSuppliers(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_SUPPLIERS, syncRelationshipMapper::supplierFromCloud) {
            supplierRepository.insertSupplier(it)
        }
    }

    private suspend fun pullPurchases(firestore: FirebaseFirestore) {
        pullMappedCollection(firestore, COLLECTION_PURCHASES, syncRelationshipMapper::purchaseFromCloud) {
            purchaseRepository.insertPurchase(it)
        }
    }

    private suspend fun pullPurchaseItems(firestore: FirebaseFirestore) {
        pullMappedCollection(
            firestore,
            COLLECTION_PURCHASE_ITEMS,
            syncRelationshipMapper::purchaseItemFromCloud
        ) {
            purchaseRepository.insertPurchaseItem(it)
        }
    }

    private suspend fun <T : Any> pullMappedCollection(
        firestore: FirebaseFirestore,
        collection: String,
        mapFromCloud: suspend (DocumentSnapshot) -> T?,
        insert: suspend (T) -> Unit
    ) {
        try {
            val documents = syncCheckpointStore.queryFor(firestore, collection).get().await().documents
            for (document in documents) {
                mapFromCloud(document)?.let { item ->
                    insertPulledIfNotPending(collection, document.id) { insert(item) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling $collection: ${e.localizedMessage}", e)
            throw e
        }
    }

    private suspend fun pullSettings(firestore: FirebaseFirestore) {
        try {
            val document = firestore.collection(COLLECTION_SETTINGS)
                .document(SETTINGS_DOCUMENT_ID)
                .get()
                .await()
            document.toObject(SettingEntity::class.java)?.let { setting ->
                insertPulledIfNotPending(COLLECTION_SETTINGS, SETTINGS_DOCUMENT_ID) {
                    settingRepository.insertSetting(
                        setting.copy(firebaseId = SETTINGS_DOCUMENT_ID, isSynced = true)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pulling settings: ${e.localizedMessage}")
            throw e
        }
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
        private const val SETTINGS_DOCUMENT_ID = "shop"
        private const val MAX_FIRESTORE_BATCH_OPERATIONS = 400
    }
}
