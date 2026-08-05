package com.foliora.pos.ui.receipt

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shows the saved receipt before leaving checkout, and also supports sales-history reprints. */
@Composable
fun ReceiptDialog(
    receipt: ReceiptData,
    onDismiss: () -> Unit,
    dismissLabel: String = "Done",
    isLoadingItems: Boolean = false
) {
    val context = LocalContext.current
    val formattedDate = remember(receipt.date) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(receipt.date))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Receipt #${receipt.saleId}",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = receipt.shopName.ifBlank { "Foliora" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        if (receipt.shopAddress.isNotBlank()) {
                            Text(receipt.shopAddress, textAlign = TextAlign.Center)
                        }
                        if (receipt.shopPhone.isNotBlank()) {
                            Text("Tel: ${receipt.shopPhone}", textAlign = TextAlign.Center)
                        }
                    }
                }

                item { HorizontalDivider() }

                item {
                    ReceiptDetailRow("Date", formattedDate)
                    ReceiptDetailRow("Cashier", receipt.cashierName.ifBlank { "Unknown" })
                    ReceiptDetailRow("Customer", receipt.customerName?.ifBlank { null } ?: "Walk-in")
                    ReceiptDetailRow("Payment", receipt.paymentMethod)
                    ReceiptDetailRow("Status", receipt.status)
                }

                item { HorizontalDivider() }

                if (isLoadingItems) {
                    item {
                        Text(
                            text = "Loading receipt items...",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    itemsIndexed(
                        items = receipt.items,
                        key = { index, item -> "${item.productName}-$index" }
                    ) { _, item ->
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(item.productName, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${formatQuantity(item.quantity)} × ${formatMoney(item.sellingPrice)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(formatMoney(item.subtotal), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatMoney(receipt.totalAmount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (receipt.receiptMessage.isNotBlank()) {
                    item {
                        Text(
                            text = receipt.receiptMessage,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoadingItems,
                onClick = {
                    runCatching { ReceiptPrinter.print(context, receipt) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                it.localizedMessage ?: "Unable to open print preview",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
            ) {
                Text("Print / Save PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissLabel)
            }
        }
    )
}

@Composable
private fun ReceiptDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.35f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.65f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End
        )
    }
}

internal fun formatMoney(value: Double): String =
    String.format(Locale.getDefault(), "Rs. %.2f", value)

internal fun formatQuantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(Locale.getDefault(), "%.2f", value).trimEnd('0').trimEnd('.')
