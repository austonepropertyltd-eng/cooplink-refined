package io.cooplink.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import io.cooplink.app.core.notifications.CoopLinkMessagingService
import io.cooplink.app.core.sync.OfflineQueueManager
import io.cooplink.app.core.sync.TokenRefreshWorker
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val TAG = "CoopLinkApplication"
private const val TOKEN_REFRESH_WORK_NAME = "token_refresh"
private const val TOKEN_REFRESH_INTERVAL_MINUTES = 50L // Supabase access tokens expire after 60

@HiltAndroidApp
class CoopLinkApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var offlineQueueManager: OfflineQueueManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        // Anything queued from a previous session that never got a chance to
        // sync (app killed before connectivity returned) gets another attempt
        // here — the request's own network constraint holds it until online.
        offlineQueueManager.scheduleSync()

        CoopLinkMessagingService.createNotificationChannels(
            this, getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager,
        )

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TOKEN_REFRESH_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TokenRefreshWorker>(TOKEN_REFRESH_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )

        // A slow/flaky connection can make Ktor's request-timeout fire at the
        // exact moment a coroutine is already being cancelled (e.g. a screen
        // navigated away mid-request). When that race happens, the timeout
        // exception is thrown from Ktor's internal killer coroutine during
        // cancellation cleanup rather than at the awaited call site, so it
        // bypasses every try/catch around the network call and reaches the
        // process's default handler, crashing the whole app over what's just
        // a transient network hiccup. Every real call site already retries
        // and surfaces a friendly error — this only guards against that one
        // narrow escape path, and still lets genuine bugs crash normally.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            if (isTransientNetworkException(throwable)) {
                Log.e(TAG, "Swallowed uncaught transient network exception", throwable)
            } else {
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun isTransientNetworkException(t: Throwable?): Boolean = when (t) {
        null -> false
        is HttpRequestTimeoutException, is TimeoutCancellationException,
        is SocketTimeoutException, is UnknownHostException, is IOException -> true
        else -> isTransientNetworkException(t.cause)
    }
}
