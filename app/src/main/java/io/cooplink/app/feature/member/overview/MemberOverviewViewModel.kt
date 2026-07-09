package io.cooplink.app.feature.member.overview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.data.toDomain
import io.cooplink.app.core.data.toEntity
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.NetworkMonitor
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "MemberOverviewVM"
private const val GENERIC_LOAD_ERROR = "Could not load your data. Pull down to retry."

@Serializable
private data class ShareCapitalValue(val total_value: Double = 0.0)

data class OverviewUiState(
    val isLoading: Boolean         = true,
    val member: MemberDetails?     = null,
    val recentTransactions: List<Transaction> = emptyList(),
    val activeLoansCount: Int      = 0,
    val shareCapital: Double       = 0.0,
    val error: String?             = null,
)

@HiltViewModel
class MemberOverviewViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val database: CoopLinkDatabase,
    private val networkMonitor: NetworkMonitor,
    val signedUrlManager: SignedUrlManager,
) : ViewModel() {

    private val _state = MutableStateFlow(OverviewUiState())
    val state: StateFlow<OverviewUiState> = _state.asStateFlow()

    init { loadData() }

    fun refresh() = loadData()

    private fun loadData() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            // Instant paint from the local cache (if any) while the fresh
            // fetch below runs — most useful on a cold start or a slow/absent
            // connection, where this would otherwise be a blank loading screen.
            val uidForCache = supabase.auth.currentSessionOrNull()?.user?.id
            if (uidForCache != null) {
                runCatching {
                    val cachedMember = database.memberDao().getByUserId(uidForCache) ?: return@runCatching
                    val cachedTransactions = database.transactionDao().observeForMember(cachedMember.id).first()
                    val cachedLoans = database.loanDao().observeForMember(cachedMember.id).first()
                    _state.value = _state.value.copy(
                        member             = cachedMember.toDomain(),
                        recentTransactions = cachedTransactions.map { it.toDomain() },
                        activeLoansCount   = cachedLoans.count { it.status == "disbursed" || it.status == "repaying" },
                    )
                }.onFailure { Log.w(TAG, "Failed to read overview cache", it) }
            }

            try {
                val result = retrying {
                    val uid = supabase.auth.currentSessionOrNull()?.user?.id
                        ?: throw IllegalStateException("Not authenticated")

                    val member = memberRepository.findCurrentMember()
                    val memberId = member?.id ?: uid

                    // These three only depend on memberId (already resolved above)
                    // and not on each other — run them concurrently instead of
                    // back-to-back.
                    coroutineScope {
                        val transactionsDeferred = async {
                            runCatching {
                                supabase.db["transactions"]
                                    .select {
                                        filter { eq("member_id", memberId) }
                                        order("created_at", Order.DESCENDING)
                                        limit(10)
                                    }
                                    .decodeList<Transaction>()
                            }.getOrDefault(emptyList())
                        }

                        val activeLoansDeferred = async {
                            runCatching {
                                supabase.db["loans"]
                                    .select(Columns.list("id")) {
                                        filter {
                                            eq("member_id", memberId)
                                            or {
                                                eq("status", "disbursed")
                                                eq("status", "repaying")
                                            }
                                        }
                                    }
                                    .decodeList<Map<String, String>>()
                                    .size
                            }.getOrDefault(0)
                        }

                        // share_capital only stores a total_value per record —
                        // summed across all of this member's rows.
                        val shareCapitalDeferred = async {
                            runCatching {
                                supabase.db["share_capital"]
                                    .select(Columns.list("total_value")) { filter { eq("member_id", memberId) } }
                                    .decodeList<ShareCapitalValue>()
                                    .sumOf { it.total_value }
                            }.getOrDefault(0.0)
                        }

                        OverviewUiState(
                            isLoading          = false,
                            member             = member,
                            recentTransactions = transactionsDeferred.await(),
                            activeLoansCount   = activeLoansDeferred.await(),
                            shareCapital       = shareCapitalDeferred.await(),
                        )
                    }
                }
                _state.value = result

                // Persist the fresh data for next time (or for offline use).
                runCatching {
                    val memberId = result.member?.id ?: uidForCache
                    result.member?.let { database.memberDao().upsert(it.toEntity()) }
                    if (memberId != null) {
                        database.transactionDao().upsertAll(result.recentTransactions.map { it.toEntity(memberId) })
                    }
                }.onFailure { Log.w(TAG, "Failed to write overview cache", it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load overview data", e)
                val hasCachedContent = _state.value.member != null
                _state.value = _state.value.copy(
                    isLoading = false,
                    // Cached data is already showing and we're offline — that's
                    // expected, not an error worth alarming the user over.
                    error = if (hasCachedContent && !networkMonitor.isOnline.value) null else GENERIC_LOAD_ERROR,
                )
            }
        }
    }
}
