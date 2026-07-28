package com.foliora.pos.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foliora.pos.data.local.entity.*
import com.foliora.pos.data.repository.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val purchaseRepository: PurchaseRepository
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
                // ============ PUSH (local -> cloud) ============
                _syncStatus.value = "Pushing sales..."
                pushCollection(firestore, "sales", saleRepository.getUnsyncedSales(),
                    { it.id }, { saleRepository.updateSale(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing sale items..."
                pushCollection(firestore, "sale_items", saleRepository.getUnsyncedSaleItems(),
                    { it.id }, { saleRepository.updateSaleItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing products..."
                pushCollection(firestore, "products", productRepository.getUnsyncedProducts(),
                    { it.id }, { productRepository.updateProduct(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing customers..."
                pushCollection(firestore, "customers", customerRepository.getUnsyncedCustomers(),
                    { it.id }, { customerRepository.updateCustomer(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing users..."
                pushCollection(firestore, "users", userRepository.getUnsyncedUsers(),
                    { it.id }, { userRepository.updateUser(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing categories..."
                pushCollection(firestore, "categories", categoryRepository.getUnsyncedCategories(),
                    { it.id }, { categoryRepository.updateCategory(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing suppliers..."
                pushCollection(firestore, "suppliers", supplierRepository.getUnsyncedSuppliers(),
                    { it.id }, { supplierRepository.updateSupplier(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing purchases..."
                pushCollection(firestore, "purchases", purchaseRepository.getUnsyncedPurchases(),
                    { it.id }, { purchaseRepository.updatePurchase(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing purchase items..."
                pushCollection(firestore, "purchase_items", purchaseRepository.getUnsyncedPurchaseItems(),
                    { it.id }, { purchaseRepository.updatePurchaseItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pushing settings..."
                pushCollection(firestore, "settings", settingRepository.getUnsyncedSettings(),
                    { it.id }, { settingRepository.updateSetting(it.copy(isSynced = true)) }, errors)

                // ============ PULL (cloud -> local) ============
                // Pull order respects foreign key dependencies

                _syncStatus.value = "Pulling users..."
                pullCollection(firestore, "users", UserEntity::class.java,
                    { userRepository.insertUser(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling categories..."
                pullCollection(firestore, "categories", CategoryEntity::class.java,
                    { categoryRepository.insertCategory(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling suppliers..."
                pullCollection(firestore, "suppliers", SupplierEntity::class.java,
                    { supplierRepository.insertSupplier(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling customers..."
                pullCollection(firestore, "customers", CustomerEntity::class.java,
                    { customerRepository.insertCustomer(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling products..."
                pullCollection(firestore, "products", ProductEntity::class.java,
                    { productRepository.insertProduct(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling purchases..."
                pullCollection(firestore, "purchases", PurchaseEntity::class.java,
                    { purchaseRepository.insertPurchase(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling purchase items..."
                pullCollection(firestore, "purchase_items", PurchaseItemEntity::class.java,
                    { purchaseRepository.insertPurchaseItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling sales..."
                pullCollection(firestore, "sales", SaleEntity::class.java,
                    { saleRepository.insertSale(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling sale items..."
                pullCollection(firestore, "sale_items", SaleItemEntity::class.java,
                    { saleRepository.insertSaleItem(it.copy(isSynced = true)) }, errors)

                _syncStatus.value = "Pulling settings..."
                pullCollection(firestore, "settings", SettingEntity::class.java,
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

    private suspend fun <T : Any> pushCollection(
        firestore: FirebaseFirestore,
        collection: String,
        items: List<T>,
        getId: (T) -> Int,
        markSynced: suspend (T) -> Unit,
        errors: MutableList<String>
    ) {
        Log.d("SyncDirect", "Pushing ${items.size} items to $collection")
        for (item in items) {
            try {
                firestore.collection(collection).document(getId(item).toString()).set(item).await()
                markSynced(item)
            } catch (e: Exception) {
                val msg = "Push $collection ID ${getId(item)}: ${e.localizedMessage}"
                Log.e("SyncDirect", msg, e)
                errors.add(msg)
            }
        }
    }

    private suspend fun <T : Any> pullCollection(
        firestore: FirebaseFirestore,
        collection: String,
        clazz: Class<T>,
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
                        insert(obj)
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
