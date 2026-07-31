package com.foliora.pos.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.PendingDeletionDao
import com.foliora.pos.data.local.entity.*
import com.foliora.pos.data.repository.*
import com.foliora.pos.ui.viewmodel.launchCrudCatching
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for managing application shop settings and authentication logout in Foliora POS.
 * Also handles direct Firebase cloud synchronization (push + pull).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingRepository: SettingRepository,
    private val saleRepository: SaleRepository,
    private val productRepository: ProductRepository,
    private val customerRepository: CustomerRepository,
    private val userRepository: UserRepository,
    private val categoryRepository: CategoryRepository,
    private val supplierRepository: SupplierRepository,
    private val purchaseRepository: PurchaseRepository,
    private val database: FolioraDatabase,
    private val pendingDeletionDao: PendingDeletionDao
) : ViewModel() {

    private val _settings = MutableStateFlow<SettingEntity?>(null)
    val settings: StateFlow<SettingEntity?> = _settings.asStateFlow()

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        launchCrudCatching("Unable to load settings", onError = { _syncStatus.value = it }) {
            val currentSettings = settingRepository.initializeSettings()
            _settings.value = currentSettings
        }
    }

    fun updateSettings(
        shopName: String,
        address: String,
        phone: String,
        receiptMessage: String
    ) {
        launchCrudCatching("Unable to save settings", onError = { _syncStatus.value = it }) {
            val current = _settings.value ?: settingRepository.initializeSettings()
            val updated = current.copy(
                shopName = shopName.trim(),
                address = address.trim(),
                phoneNumber = phone.trim(),
                receiptMessage = receiptMessage.trim(),
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            settingRepository.updateSetting(updated)
            _settings.value = updated
        }
    }

    fun logout() {
        FirebaseAuth.getInstance().signOut()
    }

    /**
     * Performs a direct Firebase sync (push + pull) on the calling coroutine,
     * reporting progress via [syncStatus] and errors in real-time.
     */
    fun syncNow() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Starting sync..."
            val firestore = FirebaseFirestore.getInstance()
            val errors = mutableListOf<String>()

            try {
                _syncStatus.value = "Deleting cloud records..."
                processPendingDeletions(firestore)

                // ============ PUSH (local -> cloud) ============
                _syncStatus.value = "Pushing sales..."
                pushCollection(firestore, "sales", saleRepository.getUnsyncedSales(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    saleRepository::updateSale,
                    { saleRepository.updateSale(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing sale items..."
                pushCollection(firestore, "sale_items", saleRepository.getUnsyncedSaleItems(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    saleRepository::updateSaleItem,
                    { saleRepository.updateSaleItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing products..."
                pushCollection(firestore, "products", productRepository.getUnsyncedProducts(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    productRepository::updateProduct,
                    { productRepository.updateProduct(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing customers..."
                pushCollection(firestore, "customers", customerRepository.getUnsyncedCustomers(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    customerRepository::updateCustomer,
                    { customerRepository.updateCustomer(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing users..."
                pushCollection(firestore, "users", userRepository.getUnsyncedUsers(),
                    { it.id },
                    {
                        it.firebaseId?.takeIf(String::isNotBlank)
                            ?: it.firebaseAuthUid.takeIf(String::isNotBlank)
                    },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    userRepository::updateUser,
                    { userRepository.updateUser(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing categories..."
                pushCollection(firestore, "categories", categoryRepository.getUnsyncedCategories(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    categoryRepository::updateCategory,
                    { categoryRepository.updateCategory(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing suppliers..."
                pushCollection(firestore, "suppliers", supplierRepository.getUnsyncedSuppliers(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    supplierRepository::updateSupplier,
                    { supplierRepository.updateSupplier(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing purchases..."
                pushCollection(firestore, "purchases", purchaseRepository.getUnsyncedPurchases(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    purchaseRepository::updatePurchase,
                    { purchaseRepository.updatePurchase(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing purchase items..."
                pushCollection(firestore, "purchase_items", purchaseRepository.getUnsyncedPurchaseItems(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    purchaseRepository::updatePurchaseItem,
                    { purchaseRepository.updatePurchaseItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing settings..."
                pushCollection(firestore, "settings", settingRepository.getUnsyncedSettings(),
                    { it.id }, { it.firebaseId },
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    settingRepository::updateSetting,
                    { settingRepository.updateSetting(it.copy(isSynced = true)) }, errors)

                // ============ PULL (cloud -> local) ============
                // Pull order respects foreign key dependencies

                _syncStatus.value = "Pulling users..."
                pullCollection(firestore, "users", UserEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { userRepository.insertUser(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling categories..."
                pullCollection(firestore, "categories", CategoryEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { categoryRepository.insertCategory(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling suppliers..."
                pullCollection(firestore, "suppliers", SupplierEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { supplierRepository.insertSupplier(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling customers..."
                pullCollection(firestore, "customers", CustomerEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { customerRepository.insertCustomer(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling products..."
                pullCollection(firestore, "products", ProductEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { productRepository.insertProduct(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling purchases..."
                pullCollection(firestore, "purchases", PurchaseEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { purchaseRepository.insertPurchase(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling purchase items..."
                pullCollection(firestore, "purchase_items", PurchaseItemEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { purchaseRepository.insertPurchaseItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling sales..."
                pullCollection(firestore, "sales", SaleEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { saleRepository.insertSale(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling sale items..."
                pullCollection(firestore, "sale_items", SaleItemEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { saleRepository.insertSaleItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling settings..."
                pullCollection(firestore, "settings", SettingEntity::class.java,
                    { item, firebaseId -> item.copy(firebaseId = firebaseId) },
                    { settingRepository.insertSetting(it.copy(isSynced = true)) }, errors)

                // ============ RESULT ============
                if (errors.isEmpty()) {
                    _syncStatus.value = "Sync completed successfully!"
                } else {
                    _syncStatus.value = "Sync done with ${errors.size} errors: ${errors.first()}"
                }
            } catch (e: Exception) {
                Log.e("SyncDirect", "Fatal sync error", e)
                _syncStatus.value = "Sync FAILED: ${e.localizedMessage}"
            } finally {
                _isSyncing.value = false
            }
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

    private suspend fun <T : Any> pushCollection(
        firestore: FirebaseFirestore,
        collection: String,
        items: List<T>,
        getLocalId: (T) -> Int,
        getFirebaseId: (T) -> String?,
        withFirebaseId: (T, String) -> T,
        persistAssignedId: suspend (T) -> Unit,
        markSynced: suspend (T) -> Unit,
        errors: MutableList<String>
    ) {
        Log.d("SyncDirect", "Pushing ${items.size} items to $collection")
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
                val msg = "Push $collection local ID ${getLocalId(item)}: ${e.localizedMessage}"
                Log.e("SyncDirect", msg, e)
                errors.add(msg)
            }
        }
    }

    private suspend fun <T : Any> pullCollection(
        firestore: FirebaseFirestore,
        collection: String,
        clazz: Class<T>,
        withFirebaseId: (T, String) -> T,
        insert: suspend (T) -> Unit,
        errors: MutableList<String>
    ) {
        try {
            val snapshot = firestore.collection(collection).get().await()
            Log.d("SyncDirect", "Pulled ${snapshot.documents.size} docs from $collection")
            for (doc in snapshot.documents) {
                try {
                    val obj = doc.toObject(clazz)
                    if (obj != null) {
                        database.withTransaction {
                            if (!pendingDeletionDao.exists(collection, doc.id)) {
                                insert(withFirebaseId(obj, doc.id))
                            }
                        }
                    } else {
                        Log.w("SyncDirect", "Null object from $collection doc ${doc.id}")
                    }
                } catch (e: Exception) {
                    val msg = "Pull $collection doc ${doc.id}: ${e.localizedMessage}"
                    Log.e("SyncDirect", msg, e)
                    errors.add(msg)
                }
            }
        } catch (e: Exception) {
            val msg = "Pull $collection failed: ${e.localizedMessage}"
            Log.e("SyncDirect", msg, e)
            errors.add(msg)
        }
    }
}
