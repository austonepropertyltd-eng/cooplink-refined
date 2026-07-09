package io.cooplink.app.feature.admin.dashboard

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.security.InactivityManager
import javax.inject.Inject

@HiltViewModel
class AdminShellViewModel @Inject constructor(
    val inactivityManager: InactivityManager,
) : ViewModel() {
    init { inactivityManager.onUserInteraction() }

    override fun onCleared() {
        super.onCleared()
        inactivityManager.stop()
    }
}
