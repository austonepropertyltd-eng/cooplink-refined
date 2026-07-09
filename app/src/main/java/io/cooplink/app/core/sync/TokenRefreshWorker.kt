package io.cooplink.app.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.cooplink.app.core.network.SupabaseClient

private const val TAG = "TokenRefreshWorker"

/** Silently refreshes the Supabase session in the background so a user who
 * leaves the app open/backgrounded for a long time doesn't hit an expired
 * access token the next time a screen makes a request. Supabase's client
 * already auto-refreshes on demand (alwaysAutoRefresh = true), so this is a
 * belt-and-suspenders top-up rather than the only refresh path. A failure
 * here (e.g. offline) isn't a real work failure — there's nothing to retry
 * urgently, the next periodic run or an on-demand refresh will cover it. */
@HiltWorker
class TokenRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (supabase.auth.currentSessionOrNull() != null) {
                supabase.auth.refreshCurrentSession()
                Log.d(TAG, "Session refreshed")
            }
            Result.success()
        } catch (e: Exception) {
            Log.w(TAG, "Refresh failed (likely offline): ${e.message}")
            Result.success()
        }
    }
}
