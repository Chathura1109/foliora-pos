package com.foliora.pos.data.sync

import com.foliora.pos.data.local.dao.CategoryDao
import com.foliora.pos.data.local.dao.CustomerDao
import com.foliora.pos.data.local.dao.InventoryBatchDao
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.PurchaseDao
import com.foliora.pos.data.local.dao.PurchaseItemDao
import com.foliora.pos.data.local.dao.SaleDao
import com.foliora.pos.data.local.dao.SaleItemDao
import com.foliora.pos.data.local.dao.SupplierDao
import com.foliora.pos.data.local.dao.StockAdjustmentDao
import com.foliora.pos.data.local.dao.UserDao
import com.foliora.pos.data.local.entity.CategoryEntity
import com.foliora.pos.data.local.entity.CustomerEntity
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.PurchaseItemEntity
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity
import com.foliora.pos.data.local.entity.SupplierEntity
import com.foliora.pos.data.local.entity.StockAdjustmentEntity
import com.foliora.pos.data.local.entity.UserEntity
import com.foliora.pos.data.local.entity.priceToCents
import com.google.firebase.firestore.DocumentSnapshot
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts device-local Room relationships to stable Firestore document references and back.
 */
@Singleton
class SyncRelationshipMapper @Inject constructor(
    private val userDao: UserDao,
    private val categoryDao: CategoryDao,
    private val customerDao: CustomerDao,
    private val supplierDao: SupplierDao,
    private val productDao: ProductDao,
    private val inventoryBatchDao: InventoryBatchDao,
    private val stockAdjustmentDao: StockAdjustmentDao,
    private val purchaseDao: PurchaseDao,
    private val purchaseItemDao: PurchaseItemDao,
    private val saleDao: SaleDao,
    private val saleItemDao: SaleItemDao
) {

    suspend fun userToCloud(user: UserEntity): Map<String, Any?> = mapOf(
        "name" to user.name,
        "role" to user.role,
        "firebaseAuthUid" to user.firebaseAuthUid,
        "isActive" to user.isActive,
        "isSynced" to true,
        "firebaseId" to user.firebaseId,
        "createdAt" to user.createdAt,
        "updatedAt" to user.updatedAt
    )

    suspend fun categoryToCloud(category: CategoryEntity): Map<String, Any?> = mapOf(
        "name" to category.name,
        "description" to category.description,
        "isSynced" to true,
        "firebaseId" to category.firebaseId,
        "createdAt" to category.createdAt,
        "updatedAt" to category.updatedAt
    )

    suspend fun customerToCloud(customer: CustomerEntity): Map<String, Any?> = mapOf(
        "name" to customer.name,
        "phoneNumber" to customer.phoneNumber,
        "address" to customer.address,
        "notes" to customer.notes,
        "isSynced" to true,
        "firebaseId" to customer.firebaseId,
        "createdAt" to customer.createdAt,
        "updatedAt" to customer.updatedAt
    )

    suspend fun supplierToCloud(supplier: SupplierEntity): Map<String, Any?> = mapOf(
        "name" to supplier.name,
        "phoneNumber" to supplier.phoneNumber,
        "address" to supplier.address,
        "notes" to supplier.notes,
        "latitude" to supplier.latitude,
        "longitude" to supplier.longitude,
        "isSynced" to true,
        "firebaseId" to supplier.firebaseId,
        "createdAt" to supplier.createdAt,
        "updatedAt" to supplier.updatedAt
    )

    suspend fun productToCloud(product: ProductEntity): Map<String, Any?> {
        val categoryFirebaseId = categoryDao.getCategoryById(product.categoryId)
            ?.firebaseId
            .requireReference("category", product.categoryId)
        return mapOf(
            "categoryFirebaseId" to categoryFirebaseId,
            "name" to product.name,
            "buyingPrice" to product.buyingPrice,
            "sellingPrice" to product.sellingPrice,
            "stockQuantity" to product.stockQuantity,
            "unit" to product.unit,
            "lowStockLimit" to product.lowStockLimit,
            "photoPath" to product.photoPath,
            "notes" to product.notes,
            "isActive" to product.isActive,
            "isSynced" to true,
            "firebaseId" to product.firebaseId,
            "createdAt" to product.createdAt,
            "updatedAt" to product.updatedAt
        )
    }

    suspend fun purchaseToCloud(purchase: PurchaseEntity): Map<String, Any?> {
        val supplierFirebaseId = supplierDao.getSupplierById(purchase.supplierId)
            ?.firebaseId
            .requireReference("supplier", purchase.supplierId)
        val creatorFirebaseId = userDao.getUserById(purchase.createdBy)
            ?.stableFirebaseId()
            .requireReference("purchase creator", purchase.createdBy)
        return mapOf(
            "supplierFirebaseId" to supplierFirebaseId,
            "createdByFirebaseId" to creatorFirebaseId,
            "date" to purchase.date,
            "totalCost" to purchase.totalCost,
            "status" to purchase.status,
            "isSynced" to true,
            "firebaseId" to purchase.firebaseId,
            "createdAt" to purchase.createdAt,
            "updatedAt" to purchase.updatedAt
        )
    }

    suspend fun purchaseItemToCloud(item: PurchaseItemEntity): Map<String, Any?> {
        val purchaseFirebaseId = purchaseDao.getPurchaseById(item.purchaseId)
            ?.firebaseId
            .requireReference("purchase", item.purchaseId)
        val productFirebaseId = productDao.getProductById(item.productId)
            ?.firebaseId
            .requireReference("product", item.productId)
        val batchFirebaseId = item.batchId?.let { batchId ->
            inventoryBatchDao.getBatchById(batchId)
                ?.firebaseId
                .requireReference("inventory batch", batchId)
        }
        return mapOf(
            "purchaseFirebaseId" to purchaseFirebaseId,
            "productFirebaseId" to productFirebaseId,
            "batchFirebaseId" to batchFirebaseId,
            "quantity" to item.quantity,
            "buyingPrice" to item.buyingPrice,
            "sellingPrice" to item.sellingPrice,
            "subtotal" to item.subtotal,
            "isSynced" to true,
            "firebaseId" to item.firebaseId,
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt
        )
    }

    suspend fun saleToCloud(sale: SaleEntity): Map<String, Any?> {
        val cashierFirebaseId = userDao.getUserById(sale.cashierId)
            ?.stableFirebaseId()
            .requireReference("cashier", sale.cashierId)
        val customerFirebaseId = sale.customerId?.let { customerId ->
            customerDao.getCustomerById(customerId)
                ?.firebaseId
                .requireReference("customer", customerId)
        }
        return mapOf(
            "customerFirebaseId" to customerFirebaseId,
            "cashierFirebaseId" to cashierFirebaseId,
            "date" to sale.date,
            "totalAmount" to sale.totalAmount,
            "paymentMethod" to sale.paymentMethod,
            "status" to sale.status,
            "isSynced" to true,
            "firebaseId" to sale.firebaseId,
            "createdAt" to sale.createdAt,
            "updatedAt" to sale.updatedAt
        )
    }

    suspend fun saleItemToCloud(item: SaleItemEntity): Map<String, Any?> {
        val saleFirebaseId = saleDao.getSaleById(item.saleId)
            ?.firebaseId
            .requireReference("sale", item.saleId)
        val productFirebaseId = productDao.getProductById(item.productId)
            ?.firebaseId
            .requireReference("product", item.productId)
        val batchFirebaseId = item.batchId?.let { batchId ->
            inventoryBatchDao.getBatchById(batchId)
                ?.firebaseId
                .requireReference("inventory batch", batchId)
        }
        return mapOf(
            "saleFirebaseId" to saleFirebaseId,
            "productFirebaseId" to productFirebaseId,
            "batchFirebaseId" to batchFirebaseId,
            "quantity" to item.quantity,
            "sellingPrice" to item.sellingPrice,
            "unitCost" to item.unitCost,
            "subtotal" to item.subtotal,
            "isSynced" to true,
            "firebaseId" to item.firebaseId,
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt
        )
    }

    suspend fun inventoryBatchToCloud(batch: InventoryBatchEntity): Map<String, Any?> {
        val productFirebaseId = productDao.getProductById(batch.productId)
            ?.firebaseId
            .requireReference("product", batch.productId)
        return mapOf(
            "productFirebaseId" to productFirebaseId,
            "originalQuantity" to batch.originalQuantity,
            "remainingQuantity" to batch.remainingQuantity,
            "unitCost" to batch.unitCost,
            "sellingPrice" to batch.sellingPrice,
            "unitCostCents" to batch.unitCostCents,
            "sellingPriceCents" to batch.sellingPriceCents,
            "receivedAt" to batch.receivedAt,
            "isSynced" to true,
            "firebaseId" to batch.firebaseId,
            "createdAt" to batch.createdAt,
            "updatedAt" to batch.updatedAt
        )
    }

    suspend fun stockAdjustmentToCloud(adjustment: StockAdjustmentEntity): Map<String, Any?> {
        val productFirebaseId = productDao.getProductById(adjustment.productId)
            ?.firebaseId
            .requireReference("product", adjustment.productId)
        val batchFirebaseId = inventoryBatchDao.getBatchById(adjustment.batchId)
            ?.firebaseId
            .requireReference("inventory batch", adjustment.batchId)
        val adjustedByFirebaseId = userDao.getUserById(adjustment.adjustedBy)
            ?.stableFirebaseId()
            .requireReference("adjusting user", adjustment.adjustedBy)
        return mapOf(
            "productFirebaseId" to productFirebaseId,
            "batchFirebaseId" to batchFirebaseId,
            "adjustedByFirebaseId" to adjustedByFirebaseId,
            "adjustmentType" to adjustment.adjustmentType,
            "quantity" to adjustment.quantity,
            "reason" to adjustment.reason,
            "notes" to adjustment.notes,
            "resultingBatchQuantity" to adjustment.resultingBatchQuantity,
            "resultingProductQuantity" to adjustment.resultingProductQuantity,
            "isSynced" to true,
            "firebaseId" to adjustment.firebaseId,
            "createdAt" to adjustment.createdAt,
            "updatedAt" to adjustment.updatedAt
        )
    }

    suspend fun userFromCloud(document: DocumentSnapshot): UserEntity? {
        val cloud = document.toObject(UserEntity::class.java) ?: return null
        val existing = userDao.getUserByFirebaseUid(document.id)
        return cloud.copy(
            id = existing?.id ?: 0,
            firebaseAuthUid = cloud.firebaseAuthUid.ifBlank { document.id },
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun categoryFromCloud(document: DocumentSnapshot): CategoryEntity? {
        val cloud = document.toObject(CategoryEntity::class.java) ?: return null
        return cloud.copy(
            id = categoryDao.getCategoryByFirebaseId(document.id)?.id ?: 0,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun customerFromCloud(document: DocumentSnapshot): CustomerEntity? {
        val cloud = document.toObject(CustomerEntity::class.java) ?: return null
        return cloud.copy(
            id = customerDao.getCustomerByFirebaseId(document.id)?.id ?: 0,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun supplierFromCloud(document: DocumentSnapshot): SupplierEntity? {
        val cloud = document.toObject(SupplierEntity::class.java) ?: return null
        return cloud.copy(
            id = supplierDao.getSupplierByFirebaseId(document.id)?.id ?: 0,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun productFromCloud(document: DocumentSnapshot): ProductEntity? {
        val cloud = document.toObject(ProductEntity::class.java) ?: return null
        val categoryFirebaseId = document.requireReference(FIELD_CATEGORY_FIREBASE_ID)
        val category = categoryDao.getCategoryByFirebaseId(categoryFirebaseId)
            ?: missingParent("category", categoryFirebaseId, "product", document.id)
        return cloud.copy(
            id = productDao.getProductByFirebaseId(document.id)?.id ?: 0,
            categoryId = category.id,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun purchaseFromCloud(document: DocumentSnapshot): PurchaseEntity? {
        val cloud = document.toObject(PurchaseEntity::class.java) ?: return null
        val supplierFirebaseId = document.requireReference(FIELD_SUPPLIER_FIREBASE_ID)
        val creatorFirebaseId = document.requireReference(FIELD_CREATED_BY_FIREBASE_ID)
        val supplier = supplierDao.getSupplierByFirebaseId(supplierFirebaseId)
            ?: missingParent("supplier", supplierFirebaseId, "purchase", document.id)
        val creator = userDao.getUserByFirebaseUid(creatorFirebaseId)
            ?: missingParent("user", creatorFirebaseId, "purchase", document.id)
        return cloud.copy(
            id = purchaseDao.getPurchaseByFirebaseId(document.id)?.id ?: 0,
            supplierId = supplier.id,
            createdBy = creator.id,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun purchaseItemFromCloud(document: DocumentSnapshot): PurchaseItemEntity? {
        val cloud = document.toObject(PurchaseItemEntity::class.java) ?: return null
        val purchaseFirebaseId = document.requireReference(FIELD_PURCHASE_FIREBASE_ID)
        val productFirebaseId = document.requireReference(FIELD_PRODUCT_FIREBASE_ID)
        val purchase = purchaseDao.getPurchaseByFirebaseId(purchaseFirebaseId)
            ?: missingParent("purchase", purchaseFirebaseId, "purchase item", document.id)
        val product = productDao.getProductByFirebaseId(productFirebaseId)
            ?: missingParent("product", productFirebaseId, "purchase item", document.id)
        val batchId = document.getString(FIELD_BATCH_FIREBASE_ID)?.let { batchFirebaseId ->
            inventoryBatchDao.getBatchByFirebaseId(batchFirebaseId)?.id
                ?: missingParent("inventory batch", batchFirebaseId, "purchase item", document.id)
        }
        return cloud.copy(
            id = purchaseItemDao.getPurchaseItemByFirebaseId(document.id)?.id ?: 0,
            purchaseId = purchase.id,
            productId = product.id,
            batchId = batchId,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun saleFromCloud(document: DocumentSnapshot): SaleEntity? {
        val cloud = document.toObject(SaleEntity::class.java) ?: return null
        val cashierFirebaseId = document.requireReference(FIELD_CASHIER_FIREBASE_ID)
        val cashier = userDao.getUserByFirebaseUid(cashierFirebaseId)
            ?: missingParent("user", cashierFirebaseId, "sale", document.id)
        val customerId = document.getString(FIELD_CUSTOMER_FIREBASE_ID)?.let { customerFirebaseId ->
            customerDao.getCustomerByFirebaseId(customerFirebaseId)?.id
                ?: missingParent("customer", customerFirebaseId, "sale", document.id)
        }
        return cloud.copy(
            id = saleDao.getSaleByFirebaseId(document.id)?.id ?: 0,
            customerId = customerId,
            cashierId = cashier.id,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun saleItemFromCloud(document: DocumentSnapshot): SaleItemEntity? {
        val cloud = document.toObject(SaleItemEntity::class.java) ?: return null
        val saleFirebaseId = document.requireReference(FIELD_SALE_FIREBASE_ID)
        val productFirebaseId = document.requireReference(FIELD_PRODUCT_FIREBASE_ID)
        val sale = saleDao.getSaleByFirebaseId(saleFirebaseId)
            ?: missingParent("sale", saleFirebaseId, "sale item", document.id)
        val product = productDao.getProductByFirebaseId(productFirebaseId)
            ?: missingParent("product", productFirebaseId, "sale item", document.id)
        val batchId = document.getString(FIELD_BATCH_FIREBASE_ID)?.let { batchFirebaseId ->
            inventoryBatchDao.getBatchByFirebaseId(batchFirebaseId)?.id
                ?: missingParent("inventory batch", batchFirebaseId, "sale item", document.id)
        }
        return cloud.copy(
            id = saleItemDao.getSaleItemByFirebaseId(document.id)?.id ?: 0,
            saleId = sale.id,
            productId = product.id,
            batchId = batchId,
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun inventoryBatchFromCloud(document: DocumentSnapshot): InventoryBatchEntity? {
        val cloud = document.toObject(InventoryBatchEntity::class.java) ?: return null
        val productFirebaseId = document.requireReference(FIELD_PRODUCT_FIREBASE_ID)
        val product = productDao.getProductByFirebaseId(productFirebaseId)
            ?: missingParent("product", productFirebaseId, "inventory batch", document.id)
        val existing = inventoryBatchDao.getBatchByFirebaseId(document.id)
        return cloud.copy(
            id = existing?.id ?: 0,
            productId = product.id,
            purchaseItemId = existing?.purchaseItemId,
            unitCostCents = priceToCents(cloud.unitCost),
            sellingPriceCents = priceToCents(cloud.sellingPrice),
            firebaseId = document.id,
            isSynced = true
        )
    }

    suspend fun stockAdjustmentFromCloud(document: DocumentSnapshot): StockAdjustmentEntity? {
        val cloud = document.toObject(StockAdjustmentEntity::class.java) ?: return null
        val productFirebaseId = document.requireReference(FIELD_PRODUCT_FIREBASE_ID)
        val batchFirebaseId = document.requireReference(FIELD_BATCH_FIREBASE_ID)
        val adjustedByFirebaseId = document.requireReference(FIELD_ADJUSTED_BY_FIREBASE_ID)
        val product = productDao.getProductByFirebaseId(productFirebaseId)
            ?: missingParent("product", productFirebaseId, "stock adjustment", document.id)
        val batch = inventoryBatchDao.getBatchByFirebaseId(batchFirebaseId)
            ?: missingParent("inventory batch", batchFirebaseId, "stock adjustment", document.id)
        val adjustedBy = userDao.getUserByFirebaseUid(adjustedByFirebaseId)
            ?: missingParent("user", adjustedByFirebaseId, "stock adjustment", document.id)
        return cloud.copy(
            id = stockAdjustmentDao.getByFirebaseId(document.id)?.id ?: 0,
            productId = product.id,
            batchId = batch.id,
            adjustedBy = adjustedBy.id,
            firebaseId = document.id,
            isSynced = true
        )
    }

    private fun UserEntity.stableFirebaseId(): String? =
        firebaseId?.takeIf(String::isNotBlank)
            ?: firebaseAuthUid.takeIf(String::isNotBlank)

    private fun String?.requireReference(parentType: String, localId: Int): String =
        this?.takeIf(String::isNotBlank)
            ?: error("Local $parentType ID $localId is not synced to Firebase")

    private fun DocumentSnapshot.requireReference(field: String): String =
        getString(field)?.takeIf(String::isNotBlank)
            ?: error(
                "$referenceType document $id uses legacy device-local IDs and is missing $field"
            )

    private val DocumentSnapshot.referenceType: String
        get() = reference.parent.id

    private fun missingParent(
        parentType: String,
        firebaseId: String,
        childType: String,
        childFirebaseId: String
    ): Nothing = error(
        "Missing $parentType $firebaseId required by $childType $childFirebaseId"
    )

    private companion object {
        private const val FIELD_CATEGORY_FIREBASE_ID = "categoryFirebaseId"
        private const val FIELD_CUSTOMER_FIREBASE_ID = "customerFirebaseId"
        private const val FIELD_CASHIER_FIREBASE_ID = "cashierFirebaseId"
        private const val FIELD_SUPPLIER_FIREBASE_ID = "supplierFirebaseId"
        private const val FIELD_CREATED_BY_FIREBASE_ID = "createdByFirebaseId"
        private const val FIELD_PRODUCT_FIREBASE_ID = "productFirebaseId"
        private const val FIELD_BATCH_FIREBASE_ID = "batchFirebaseId"
        private const val FIELD_ADJUSTED_BY_FIREBASE_ID = "adjustedByFirebaseId"
        private const val FIELD_PURCHASE_ITEM_FIREBASE_ID = "purchaseItemFirebaseId"
        private const val FIELD_PURCHASE_FIREBASE_ID = "purchaseFirebaseId"
        private const val FIELD_SALE_FIREBASE_ID = "saleFirebaseId"
    }
}
