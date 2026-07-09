package io.cooplink.app.core.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TAG = "SyncWorker"
private val json = Json { ignoreUnknownKeys = true }

/** Replays writes queued by [OfflineQueueManager] while the device was
 * offline. Runs whenever WorkManager's network constraint is satisfied (see
 * [OfflineQueueManager.enqueue]) — each queue item is processed independently
 * so one failure doesn't block the rest. */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: CoopLinkDatabase,
    private val supabase: SupabaseClient,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val dao = database.offlineQueueDao()
        val pending = dao.getAll()

        for (item in pending) {
            val outcome = runCatching {
                when (item.operationType) {
                    QueuedOperationType.ADD_CONTRIBUTION -> replayAddContribution(item.payloadJson)
                    else -> Log.w(TAG, "Unknown queued operation type: ${item.operationType}")
                }
            }

            outcome
                .onSuccess { dao.remove(item.id) }
                .onFailure { e ->
                    Log.w(TAG, "Failed to replay queued operation ${item.id} (${item.operationType})", e)
                    dao.markFailed(item.id, e.message ?: "Unknown error")
                }
        }

        return Result.success()
    }

    private suspend fun replayAddContribution(payloadJson: String) {
        val payload = json.decodeFromString<AddContributionPayload>(payloadJson)
        supabase.db["contributions"].insert(
            buildJsonObject {
                put("member_id", payload.member_id)
                payload.cooperative_id?.let { put("cooperative_id", it) }
                put("amount", payload.amount)
                put("status", "pending")
            },
        )
    }
}
