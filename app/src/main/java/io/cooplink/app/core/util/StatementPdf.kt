package io.cooplink.app.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

private const val PAGE_WIDTH  = 595  // A4 @ 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN      = 40f
private const val ROW_HEIGHT  = 20f
private const val ROWS_PER_PAGE = 34

data class StatementRow(val date: String, val type: String, val amount: String)

/** Builds a simple PDF statement, saves it to Downloads, and opens it.
 * Pass [launch] (e.g. an ActivityResultLauncher::launch) to open the viewer
 * through a launcher that gives a "we've returned" callback instead of a
 * plain fire-and-forget startActivity — needed so an inactivity-timer pause
 * around this call has somewhere to resume from. */
fun generateAndOpenStatementPdf(
    context: Context,
    memberName: String?,
    rows: List<StatementRow>,
    launch: (Intent) -> Unit = { context.startActivity(it) },
) {
    val document = PdfDocument()
    val titlePaint = Paint().apply { textSize = 16f; isFakeBoldText = true }
    val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
    val bodyPaint = Paint().apply { textSize = 10f }

    val pages = rows.chunked(ROWS_PER_PAGE).ifEmpty { listOf(emptyList()) }

    pages.forEachIndexed { pageIndex, pageRows ->
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
        val page = document.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        var y = MARGIN

        if (pageIndex == 0) {
            canvas.drawText("CoopLink Statement", MARGIN, y, titlePaint)
            y += 24f
            canvas.drawText(memberName ?: "Member", MARGIN, y, bodyPaint)
            y += 16f
            canvas.drawText("Last 90 days", MARGIN, y, bodyPaint)
            y += 24f
        }

        canvas.drawText("Date", MARGIN, y, headerPaint)
        canvas.drawText("Type", MARGIN + 150f, y, headerPaint)
        canvas.drawText("Amount", MARGIN + 350f, y, headerPaint)
        y += ROW_HEIGHT

        pageRows.forEach { row ->
            canvas.drawText(row.date, MARGIN, y, bodyPaint)
            canvas.drawText(row.type, MARGIN + 150f, y, bodyPaint)
            canvas.drawText(row.amount, MARGIN + 350f, y, bodyPaint)
            y += ROW_HEIGHT
        }

        document.finishPage(page)
    }

    val fileName = "CoopLink-Statement-${System.currentTimeMillis()}.pdf"
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        itemUri?.let {
            resolver.openOutputStream(it)?.use { out -> document.writeTo(out) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(it, values, null, null)
        }
        itemUri
    } else {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)
        FileOutputStream(file).use { out -> document.writeTo(out) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    document.close()

    uri?.let {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(it, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launch(intent)
    }
}
