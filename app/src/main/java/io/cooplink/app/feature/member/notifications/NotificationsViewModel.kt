package io.cooplink.app.feature.member.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.toDomain
import io.cooplink.app.core.data.toEntity
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "NotificationsVM"
private const val GENERIC_LOAD_ERROR = "Could not load your notifications. Pull down to retry."

@Serializable
data class NotificationItem(
    val id: String,
    val title: String?    = null,
    val message: String?  = null,
    val type: String?     = null,
    val is_read: Boolean  = false,
    val created_at: String,
)

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val notifications: List<NotificationItem> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val database: CoopLinkDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()

    fun markAsRead(notificationId: String) {
        val notification = _state.value.notifications.find { it.id == notificationId }
        if (notification == null || notification.is_read) return

        // Optimistic — flip the dot immediately, don't wait on the network.
        _state.update { s ->
            s.copy(notifications = s.notifications.map { if (it.id == notificationId) it.copy(is_read = true) else it })
        }
        viewModelScope.launch {
            runCatching {
                supabase.db["notifications"]
                    .update(buildJsonObject { put("is_read", true) }) { filter { eq("id", notificationId) } }
            }.onFailure { Log.w(TAG, "Failed to mark notification $notificationId as read", it) }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val uidForCache = supabase.auth.currentSessionOrNull()?.user?.id
            if (uidForCache != null) {
                runCatching {
                    val cached = database.notificationDao().observeForUser(uidForCache).first()
                    if (cached.isNotEmpty()) _state.value = _state.value.copy(notifications = cached.map { it.toDomain() })
                }.onFailure { Log.w(TAG, "Failed to read notifications cache", it) }
            }

            try {
                val result = retrying {
                    val uid = uidForCache ?: throw IllegalStateException("Not authenticated")

                    val notifications = supabase.db["notifications"]
                        .select {
                            filter { eq("user_id", uid) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<NotificationItem>()

                    NotificationsUiState(isLoading = false, notifications = notifications)
                }
                _state.value = result

                runCatching {
                    if (uidForCache != null) database.notificationDao().upsertAll(result.notifications.map { it.toEntity(uidForCache) })
                }.onFailure { Log.w(TAG, "Failed to write notifications cache", it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load notifications", e)
                val hasCachedContent = _state.value.notifications.isNotEmpty()
                _state.value = _state.value.copy(isLoading = false, error = if (hasCachedContent) null else GENERIC_LOAD_ERROR)
            }
        }
    }
}
