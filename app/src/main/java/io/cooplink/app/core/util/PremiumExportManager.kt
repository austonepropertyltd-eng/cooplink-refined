package io.cooplink.app.core.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cooplink.app.R
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ── PDF Export ────────────────────────────────────────────────────────
    fun exportToPdf(
        title: String,
        cooperativeName: String,
        headers: List<String>,
        rows: List<List<String>>,
        summary: Map<String, String> = emptyMap(),
    ): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(842, 595, 1).create() // A4 landscape
        val page   = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint  = Paint().apply { isAntiAlias = true }

        // ── Header bar ──────────────────────────────────────────────────
        paint.color = android.graphics.Color.parseColor("#0A1628")
        canvas.drawRect(0f, 0f, 842f, 80f, paint)

        runCatching {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.cooplink_logo)
            val scaled = Bitmap.createScaledBitmap(logo, 140, 56, true)
            canvas.drawBitmap(scaled, 20f, 12f, null)
        }

        paint.color = android.graphics.Color.parseColor("#FCB424")
        paint.textSize = 22f
        paint.isFakeBoldText = true
        canvas.drawText(title, 200f, 38f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText(cooperativeName, 200f, 62f, paint)

        val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.US).format(java.util.Date())
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Generated: $dateStr", 822f, 50f, paint)
        paint.textAlign = Paint.Align.LEFT

        // ── Summary cards ───────────────────────────────────────────────
        if (summary.isNotEmpty()) {
            var sx = 20f
            summary.entries.take(5).forEach { (k, v) ->
                paint.color = android.graphics.Color.parseColor("#112240")
                canvas.drawRoundRect(RectF(sx, 90f, sx + 150f, 140f), 8f, 8f, paint)
                paint.color = android.graphics.Color.parseColor("#FCB424")
                paint.textSize = 18f
                paint.isFakeBoldText = true
                canvas.drawText(v, sx + 10f, 120f, paint)
                paint.color = android.graphics.Color.parseColor("#AAAAAA")
                paint.textSize = 11f
                paint.isFakeBoldText = false
                canvas.drawText(k, sx + 10f, 135f, paint)
                sx += 162f
            }
        }

        // ── Table header ─────────────────────────────────────────────────
        val tableTop = if (summary.isNotEmpty()) 155f else 95f
        val colWidth = 802f / headers.size.coerceAtLeast(1)

        paint.color = android.graphics.Color.parseColor("#243C54")
        canvas.drawRect(20f, tableTop, 822f, tableTop + 30f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 12f
        paint.isFakeBoldText = true
        headers.forEachIndexed { i, h -> canvas.drawText(h, 25f + i * colWidth, tableTop + 20f, paint) }

        // ── Table rows ────────────────────────────────────────────────────
        paint.isFakeBoldText = false
        rows.take(18).forEachIndexed { ri, row ->
            val ry = tableTop + 30f + ri * 22f
            if (ri % 2 == 0) {
                paint.color = android.graphics.Color.parseColor("#F8FAFC")
                canvas.drawRect(20f, ry, 822f, ry + 22f, paint)
            }
            paint.color = android.graphics.Color.parseColor("#1A202C")
            paint.textSize = 11f
            row.take(headers.size).forEachIndexed { ci, cell -> canvas.drawText(cell, 25f + ci * colWidth, ry + 15f, paint) }
        }

        paint.color = android.graphics.Color.parseColor("#718096")
        paint.textSize = 10f
        canvas.drawText(
            "Powered by CoopLink — cooplink.io  |  Confidential — For internal use only",
            20f, 578f, paint,
        )

        document.finishPage(page)

        val fileName = "${title.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())}.pdf"
        val uri = writePdfToDownloads(document, fileName)
        document.close()
        return uri
    }

    private fun writePdfToDownloads(document: PdfDocument, fileName: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
    }

    // ── CSV Export ────────────────────────────────────────────────────────
    fun exportToCsv(title: String, headers: List<String>, rows: List<List<String>>): Uri? {
        val fileName = "${title.replace(" ", "_")}_${SimpleDateFormat("yyyyMMdd", Locale.US).format(java.util.Date())}.csv"
        val csv = buildString {
            appendLine(headers.joinToString(",") { "\"$it\"" })
            rows.forEach { row -> appendLine(row.joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }) }
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/csv")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val itemUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            itemUri?.let {
                resolver.openOutputStream(it)?.use { out -> out.write(csv.toByteArray()) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(it, values, null, null)
            }
            itemUri
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeText(csv)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
    }

    // ── Share ────────────────────────────────────────────────────────────
    fun shareUri(uri: Uri, mimeType: String, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(Intent.createChooser(intent, "Export Report"))
    }

    fun viewUri(uri: Uri, mimeType: String, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launch(intent)
    }
}
