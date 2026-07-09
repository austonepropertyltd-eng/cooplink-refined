package io.cooplink.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore("notification_prefs")

data class NotificationPrefs(
    val allEnabled: Boolean = true,
    val contributions: Boolean = true,
    val loans: Boolean = true,
    val repayments: Boolean = true,
    val announcements: Boolean = true,
    val reminders: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibration: Boolean = true,
)

@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.notificationDataStore

    private val keyAll           = booleanPreferencesKey("all_enabled")
    private val keyContributions = booleanPreferencesKey("contributions")
    private val keyLoans         = booleanPreferencesKey("loans")
    private val keyRepayments    = booleanPreferencesKey("repayments")
    private val keyAnnouncements = booleanPreferencesKey("announcements")
    private val keyReminders     = booleanPreferencesKey("reminders")
    private val keySound         = booleanPreferencesKey("sound_enabled")
    private val keyVibration     = booleanPreferencesKey("vibration")

    val prefsFlow: Flow<NotificationPrefs> = store.data.map {
        NotificationPrefs(
            allEnabled    = it[keyAll] ?: true,
            contributions = it[keyContributions] ?: true,
            loans         = it[keyLoans] ?: true,
            repayments    = it[keyRepayments] ?: true,
            announcements = it[keyAnnouncements] ?: true,
            reminders     = it[keyReminders] ?: true,
            soundEnabled  = it[keySound] ?: true,
            vibration     = it[keyVibration] ?: true,
        )
    }

    suspend fun setAllEnabled(enabled: Boolean) {
        store.edit {
            it[keyAll] = enabled
            if (!enabled) {
                it[keyContributions] = false
                it[keyLoans] = false
                it[keyRepayments] = false
                it[keyAnnouncements] = false
                it[keyReminders] = false
            }
        }
    }

    suspend fun setContributions(enabled: Boolean) { store.edit { it[keyContributions] = enabled } }
    suspend fun setLoans(enabled: Boolean) { store.edit { it[keyLoans] = enabled } }
    suspend fun setRepayments(enabled: Boolean) { store.edit { it[keyRepayments] = enabled } }
    suspend fun setAnnouncements(enabled: Boolean) { store.edit { it[keyAnnouncements] = enabled } }
    suspend fun setReminders(enabled: Boolean) { store.edit { it[keyReminders] = enabled } }
    suspend fun setSoundEnabled(enabled: Boolean) { store.edit { it[keySound] = enabled } }
    suspend fun setVibration(enabled: Boolean) { store.edit { it[keyVibration] = enabled } }
}
