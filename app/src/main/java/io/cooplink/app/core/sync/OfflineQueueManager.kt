package io.cooplink.app.core.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.OfflineQueueEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val SYNC_WORK_NAME = "offline_queue_sync"

/** Known offline-queueable write operations. Each maps to a replay branch in
 * [SyncWorker] — add a new constant here + a branch there for a new
 * offline-capable write flow. */
object QueuedOperationType {
    const val ADD_CONTRIBUTION = "add_contribution"
}

/** Shared between the enqueue side (e.g. ContributionsViewModel) and the
 * replay side ([SyncWorker]) so the two can never drift out of sync on field
 * names/shape. */
@Serializable
data class AddContributionPayload(
    val member_id: String,
    val cooperative_id: String?,
    val amount: Double,
)

/** Records a write the user made while offline so it can be replayed once
 * connectivity returns, and lets screens show what's still pending. This is
 * intentionally generic (a type tag + a JSON payload) rather than one table
 * per operation, since new offline-capable writes can reuse it without a
 * schema migration. */
@Singleton
class OfflineQueueManager @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val database: CoopLinkDatabase,
) {
    val pendingOperations: Flow<List<OfflineQueueEntity>> = database.offlineQueueDao().observeAll()

    suspend fun enqueue(operationType: String, payloadJson: String) {
        database.offlineQueueDao().enqueue(
            OfflineQueueEntity(
                operationType = operationType,
                payloadJson   = payloadJson,
                createdAt     = System.currentTimeMillis(),
            ),
        )
        scheduleSync()
    }

    /** Also called on app start / connectivity regained so anything already
     * queued from a previous session gets a chance to replay. */
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
