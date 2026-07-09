package io.cooplink.app.core.payment

import android.net.Uri
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the payment-callback deep link received by MainActivity to
 * PaymentViewModel, which is scoped to the Compose back-stack entry
 * hosting ContributionsScreen and isn't reachable directly from the Activity.
 */
@Singleton
class PaymentDeepLinkBus @Inject constructor() {

    private val _events = MutableSharedFlow<Uri>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Uri> = _events

    fun emit(uri: Uri) {
        _events.tryEmit(uri)
    }
}
