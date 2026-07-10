package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.domain.Member
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Profile
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private val MEMBER_ID_METADATA_KEYS = listOf("member_id", "memberId", "member_code", "memberCode")

private const val TAG = "MemberRepository"

/**
 * Resolves the current signed-in user's combined member record. `members`
 * holds no identity fields — those live in `profiles` (by user_id) and the
 * auth session (email). Different cooperatives' data was seeded/migrated
 * inconsistently, so the members lookup tries user_id then id before giving
 * up. (A third "by email" fallback isn't possible — confirmed via schema
 * probing that neither members nor profiles has an email column; email only
 * exists in Supabase's internal auth.users, which isn't exposed over REST.)
 */
@Singleton
class MemberRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun findCurrentMember(): MemberDetails? = coroutineScope {
        val session = supabase.auth.currentSessionOrNull() ?: return@coroutineScope null
        val uid     = session.user?.id ?: return@coroutineScope null
        val email   = session.user?.email

        val member = findMemberRow(uid) ?: run {
            Log.w(TAG, "No members row found for uid=$uid via user_id or id")
            return@coroutineScope null
        }

        // Both only depend on `member`, already resolved above — independent
        // of each other, so run them concurrently instead of back-to-back.
        val profileDeferred      = async { member.userId?.let { fetchProfile(it) } ?: fetchProfile(uid) }
        val totalSavingsDeferred = async { fetchTotalSavings(member.id) }
        val profile      = profileDeferred.await()
        val totalSavings = totalSavingsDeferred.await()

        val metadata = session.user?.userMetadata
        Log.d(TAG, "auth user_metadata for $uid: $metadata")
        val formattedMemberId = extractFormattedMemberId(metadata)
        Log.d(TAG, "members.member_number for $uid: ${member.memberNumber}")

        MemberDetails(
            id               = member.id,
            userId           = member.userId,
            cooperativeId    = member.cooperativeId,
            status           = member.status,
            fullName         = profile?.fullName,
            phone            = profile?.phone,
            avatarUrl        = profile?.avatarUrl,
            email            = email,
            totalSavings     = totalSavings,
            createdAt        = member.createdAt,
            memberNumber     = member.memberNumber,
            loginId          = member.loginId,
            formattedMemberId = formattedMemberId,
            kycStatus        = io.cooplink.app.core.domain.KycStatus.fromRaw(member.kycStatus),
        )
    }

    private fun extractFormattedMemberId(metadata: JsonObject?): String? {
        if (metadata == null) return null
        for (key in MEMBER_ID_METADATA_KEYS) {
            val value = metadata[key]?.jsonPrimitive?.contentOrNull
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private suspend fun findMemberRow(uid: String): Member? {
        byUserId(uid)?.let { return it }
        byId(uid)?.let { return it }
        return null
    }

    private suspend fun byUserId(uid: String): Member? = runCatching {
        supabase.db["members"]
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull<Member>()
    }.onFailure { Log.w(TAG, "members lookup by user_id failed", it) }.getOrNull()

    private suspend fun byId(uid: String): Member? = runCatching {
        supabase.db["members"]
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<Member>()
    }.onFailure { Log.w(TAG, "members lookup by id failed", it) }.getOrNull()

    private suspend fun fetchProfile(userId: String): Profile? = runCatching {
        supabase.db["profiles"]
            .select { filter { eq("user_id", userId) } }
            .decodeSingleOrNull<Profile>()
    }.onFailure { Log.w(TAG, "profiles lookup failed", it) }.getOrNull()

    // There is no stored wallet/savings-balance column anywhere in the schema —
    // total savings is the sum of the member's contributions.
    private suspend fun fetchTotalSavings(memberId: String): Double = runCatching {
        supabase.db["contributions"]
            .select { filter { eq("member_id", memberId) } }
            .decodeList<io.cooplink.app.core.domain.Contribution>()
            .sumOf { it.amount }
    }.onFailure { Log.w(TAG, "contributions sum failed", it) }.getOrDefault(0.0)

    /** Members + their profile names for a cooperative — used by admin screens
     * that need a "pick a member" list (manual record entry, dropdowns, etc). */
    suspend fun fetchMembersForCooperative(cooperativeId: String): List<MemberDetails> = runCatching {
        val rows = supabase.db["members"]
            .select { filter { eq("cooperative_id", cooperativeId) } }
            .decodeList<Member>()

        val userIds = rows.mapNotNull { it.userId }
        val profilesByUserId = if (userIds.isEmpty()) emptyMap() else runCatching {
            supabase.db["profiles"]
                .select { filter { isIn("user_id", userIds) } }
                .decodeList<Profile>()
                .associateBy { it.userId }
        }.getOrDefault(emptyMap())

        rows.map { member ->
            val profile = profilesByUserId[member.userId]
            MemberDetails(
                id            = member.id,
                userId        = member.userId,
                cooperativeId = member.cooperativeId,
                status        = member.status,
                fullName      = profile?.fullName,
                phone         = profile?.phone,
                avatarUrl     = profile?.avatarUrl,
                email         = null,
                createdAt     = member.createdAt,
                memberNumber  = member.memberNumber,
                loginId       = member.loginId,
                kycStatus     = io.cooplink.app.core.domain.KycStatus.fromRaw(member.kycStatus),
            )
        }
    }.onFailure { Log.w(TAG, "fetchMembersForCooperative failed", it) }.getOrDefault(emptyList())
}
