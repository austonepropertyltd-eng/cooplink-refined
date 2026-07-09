package io.cooplink.app.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.txPinDataStore by preferencesDataStore("tx_pin_prefs")
private const val MAX_ATTEMPTS = 3
private const val LOCKOUT_DURATION_MS = 30 * 60 * 1_000L

/** A 4-digit PIN the member sets up to confirm in-app payments — separate
 * from their account password and never sent to the server. Hashed with a
 * per-install random salt (rather than plain SHA-256) since a fixed,
 * unsalted hash of a 4-digit PIN is trivially brute-forceable from the
 * stored value alone. */
@Singleton
class TransactionPinPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.txPinDataStore
    private val keyHash          = stringPreferencesKey("pin_hash")
    private val keySalt          = stringPreferencesKey("pin_salt")
    private val keyFailedAttempts = intPreferencesKey("failed_attempts")
    private val keyLockoutUntil   = longPreferencesKey("lockout_until")

    val isPinSetFlow: Flow<Boolean> = store.data.map { it[keyHash] != null }
    val lockoutUntilFlow: Flow<Long> = store.data.map { it[keyLockoutUntil] ?: 0L }

    suspend fun setPin(pin: String) {
        val salt = generateSalt()
        store.edit {
            it[keyHash] = hash(pin, salt)
            it[keySalt] = salt
            it[keyFailedAttempts] = 0
            it[keyLockoutUntil] = 0L
        }
    }

    /** Returns true if [pin] matches, tracking failed attempts and applying a
     * 30-minute lockout after 3 wrong tries. */
    suspend fun verifyPin(pin: String): Boolean {
        val prefs = store.data.first()
        val storedHash = prefs[keyHash] ?: return false
        val salt = prefs[keySalt] ?: return false

        val matches = hash(pin, salt) == storedHash
        if (matches) {
            store.edit { it[keyFailedAttempts] = 0; it[keyLockoutUntil] = 0L }
        } else {
            val attempts = (prefs[keyFailedAttempts] ?: 0) + 1
            store.edit {
                it[keyFailedAttempts] = attempts
                if (attempts >= MAX_ATTEMPTS) it[keyLockoutUntil] = System.currentTimeMillis() + LOCKOUT_DURATION_MS
            }
        }
        return matches
    }

    suspend fun clearPin() {
        store.edit {
            it.remove(keyHash); it.remove(keySalt)
            it.remove(keyFailedAttempts); it.remove(keyLockoutUntil)
        }
    }

    private fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hash(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(Base64.decode(salt, Base64.NO_WRAP))
        return Base64.encodeToString(digest.digest(pin.toByteArray()), Base64.NO_WRAP)
    }
}
