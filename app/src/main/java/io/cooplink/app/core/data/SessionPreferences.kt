package io.cooplink.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore("session_prefs")

/** Remembers the last identifier used to log in (member ID or admin email) so the
 * login screen can offer a "Welcome back" shortcut. Never stores a password —
 * the underlying Supabase session token (if still valid) is what actually lets
 * [loginWithSaved]-style flows skip re-entering a password; this is purely a
 * display convenience. */
@Singleton
class SessionPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.sessionDataStore
    private val keyIdentifier = stringPreferencesKey("last_identifier")
    private val keyIsAdmin    = booleanPreferencesKey("last_is_admin")
    private val keyCachedRole = stringPreferencesKey("cached_role")
    private val keyAutoLogout = booleanPreferencesKey("auto_logout")
    private val keyBioLock    = booleanPreferencesKey("bio_lock")
    private val keyCoopName   = stringPreferencesKey("coop_name")
    private val keyCoopLogo   = stringPreferencesKey("coop_logo")
    private val keyCoopColor  = stringPreferencesKey("coop_color")
    private val keyCoopOrgType   = stringPreferencesKey("coop_org_type")
    private val keyCoopWhatsapp  = stringPreferencesKey("coop_whatsapp")
    private val keyCurrency       = stringPreferencesKey("currency_code")
    private val keyCurrencySymbol = stringPreferencesKey("currency_symbol")

    val savedIdentifierFlow: Flow<String?> = store.data.map { it[keyIdentifier] }
    val savedIsAdminFlow: Flow<Boolean>     = store.data.map { it[keyIsAdmin] ?: false }

    // The exact resolved UserRole from the last successful fetchCurrentUser()
    // call — lets cold start route straight to the right shell without a
    // network round trip, then reconcile with the server in the background.
    // Cleared on logout since it's meaningless once the session ends.
    val cachedRoleFlow: Flow<String?> = store.data.map { it[keyCachedRole] }

    // Both default OFF: the user stays signed in indefinitely (no inactivity
    // timeout, no re-lock on resume) until they explicitly log out, unless
    // they opt into one or both from Security Settings.
    val autoLogoutEnabledFlow: Flow<Boolean> = store.data.map { it[keyAutoLogout] ?: false }
    val requireBiometricFlow: Flow<Boolean>  = store.data.map { it[keyBioLock] ?: false }

    suspend fun setAutoLogoutEnabled(enabled: Boolean) {
        store.edit { it[keyAutoLogout] = enabled }
    }

    suspend fun setRequireBiometric(enabled: Boolean) {
        store.edit { it[keyBioLock] = enabled }
    }

    // Lets the shell top bar/drawer paint the cooperative's real name and
    // logo instantly on launch, instead of the generic "CoopLink" fallback
    // flashing before the network fetch resolves.
    data class CachedBranding(
        val name: String?, val logoUrl: String?, val primaryColor: String?,
        val organizationType: String? = null, val whatsappNumber: String? = null,
    )

    val cachedBrandingFlow: Flow<CachedBranding> = store.data.map {
        CachedBranding(it[keyCoopName], it[keyCoopLogo], it[keyCoopColor], it[keyCoopOrgType], it[keyCoopWhatsapp])
    }

    suspend fun saveCachedBranding(
        name: String?, logoUrl: String?, primaryColor: String?,
        organizationType: String? = null, whatsappNumber: String? = null,
    ) {
        store.edit {
            name?.let { v -> it[keyCoopName] = v }
            logoUrl?.let { v -> it[keyCoopLogo] = v }
            primaryColor?.let { v -> it[keyCoopColor] = v }
            organizationType?.let { v -> it[keyCoopOrgType] = v }
            whatsappNumber?.let { v -> it[keyCoopWhatsapp] = v }
        }
    }

    suspend fun save(identifier: String, isAdmin: Boolean) {
        store.edit {
            it[keyIdentifier] = identifier
            it[keyIsAdmin]    = isAdmin
        }
    }

    suspend fun saveCachedRole(role: String) {
        store.edit { it[keyCachedRole] = role }
    }

    suspend fun clear() {
        store.edit {
            it.remove(keyIdentifier)
            it.remove(keyIsAdmin)
            it.remove(keyCachedRole)
        }
    }

    suspend fun clearCachedRole() {
        store.edit { it.remove(keyCachedRole) }
    }

    // Lets the active currency survive a cold start / offline launch before
    // the cooperative row loads — CurrencyProvider overwrites this the moment
    // a live fetch succeeds.
    val savedCurrencyFlow: Flow<String?>       = store.data.map { it[keyCurrency] }
    val savedCurrencySymbolFlow: Flow<String?> = store.data.map { it[keyCurrencySymbol] }

    suspend fun saveCurrency(code: String, symbol: String) {
        store.edit {
            it[keyCurrency]       = code
            it[keyCurrencySymbol] = symbol
        }
    }
}
