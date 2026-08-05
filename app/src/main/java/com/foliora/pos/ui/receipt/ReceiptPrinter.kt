package com.foliora.pos.ui.receipt

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/** Opens Android's print preview. The same dialog can print to a service or save the receipt as PDF. */
object ReceiptPrinter {
    fun print(context: Context, receipt: ReceiptData) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins(300, 300, 300, 300))
            .build()

        printManager.print(
            "Foliora receipt ${receipt.saleId}",
            ReceiptPrintDocumentAdapter(context.applicationContext, receipt),
            attributes
        )
    }
}

private class ReceiptPrintDocumentAdapter(
    private val context: Context,
    private val receipt: ReceiptData
) : PrintDocumentAdapter() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var attributes: PrintAttributes? = null
    private var pages: List<List<PrintLine>> = emptyList()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }

        attributes = newAttributes
        val document = PrintedPdfDocument(context, newAttributes)
        pages = try {
            val maxCharacters = (document.pageContentRect.width() / APPROXIMATE_CHARACTER_WIDTH)
                .toInt()
                .coerceIn(MINIMUM_LINE_CHARACTERS, MAXIMUM_LINE_CHARACTERS)
            val lines = receipt.toPrintLines(maxCharacters)
            paginate(lines, document.pageContentRect.height().toFloat())
        } finally {
            document.close()
        }

        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("foliora-receipt.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages.size)
                .build(),
            oldAttributes != newAttributes
        )
    }

    override fun onWrite(
        pageRanges: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        val currentAttributes = attributes
        if (currentAttributes == null) {
            callback.onWriteFailed("Print layout is unavailable")
            return
        }

        scope.launch {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return@launch
            }

            val document = PrintedPdfDocument(context, currentAttributes)
            try {
                pages.forEachIndexed { pageIndex, pageLines ->
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return@launch
                    }
                    val page = document.startPage(pageIndex)
                    drawPage(page.canvas, page.info.contentRect, pageLines, pageIndex + 1, pages.size)
                    document.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use(document::writeTo)
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) {
                callback.onWriteFailed(error.localizedMessage ?: "Unable to create receipt PDF")
            } finally {
                document.close()
                destination.close()
            }
        }
    }

    override fun onFinish() {
        scope.cancel()
    }
}

private data class PrintLine(
    val left: String,
    val right: String? = null,
    val centered: Boolean = false,
    val bold: Boolean = false,
    val size: Float = 11f
) {
    val height: Float get() = size + 8f
}

private fun ReceiptData.toPrintLines(maxCharacters: Int): List<PrintLine> = buildList {
    val divider = "-".repeat(maxCharacters)
    val headingCharacters = (maxCharacters * 0.6f).toInt().coerceAtLeast(10)
    wrapForPrint(shopName.ifBlank { "Foliora" }, headingCharacters).forEach {
        add(PrintLine(it, centered = true, bold = true, size = 18f))
    }
    wrapForPrint(shopAddress, maxCharacters).forEach { add(PrintLine(it, centered = true)) }
    wrapForPrint("Tel: $shopPhone".takeIf { shopPhone.isNotBlank() }.orEmpty(), maxCharacters)
        .forEach { add(PrintLine(it, centered = true)) }
    add(PrintLine(divider))
    addResponsivePair("Receipt", "#$saleId", maxCharacters, bold = true)
    addWrappedInfo(
        "Date",
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(date)),
        maxCharacters
    )
    addWrappedInfo("Cashier", cashierName.ifBlank { "Unknown" }, maxCharacters)
    addWrappedInfo("Customer", customerName?.ifBlank { null } ?: "Walk-in", maxCharacters)
    addWrappedInfo("Payment", paymentMethod, maxCharacters)
    addWrappedInfo("Status", status, maxCharacters)
    add(PrintLine(divider))
    add(PrintLine("Item", "Amount", bold = true))
    add(PrintLine(divider))
    items.forEach { item ->
        wrapForPrint(item.productName, maxCharacters).forEachIndexed { index, nameLine ->
            add(PrintLine(nameLine, bold = index == 0))
        }
        addResponsivePair(
            left = "${formatQuantity(item.quantity)} x ${formatMoney(item.sellingPrice)}",
            right = formatMoney(item.subtotal),
            maxCharacters = maxCharacters
        )
    }
    add(PrintLine(divider))
    addResponsivePair("TOTAL", formatMoney(totalAmount), maxCharacters, bold = true, size = 15f)
    add(PrintLine(divider))
    wrapForPrint(receiptMessage, maxCharacters).forEach { add(PrintLine(it, centered = true)) }
}

private fun MutableList<PrintLine>.addWrappedInfo(label: String, value: String, maxCharacters: Int) {
    wrapForPrint("$label: $value", maxCharacters).forEach { add(PrintLine(it)) }
}

private fun MutableList<PrintLine>.addResponsivePair(
    left: String,
    right: String,
    maxCharacters: Int,
    bold: Boolean = false,
    size: Float = 11f
) {
    if (left.length + right.length + 1 <= maxCharacters) {
        add(PrintLine(left = left, right = right, bold = bold, size = size))
    } else {
        wrapForPrint(left, maxCharacters).forEach { add(PrintLine(it, bold = bold, size = size)) }
        add(PrintLine(left = "", right = right, bold = bold, size = size))
    }
}

private fun paginate(lines: List<PrintLine>, pageHeight: Float): List<List<PrintLine>> {
    val availableHeight = max(pageHeight - PAGE_FOOTER_HEIGHT, 100f)
    val result = mutableListOf<MutableList<PrintLine>>()
    var currentPage = mutableListOf<PrintLine>()
    var usedHeight = 0f

    for (line in lines) {
        if (currentPage.isNotEmpty() && usedHeight + line.height > availableHeight) {
            result += currentPage
            currentPage = mutableListOf()
            usedHeight = 0f
        }
        currentPage += line
        usedHeight += line.height
    }
    if (currentPage.isNotEmpty()) result += currentPage
    return result.ifEmpty { listOf(emptyList()) }
}

private fun drawPage(
    canvas: Canvas,
    contentRect: android.graphics.Rect,
    lines: List<PrintLine>,
    pageNumber: Int,
    pageCount: Int
) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    }
    var y = contentRect.top.toFloat()

    lines.forEach { line ->
        paint.textSize = line.size
        paint.typeface = Typeface.create(
            Typeface.MONOSPACE,
            if (line.bold) Typeface.BOLD else Typeface.NORMAL
        )
        y += line.size

        when {
            line.centered -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(line.left, contentRect.exactCenterX(), y, paint)
            }
            line.right != null -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(line.left, contentRect.left.toFloat(), y, paint)
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText(line.right, contentRect.right.toFloat(), y, paint)
            }
            else -> {
                paint.textAlign = Paint.Align.LEFT
                canvas.drawText(line.left, contentRect.left.toFloat(), y, paint)
            }
        }
        y += line.height - line.size
    }

    paint.textSize = 9f
    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(
        "Page $pageNumber of $pageCount",
        contentRect.exactCenterX(),
        contentRect.bottom.toFloat(),
        paint
    )
}

private fun wrapForPrint(value: String, maxLength: Int): List<String> {
    if (value.isBlank()) return emptyList()
    val words = value.trim().split(Regex("\\s+"))
    val result = mutableListOf<String>()
    var current = ""
    words.forEach { word ->
        if (word.length > maxLength) {
            if (current.isNotEmpty()) {
                result += current
                current = ""
            }
            word.chunked(maxLength).forEach(result::add)
        } else if (current.isEmpty()) {
            current = word
        } else if (current.length + word.length + 1 <= maxLength) {
            current += " $word"
        } else {
            result += current
            current = word
        }
    }
    if (current.isNotEmpty()) result += current
    return result
}

private const val PAGE_FOOTER_HEIGHT = 24f
private const val APPROXIMATE_CHARACTER_WIDTH = 6.6f
private const val MINIMUM_LINE_CHARACTERS = 16
private const val MAXIMUM_LINE_CHARACTERS = 60
