package io.cooplink.app.feature.member.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.cooplink.app.ui.theme.CoopDarkSurface
import io.cooplink.app.ui.theme.CoopError
import io.cooplink.app.ui.theme.CoopGold
import io.cooplink.app.ui.theme.CoopNavyDeep

@Composable
fun SetupPinScreen(
    onDone: () -> Unit,
    viewModel: TransactionPinViewModel = hiltViewModel(),
) {
    var firstEntry by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = CoopNavyDeep) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                if (firstEntry == null) "Set Your Transaction PIN" else "Confirm Your PIN",
                style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (firstEntry == null) "This PIN protects your payments" else "Enter the same 4 digits again",
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(32.dp))
            PinDots(length = pin.length)
            Spacer(Modifier.height(16.dp))
            if (error.isNotEmpty()) {
                Text(error, color = CoopError, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(16.dp))
            NumberPad { key ->
                when (key) {
                    PinKey.BACKSPACE -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                    else -> if (pin.length < 4) {
                        pin += key.digit
                        if (pin.length == 4) {
                            if (firstEntry == null) {
                                firstEntry = pin
                                pin = ""
                                error = ""
                            } else if (pin == firstEntry) {
                                viewModel.setPin(pin, onDone = onDone)
                            } else {
                                error = "PINs don't match — try again"
                                firstEntry = null
                                pin = ""
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinEntryDialog(
    title: String = "Enter Transaction PIN",
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: TransactionPinViewModel = hiltViewModel(),
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var lockedMinutes by remember { mutableStateOf(0) }

    AlertDialog(
        containerColor = CoopDarkSurface,
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                PinDots(length = pin.length)
                Spacer(Modifier.height(24.dp))
                if (lockedMinutes > 0) {
                    Text(
                        "Too many attempts. Try again in $lockedMinutes min.",
                        color = CoopError, style = MaterialTheme.typography.labelMedium,
                    )
                } else if (error.isNotEmpty()) {
                    Text(error, color = CoopError, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(8.dp))
                NumberPad(enabled = lockedMinutes == 0) { key ->
                    when (key) {
                        PinKey.BACKSPACE -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                        else -> if (pin.length < 4) {
                            pin += key.digit
                            if (pin.length == 4) {
                                viewModel.verifyPin(pin) { correct, minutesLeft ->
                                    if (correct) {
                                        onSuccess()
                                    } else {
                                        lockedMinutes = minutesLeft
                                        error = if (minutesLeft == 0) "Incorrect PIN — try again" else ""
                                        pin = ""
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.5f)) } },
    )
}

@Composable
private fun PinDots(length: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(4) { i ->
            Box(
                Modifier.size(20.dp)
                    .background(if (i < length) CoopGold else Color.White.copy(alpha = 0.2f), CircleShape),
            )
        }
    }
}

private enum class PinKey(val digit: String) {
    D1("1"), D2("2"), D3("3"), D4("4"), D5("5"), D6("6"), D7("7"), D8("8"), D9("9"), D0("0"),
    BACKSPACE(""), BLANK("");
}

@Composable
private fun NumberPad(enabled: Boolean = true, onKey: (PinKey) -> Unit) {
    val rows = listOf(
        listOf(PinKey.D1, PinKey.D2, PinKey.D3),
        listOf(PinKey.D4, PinKey.D5, PinKey.D6),
        listOf(PinKey.D7, PinKey.D8, PinKey.D9),
        listOf(PinKey.BLANK, PinKey.D0, PinKey.BACKSPACE),
    )
    Column {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(if (key == PinKey.BLANK) Color.Transparent else Color.White.copy(alpha = 0.08f))
                            .clickable(enabled = enabled && key != PinKey.BLANK) { onKey(key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (key) {
                            PinKey.BLANK -> {}
                            PinKey.BACKSPACE -> Icon(Icons.Default.Backspace, "Backspace", tint = Color.White)
                            else -> Text(key.digit, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
