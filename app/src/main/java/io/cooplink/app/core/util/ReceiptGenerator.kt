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
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReceiptGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Builds a one-page PDF receipt and saves it to Downloads, returning a
     * content Uri suitable for viewing or sharing. */
    fun generateReceipt(
        receiptNumber: String,
        memberName: String,
        memberId: String,
        cooperativeName: String,
        transactionType: String,
        amount: Double,
        date: String,
        reference: String,
    ): Uri? {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        val paint = Paint().apply { isAntiAlias = true }

        paint.color = android.graphics.Color.parseColor("#0A1628")
        canvas.drawRect(0f, 0f, 595f, 200f, paint)

        runCatching {
            val logo = BitmapFactory.decodeResource(context.resources, R.drawable.cooplink_logo)
            val scaled = Bitmap.createScaledBitmap(logo, 200, 80, true)
            canvas.drawBitmap(scaled, 40f, 40f, null)
        }

        paint.color = android.graphics.Color.parseColor("#FCB424")
        paint.textSize = 28f
        paint.isFakeBoldText = true
        canvas.drawText("OFFICIAL RECEIPT", 360f, 80f, paint)

        paint.color = android.graphics.Color.WHITE
        paint.textSize = 16f
        paint.isFakeBoldText = false
        canvas.drawText("No: $receiptNumber", 360f, 110f, paint)
        canvas.drawText(date, 360f, 135f, paint)

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText(cooperativeName, 40f, 160f, paint)
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Powered by CoopLink", 40f, 182f, paint)

        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(0f, 200f, 595f, 842f, paint)

        paint.color = android.graphics.Color.parseColor("#E2E8F0")
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 220f, 555f, 220f, paint)

        fun drawRow(label: String, value: String, y: Float) {
            paint.textSize = 14f
            paint.color = android.graphics.Color.parseColor("#718096")
            paint.isFakeBoldText = false
            canvas.drawText(label, 40f, y, paint)

            paint.textSize = 16f
            paint.color = android.graphics.Color.parseColor("#1A202C")
            paint.isFakeBoldText = true
            canvas.drawText(value, 300f, y, paint)
        }

        drawRow("Member Name", memberName, 260f)
        drawRow("Member ID", memberId, 300f)
        drawRow("Transaction", transactionType, 340f)
        drawRow("Reference", reference, 380f)
        drawRow("Date", date, 420f)

        paint.color = android.graphics.Color.parseColor("#1FA67A")
        paint.textSize = 48f
        paint.isFakeBoldText = true
        val fmt = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2; maximumFractionDigits = 2
        }
        canvas.drawText("₦${fmt.format(amount)}", 40f, 530f, paint)

        paint.color = android.graphics.Color.parseColor("#718096")
        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Amount Paid", 40f, 555f, paint)

        paint.color = android.graphics.Color.parseColor("#FCB424")
        paint.strokeWidth = 3f
        canvas.drawLine(40f, 580f, 555f, 580f, paint)

        paint.color = android.graphics.Color.parseColor("#1FA67A")
        canvas.drawRoundRect(RectF(40f, 600f, 220f, 640f), 20f, 20f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas.drawText("✓ PAYMENT CONFIRMED", 55f, 626f, paint)

        paint.color = android.graphics.Color.parseColor("#718096")
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("This is an official receipt from $cooperativeName", 40f, 780f, paint)
        canvas.drawText("For support: contact your cooperative admin", 40f, 800f, paint)
        canvas.drawText("Powered by CoopLink", 40f, 820f, paint)

        document.finishPage(page)

        val fileName = "CoopLink_Receipt_$receiptNumber.pdf"
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
        return uri
    }

    fun shareReceipt(uri: Uri, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "CoopLink Payment Receipt")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(Intent.createChooser(intent, "Share Receipt"))
    }

    fun viewReceipt(uri: Uri, launch: (Intent) -> Unit) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launch(intent)
    }
}
