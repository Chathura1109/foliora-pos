package com.foliora.pos.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import com.foliora.pos.data.local.entity.SaleEntity
import com.foliora.pos.data.local.entity.SaleItemEntity

/**
 * Represents a Sale together with all its line items.
 *
 * Room's @Relation annotation tells Room how to join the two tables automatically.
 * @Embedded means Room maps the parent entity's columns directly into this class.
 * @Relation tells Room: "For each SaleEntity, find all SaleItemEntities where
 * saleId matches the parent's id."
 *
 * This avoids writing complex JOIN queries manually — Room does it for you.
 */
data class SaleWithItems(
    @Embedded val sale: SaleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "saleId"
    )
    val items: List<SaleItemEntity>
)
