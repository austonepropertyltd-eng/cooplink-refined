package io.cooplink.app.feature.member.security

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.security.TransactionPinPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import javax.inject.Inject

private const val TAG = "TransactionPinVM"

@HiltViewModel
class TransactionPinViewModel @Inject constructor(
    private val pinPreferences: TransactionPinPreferences,
    private val supabase: SupabaseClient,
) : ViewModel() {

    // Assume set until proven otherwise, to avoid flashing the setup screen
    // for a frame on every launch while the real DataStore value loads.
    val isPinSet: StateFlow<Boolean> = pinPreferences.isPinSetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setPin(pin: String, onDone: () -> Unit) {
        viewModelScope.launch {
            pinPreferences.setPin(pin)
            syncPinToServer(pin)
            onDone()
        }
    }

    // PIN verification itself stays fully local/offline (see verifyPin below)
    // — this only mirrors the hash server-side, best-effort, since
    // transaction_pins has no salt column of its own. The user's own id
    // stands in as a per-user salt (SHA-256 of "pin:userId") rather than an
    // unsalted hash of a 4-digit PIN, which would be identical across any
    // two members who happen to pick the same PIN.
    private suspend fun syncPinToServer(pin: String) {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        runCatching {
            val hash = sha256("$pin:$uid")
            val existing = supabase.db["transaction_pins"]
                .select { filter { eq("user_id", uid) } }
                .decodeSingleOrNull<Map<String, String>>()
            if (existing == null) {
                supabase.db["transaction_pins"].insert(
                    buildJsonObject { put("user_id", uid); put("pin_hash", hash) },
                )
            } else {
                supabase.db["transaction_pins"]
                    .update(buildJsonObject { put("pin_hash", hash) }) { filter { eq("user_id", uid) } }
            }
        }.onFailure { Log.w(TAG, "Failed to sync transaction PIN to server", it) }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun verifyPin(pin: String, onResult: (correct: Boolean, lockedOutMinutes: Int) -> Unit) {
        viewModelScope.launch {
            val lockoutUntil = pinPreferences.lockoutUntilFlow.first()
            val remainingMs = lockoutUntil - System.currentTimeMillis()
            if (remainingMs > 0) {
                onResult(false, (remainingMs / 60_000L).toInt() + 1)
                return@launch
            }
            val correct = pinPreferences.verifyPin(pin)
            val newLockout = if (!correct) pinPreferences.lockoutUntilFlow.first() else 0L
            val newRemainingMs = newLockout - System.currentTimeMillis()
            onResult(correct, if (newRemainingMs > 0) (newRemainingMs / 60_000L).toInt() + 1 else 0)
        }
    }
}
