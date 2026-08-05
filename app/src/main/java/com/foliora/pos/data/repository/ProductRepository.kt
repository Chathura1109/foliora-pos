package com.foliora.pos.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.foliora.pos.data.local.FolioraDatabase
import com.foliora.pos.data.local.dao.InventoryBatchDao
import com.foliora.pos.data.local.dao.ProductDao
import com.foliora.pos.data.local.dao.SaleItemDao
import com.foliora.pos.data.local.entity.InventoryBatchEntity
import com.foliora.pos.data.local.entity.ProductEntity
import com.foliora.pos.data.local.entity.priceToCents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.UUID
import javax.inject.Inject

/**
 * Repository class for managing inventory products in Foliora POS.
 * Handles product creation, inventory querying, stock alerts, search filtering, and stock adjustments via [ProductDao].
 *
 * @property productDao Data access object for product inventory operations.
 */
class ProductRepository @Inject constructor(
    private val database: FolioraDatabase,
    private val productDao: ProductDao,
    private val inventoryBatchDao: InventoryBatchDao,
    private val saleItemDao: SaleItemDao
) {

    /**
     * Inserts a product entity into the database with an updated timestamp.
     */
    suspend fun insertProduct(product: ProductEntity): Long {
        return productDao.insertProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Creates a local product and its opening batch as one atomic Room operation. */
    suspend fun createProductWithOpeningBatch(product: ProductEntity): Long =
        database.withTransaction {
            require(product.stockQuantity.isFinite() && product.stockQuantity >= 0) {
                "Initial stock quantity is invalid"
            }
            val now = System.currentTimeMillis()
            val productId = productDao.insertProduct(
                product.copy(isSynced = false, updatedAt = now)
            )
            if (product.stockQuantity > 0) {
                inventoryBatchDao.insertBatch(
                    InventoryBatchEntity(
                        productId = productId.toInt(),
                        originalQuantity = product.stockQuantity,
                        remainingQuantity = product.stockQuantity,
                        unitCost = product.buyingPrice,
                        sellingPrice = product.sellingPrice,
                        unitCostCents = priceToCents(product.buyingPrice),
                        sellingPriceCents = priceToCents(product.sellingPrice),
                        receivedAt = now,
                        isSynced = false,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
            productId
        }

    /**
     * Updates an existing product entity with an updated timestamp.
     */
    suspend fun updateProduct(product: ProductEntity) {
        productDao.updateProduct(product.copy(updatedAt = System.currentTimeMillis()))
    }

    /** Updates descriptive product fields while keeping batch-controlled stock unchanged. */
    suspend fun updateProductDetails(product: ProductEntity) {
        database.withTransaction {
            val current = requireNotNull(productDao.getProductById(product.id)) {
                "Product no longer exists"
            }
            productDao.updateProduct(
                product.copy(
                    stockQuantity = current.stockQuantity,
                    isSynced = false,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /** Returns false after this batch has appeared in any completed sale. */
    suspend fun canEditBatchUnitCost(batchId: Int): Boolean =
        !saleItemDao.hasItemsForBatch(batchId)

    /**
     * Changes prices for future sales without touching historical sale-item snapshots.
     * Cost is immutable after the batch has been used in a sale.
     */
    suspend fun updateBatchPrices(
        productId: Int,
        batchId: Int,
        unitCost: Double,
        sellingPrice: Double
    ) {
        require(unitCost.isFinite() && unitCost >= 0) { "Batch cost must be a valid non-negative number" }
        require(sellingPrice.isFinite() && sellingPrice >= 0) {
            "Batch selling price must be a valid non-negative number"
        }

        database.withTransaction {
            val product = requireNotNull(productDao.getProductById(productId)) {
                "Product no longer exists"
            }
            val batch = requireNotNull(inventoryBatchDao.getBatchById(batchId)) {
                "Stock batch no longer exists"
            }
            require(batch.productId == product.id) { "Selected batch does not belong to this product" }
            require(batch.remainingQuantity > 0) { "Exhausted batches cannot be edited" }

            val unitCostCents = priceToCents(unitCost)
            val sellingPriceCents = priceToCents(sellingPrice)
            val costChanged = unitCostCents != batch.unitCostCents
            if (costChanged) {
                require(!saleItemDao.hasItemsForBatch(batch.id)) {
                    "Batch cost is locked because stock from this batch has already been sold"
                }
            }

            if (!costChanged && sellingPriceCents == batch.sellingPriceCents) return@withTransaction

            val now = System.currentTimeMillis()
            inventoryBatchDao.updateBatch(
                batch.copy(
                    unitCost = unitCost,
                    sellingPrice = sellingPrice,
                    unitCostCents = unitCostCents,
                    sellingPriceCents = sellingPriceCents,
                    isSynced = false,
                    updatedAt = now
                )
            )

            // Keep the legacy product defaults aligned with its newest batch for future restocks.
            val newestBatch = inventoryBatchDao.getBatchesForProduct(product.id).firstOrNull()
            if (newestBatch != null &&
                (priceToCents(product.buyingPrice) != newestBatch.unitCostCents ||
                    priceToCents(product.sellingPrice) != newestBatch.sellingPriceCents)
            ) {
                productDao.updateProduct(
                    product.copy(
                        buyingPrice = newestBatch.unitCost,
                        sellingPrice = newestBatch.sellingPrice,
                        isSynced = false,
                        updatedAt = now
                    )
                )
            }
        }
    }

    /**
     * Deletes a product entity from the database.
     */
    suspend fun deleteProduct(product: ProductEntity) {
        try {
            productDao.deleteProduct(product)
        } catch (e: SQLiteConstraintException) {
            throw IllegalStateException(
                "${product.name} cannot be deleted because it is used in sales or purchase history",
                e
            )
        }
    }

    /**
     * Retrieves a single product entity by its primary key ID.
     */
    suspend fun getProductById(id: Int): ProductEntity? {
        return productDao.getProductById(id)
    }

    /**
     * Observes all products in the database ordered by name.
     */
    fun getAllProducts(): Flow<List<ProductEntity>> {
        return productDao.getAllProducts()
    }

    /**
     * Observes active products available for sale.
     */
    fun getActiveProducts(): Flow<List<ProductEntity>> {
        return productDao.getActiveProducts()
    }

    /**
     * Observes products belonging to a specific category ID.
     */
    fun getProductsByCategory(categoryId: Int): Flow<List<ProductEntity>> {
        return productDao.getProductsByCategory(categoryId)
    }

    /**
     * Observes active products where stock levels are at or below the low stock threshold.
     */
    fun getLowStockProducts(): Flow<List<ProductEntity>> {
        return productDao.getLowStockProducts()
    }

    /**
     * Searches products matching the given query string.
     */
    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return productDao.searchProducts(query)
    }

    /**
     * Directly updates the stock quantity of a specified product.
     */
    suspend fun updateStockQuantity(productId: Int, newQuantity: Double) {
        productDao.updateStockQuantity(productId, newQuantity, System.currentTimeMillis())
    }

    /**
     * Retrieves product records that require remote cloud synchronization.
     */
    suspend fun getUnsyncedProducts(): List<ProductEntity> {
        return productDao.getUnsyncedProducts()
    }

    fun observePendingSyncCount(): Flow<Int> = productDao.observePendingSyncCount()

    /**
     * Uploads a device-local product photo to Cloudinary and returns the Firestore-safe product copy.
     * The Room entity keeps its local path so the photo remains available offline.
     */
    suspend fun prepareProductForCloud(product: ProductEntity): ProductEntity {
        val photoPath = product.photoPath?.takeIf(String::isNotBlank) ?: return product
        if (photoPath.startsWith("https://") || photoPath.startsWith("http://")) return product

        val photoFile = File(photoPath)
        if (!photoFile.isFile) return product.copy(photoPath = null)

        require(photoFile.length() <= MAX_CLOUDINARY_IMAGE_BYTES) {
            "Product photo must be smaller than 10 MB"
        }
        val downloadUrl = uploadProductPhotoToCloudinary(photoFile)
        return product.copy(photoPath = downloadUrl)
    }

    private suspend fun uploadProductPhotoToCloudinary(photoFile: File): String =
        withContext(Dispatchers.IO) {
            val boundary = "FolioraBoundary${UUID.randomUUID()}"
            val connection = URL(CLOUDINARY_UPLOAD_URL).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.setChunkedStreamingMode(0)
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                DataOutputStream(connection.outputStream).use { output ->
                    output.writeMultipartText(boundary, "upload_preset", CLOUDINARY_UPLOAD_PRESET)
                    output.writeBytes("--$boundary\r\n")
                    output.writeBytes(
                        "Content-Disposition: form-data; name=\"file\"; filename=\"${photoFile.name}\"\r\n"
                    )
                    val contentType = URLConnection.guessContentTypeFromName(photoFile.name) ?: "image/jpeg"
                    output.writeBytes("Content-Type: $contentType\r\n\r\n")
                    photoFile.inputStream().use { input -> input.copyTo(output) }
                    output.writeBytes("\r\n--$boundary--\r\n")
                }

                val responseCode = connection.responseCode
                val responseText = (if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                })?.bufferedReader()?.use { it.readText() }.orEmpty()

                if (responseCode !in 200..299) {
                    val cloudinaryMessage = runCatching {
                        JSONObject(responseText).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.takeIf(String::isNotBlank)
                    throw IOException(
                        cloudinaryMessage ?: "Cloudinary image upload failed with HTTP $responseCode"
                    )
                }

                JSONObject(responseText).optString("secure_url").takeIf(String::isNotBlank)
                    ?: throw IOException("Cloudinary did not return an image URL")
            } finally {
                connection.disconnect()
            }
        }

    private fun DataOutputStream.writeMultipartText(boundary: String, name: String, value: String) {
        writeBytes("--$boundary\r\n")
        writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
        writeBytes("$value\r\n")
    }

    private companion object {
        private const val CLOUDINARY_CLOUD_NAME = "sioylmkz"
        private const val CLOUDINARY_UPLOAD_PRESET = "foliora_product_images"
        private const val CLOUDINARY_UPLOAD_URL =
            "https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/image/upload"
        private const val MAX_CLOUDINARY_IMAGE_BYTES = 10L * 1024L * 1024L
    }
}
