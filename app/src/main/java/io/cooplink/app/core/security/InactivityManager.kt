package io.cooplink.app.core.security

import io.cooplink.app.core.data.SessionPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_TIMEOUT_MINUTES = 15

@Singleton
class InactivityManager @Inject constructor(
    sessionPreferences: SessionPreferences,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var job: Job? = null

    private val _expired = MutableStateFlow(false)
    val sessionExpired: StateFlow<Boolean> = _expired.asStateFlow()

    // Off by default — the user stays signed in indefinitely until they
    // explicitly log out, unless they opt into this from Security Settings.
    // Kept in sync with the persisted preference so a toggle flip takes
    // effect immediately without needing a fresh app launch.
    private var autoLogoutEnabled = false
    private var timeoutMs = DEFAULT_TIMEOUT_MINUTES * 60 * 1_000L

    init {
        scope.launch {
            sessionPreferences.autoLogoutEnabledFlow.collect { enabled ->
                autoLogoutEnabled = enabled
                if (!enabled) {
                    job?.cancel()
                    _expired.value = false
                }
            }
        }
    }

    // Set while a system picker/camera/share-sheet/download that this app
    // itself launched is in front — backgrounds MainActivity briefly without
    // being a real "user left the app" event, so both the inactivity
    // countdown and the biometric re-lock on resume should stand down.
    var isPaused: Boolean = false
        private set

    /** Call on every user interaction — resets the countdown (no-op unless
     * auto-logout is enabled). */
    fun onUserInteraction() {
        if (!autoLogoutEnabled || _expired.value || isPaused) return
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs)
            _expired.value = true
        }
    }

    /** Called by the Security Settings toggle. */
    fun setAutoLogout(enabled: Boolean, timeoutMinutes: Int = DEFAULT_TIMEOUT_MINUTES) {
        autoLogoutEnabled = enabled
        timeoutMs = timeoutMinutes * 60 * 1_000L
        if (enabled) onUserInteraction() else { job?.cancel(); _expired.value = false }
    }

    /** Call just before launching a picker/camera/share sheet/download. */
    fun pauseTimer() {
        isPaused = true
        job?.cancel()
    }

    /** Call when that picker/share sheet returns control to the app.
     *
     * The clear is deferred a beat rather than immediate: the Activity
     * Result callback that calls this can fire right around the same time
     * as MainActivity.onResume() (sometimes first), so clearing isPaused
     * synchronously here can race ahead of onResume()'s isPaused check and
     * let the biometric re-lock fire anyway right after picking a file. */
    fun resumeTimer() {
        scope.launch {
            delay(500)
            isPaused = false
            onUserInteraction()
        }
    }

    /** Call after navigating away to login */
    fun acknowledge() { _expired.value = false }

    /** Call when user actively logs out */
    fun stop() { job?.cancel(); _expired.value = false; isPaused = false }
}
