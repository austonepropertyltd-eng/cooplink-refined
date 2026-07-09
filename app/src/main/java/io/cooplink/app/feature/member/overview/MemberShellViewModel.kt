package io.cooplink.app.feature.member.overview

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.security.InactivityManager
import javax.inject.Inject

@HiltViewModel
class MemberShellViewModel @Inject constructor(
    val inactivityManager: InactivityManager,
) : ViewModel() {
    init { inactivityManager.onUserInteraction() }

    override fun onCleared() {
        super.onCleared()
        inactivityManager.stop()
    }
}
