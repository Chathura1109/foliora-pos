package com.foliora.pos.ui.receipt

/** Immutable sale snapshot used by both receipt preview and printing. */
data class ReceiptData(
    val saleId: Int,
    val date: Long,
    val shopName: String,
    val shopAddress: String,
    val shopPhone: String,
    val cashierName: String,
    val customerName: String?,
    val paymentMethod: String,
    val status: String,
    val items: List<ReceiptLineItem>,
    val totalAmount: Double,
    val receiptMessage: String
)

/** Customer-facing values for one saved sale line. Internal cost is intentionally excluded. */
data class ReceiptLineItem(
    val productName: String,
    val quantity: Double,
    val sellingPrice: Double,
    val subtotal: Double
)
