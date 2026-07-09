package io.cooplink.app.feature.member.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.NotificationPreferences
import io.cooplink.app.core.data.NotificationPrefs
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "NotificationSettingsVM"

@Serializable
private data class DeviceTokenInsert(val user_id: String, val token: String, val platform: String = "android")

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val notificationPreferences: NotificationPreferences,
    private val authRepository: AuthRepository,
    private val supabase: SupabaseClient,
) : ViewModel() {

    val prefs: StateFlow<NotificationPrefs> = notificationPreferences.prefsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NotificationPrefs())

    fun setAllEnabled(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setAllEnabled(enabled) }
    fun setContributions(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setContributions(enabled) }
    fun setLoans(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setLoans(enabled) }
    fun setRepayments(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setRepayments(enabled) }
    fun setAnnouncements(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setAnnouncements(enabled) }
    fun setReminders(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setReminders(enabled) }
    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setSoundEnabled(enabled) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { notificationPreferences.setVibration(enabled) }

    // No device_tokens table exists on this backend yet (confirmed via a
    // direct probe — the table isn't found). This fails silently rather than
    // crashing; push delivery to a specific device just isn't addressable
    // until that table (and the server-side send path) exists.
    fun saveDeviceToken(token: String) {
        viewModelScope.launch {
            try {
                val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return@launch
                supabase.db["device_tokens"].insert(DeviceTokenInsert(user_id = uid, token = token))
            } catch (e: Exception) {
                Log.w(TAG, "Could not save device token (device_tokens table likely missing)", e)
            }
        }
    }
}
