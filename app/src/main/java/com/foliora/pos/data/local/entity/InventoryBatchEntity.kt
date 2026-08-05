package com.foliora.pos.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import kotlin.math.roundToLong

/** Normalizes a currency value for reliable equality checks. */
fun priceToCents(price: Double): Long = (price * 100.0).roundToLong()

/** A separately priced quantity of a product received in one restock. */
@Entity(
    tableName = "inventory_batches",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["purchaseItemId"]),
        Index(value = ["productId", "unitCostCents", "sellingPriceCents"])
    ]
)
data class InventoryBatchEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val productId: Int = 0,
    val purchaseItemId: Int? = null,
    val originalQuantity: Double = 0.0,
    val remainingQuantity: Double = 0.0,
    val unitCost: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val sellingPrice: Double = 0.0,
    @ColumnInfo(defaultValue = "0")
    val unitCostCents: Long = priceToCents(unitCost),
    @ColumnInfo(defaultValue = "0")
    val sellingPriceCents: Long = priceToCents(sellingPrice),
    val receivedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val firebaseId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
