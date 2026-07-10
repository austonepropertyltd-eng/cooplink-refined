package io.cooplink.app.core.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "cached_transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val memberId: String?,
    val type: String,
    val amount: Double,
    val description: String?,
    val status: String,
    val createdAt: String,
)

@Entity(tableName = "cached_loans")
data class LoanEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val amount: Double,
    val outstandingBalance: Double,
    val status: String,
    val createdAt: String?,
)

// Caches the signed-in member's own combined record (members + profiles +
// totalSavings) — one row per member id ever seen on this device. Queries
// always filter by the current session's member id, so stale rows from a
// previous different login on the same device are simply never read.
@Entity(tableName = "cached_members")
data class MemberEntity(
    @PrimaryKey val id: String,
    val userId: String?,
    val cooperativeId: String?,
    val status: String,
    val fullName: String?,
    val phone: String?,
    val avatarUrl: String?,
    val email: String?,
    val totalSavings: Double,
    val createdAt: String?,
    val memberNumber: String?,
    val loginId: String? = null,
)

@Entity(tableName = "cached_cooperatives")
data class CooperativeEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val slug: String?,
    val whatsappNumber: String?,
    val currency: String,
    val logoUrl: String?,
    val address: String?,
    val phone: String?,
    val email: String?,
    val primaryColor: String?,
)

@Entity(tableName = "cached_contributions")
data class ContributionEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val amount: Double,
    val status: String,
    val reference: String?,
    val cooperativeId: String?,
    val createdAt: String,
)

@Entity(tableName = "cached_loan_plans")
data class LoanPlanEntity(
    @PrimaryKey val id: String,
    val cooperativeId: String?,
    val name: String?,
    val minAmount: Double?,
    val maxAmount: Double?,
    val active: Boolean,
)

@Entity(tableName = "cached_notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val cooperativeId: String?,
    val title: String?,
    val message: String?,
    val type: String?,
    val isRead: Boolean,
    val createdAt: String,
)

@Entity(tableName = "cached_standing_orders")
data class StandingOrderEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val cooperativeId: String?,
    val amount: Double,
    val frequency: String,
    val dayOfMonth: Int?,
    val purpose: String?,
    val startDate: String?,
    val endDate: String?,
    val active: Boolean,
    val createdAt: String?,
)

@Entity(tableName = "cached_disputes")
data class DisputeEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val cooperativeId: String?,
    val transactionId: String?,
    val description: String,
    val status: String,
    val category: String?,
    val resolutionNotes: String?,
    val resolvedAt: String?,
    val resolvedBy: String?,
    val createdAt: String?,
)

@Entity(tableName = "cached_savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val cooperativeId: String?,
    val goalName: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val targetDate: String?,
    val status: String,
    val createdAt: String?,
)

/** A write the user made while offline, queued for [io.cooplink.app.core.sync.SyncWorker]
 * to replay once connectivity returns. [payloadJson] holds whatever fields that
 * specific [operationType] needs — kept generic rather than one table per
 * operation, since new offline-capable write flows can reuse this without a
 * schema migration. */
@Entity(tableName = "offline_queue")
data class OfflineQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,
    val payloadJson: String,
    val createdAt: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
)

@Dao
interface TransactionDao {
    @Query("SELECT * FROM cached_transactions WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeForMember(memberId: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TransactionEntity>)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM cached_loans WHERE memberId = :memberId")
    fun observeForMember(memberId: String): Flow<List<LoanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LoanEntity>)
}

@Dao
interface MemberDao {
    @Query("SELECT * FROM cached_members WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MemberEntity?

    // members.id and the auth user id aren't always the same value, and this
    // cache is read before that mapping is known — looked up by whichever
    // one the caller already has.
    @Query("SELECT * FROM cached_members WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): MemberEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemberEntity)
}

@Dao
interface CooperativeDao {
    @Query("SELECT * FROM cached_cooperatives WHERE id = :id LIMIT 1")
    suspend fun get(id: String): CooperativeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: CooperativeEntity)
}

@Dao
interface ContributionDao {
    @Query("SELECT * FROM cached_contributions WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeForMember(memberId: String): Flow<List<ContributionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContributionEntity>)
}

@Dao
interface LoanPlanDao {
    @Query("SELECT * FROM cached_loan_plans WHERE cooperativeId = :cooperativeId")
    fun observeForCooperative(cooperativeId: String): Flow<List<LoanPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LoanPlanEntity>)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM cached_notifications WHERE userId = :userId ORDER BY createdAt DESC")
    fun observeForUser(userId: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<NotificationEntity>)
}

@Dao
interface StandingOrderDao {
    @Query("SELECT * FROM cached_standing_orders WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeForMember(memberId: String): Flow<List<StandingOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StandingOrderEntity>)
}

@Dao
interface DisputeDao {
    @Query("SELECT * FROM cached_disputes WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeForMember(memberId: String): Flow<List<DisputeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DisputeEntity>)
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM cached_savings_goals WHERE memberId = :memberId ORDER BY createdAt DESC")
    fun observeForMember(memberId: String): Flow<List<SavingsGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavingsGoalEntity>)
}

@Dao
interface OfflineQueueDao {
    @Query("SELECT * FROM offline_queue ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<OfflineQueueEntity>>

    @Query("SELECT * FROM offline_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<OfflineQueueEntity>

    @Insert
    suspend fun enqueue(item: OfflineQueueEntity): Long

    @Query("DELETE FROM offline_queue WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("UPDATE offline_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)
}

@Database(
    entities = [
        TransactionEntity::class, LoanEntity::class,
        MemberEntity::class, CooperativeEntity::class,
        ContributionEntity::class, LoanPlanEntity::class,
        OfflineQueueEntity::class,
        NotificationEntity::class, StandingOrderEntity::class,
        DisputeEntity::class, SavingsGoalEntity::class,
    ],
    version  = 4,
    exportSchema = false,
)
abstract class CoopLinkDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun loanDao(): LoanDao
    abstract fun memberDao(): MemberDao
    abstract fun cooperativeDao(): CooperativeDao
    abstract fun contributionDao(): ContributionDao
    abstract fun loanPlanDao(): LoanPlanDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun notificationDao(): NotificationDao
    abstract fun standingOrderDao(): StandingOrderDao
    abstract fun disputeDao(): DisputeDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        const val NAME = "cooplink_cache.db"
    }
}
