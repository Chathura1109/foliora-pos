package com.foliora.pos.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.foliora.pos.data.local.entity.PurchaseEntity
import com.foliora.pos.data.local.entity.PurchaseItemEntity

/**
 * Represents a Purchase together with all its line items.
 *
 * Same pattern as [SaleWithItems] — Room automatically joins
 * PurchaseEntity with its PurchaseItemEntities using @Relation.
 */
data class PurchaseWithItems(
    @Embedded val purchase: PurchaseEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "purchaseId"
    )
    val items: List<PurchaseItemEntity>
)
