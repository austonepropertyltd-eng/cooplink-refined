package io.cooplink.app.feature.shell

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.network.NetworkMonitor
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class OfflineBannerViewModel @Inject constructor(
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
}
