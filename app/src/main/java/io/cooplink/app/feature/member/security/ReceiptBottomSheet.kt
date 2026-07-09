package io.cooplink.app.feature.member.security

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.cooplink.app.core.util.ReceiptGenerator
import io.cooplink.app.ui.theme.CoopDarkSurface
import io.cooplink.app.ui.theme.CoopGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptBottomSheet(
    receiptUri: Uri,
    memberName: String,
    transactionType: String,
    amount: String,
    receiptGenerator: ReceiptGenerator,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = CoopDarkSurface) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = CoopGreen, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Payment Successful", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(amount, style = MaterialTheme.typography.displaySmall, color = CoopGreen, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("$transactionType • $memberName", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f))
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { receiptGenerator.viewReceipt(receiptUri, launcher::launch) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Download PDF")
                }
                Button(
                    onClick = {
                        val whatsAppIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/pdf"
                            putExtra(Intent.EXTRA_STREAM, receiptUri)
                            setPackage("com.whatsapp")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        if (whatsAppIntent.resolveActivity(context.packageManager) != null) {
                            launcher.launch(whatsAppIntent)
                        } else {
                            Toast.makeText(context, "WhatsApp isn't installed — sharing another way", Toast.LENGTH_SHORT).show()
                            receiptGenerator.shareReceipt(receiptUri, launcher::launch)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                ) {
                    Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Share via WhatsApp")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
