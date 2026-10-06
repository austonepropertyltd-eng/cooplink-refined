package io.cooplink.app.feature.member.disputes

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.toDomain
import io.cooplink.app.core.data.toEntity
import io.cooplink.app.core.domain.Transaction
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
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

private const val TAG = "DisputesVM"
private const val GENERIC_LOAD_ERROR = "Could not load your disputes. Pull down to retry."

object DisputeStatus {
    const val OPEN        = "open"
    const val INVESTIGATING = "investigating"
    const val RESOLVED    = "resolved"
    const val REJECTED    = "rejected"
}

object DisputeCategory {
    const val TRANSACTION   = "transaction"
    const val LOAN          = "loan"
    const val CONTRIBUTION  = "contribution"
    const val OTHER         = "other"
    val ALL = listOf(TRANSACTION, LOAN, CONTRIBUTION, OTHER)
}

@Serializable
data class Dispute(
    val id: String,
    val member_id: String,
    val cooperative_id: String? = null,
    val transaction_id: String? = null,
    val subject: String? = null,
    val description: String,
    val status: String = DisputeStatus.OPEN,
    val category: String? = null,
    val resolution_notes: String? = null,
    val resolved_at: String? = null,
    val resolved_by: String? = null,
    val created_at: String? = null,
)

@Serializable
private data class NewDisputeRequest(
    val member_id: String,
    val cooperative_id: String?,
    val transaction_id: String?,
    val subject: String,
    val description: String,
    val category: String,
    // No default — see NewRepaymentRequest.type in AdminRepaymentsViewModel
    // for why a defaulted property is silently dropped from the request even
    // when the call site passes exactly that value.
    val status: String,
)

data class DisputesUiState(
    val isLoading: Boolean              = true,
    val disputes: List<Dispute>         = emptyList(),
    val recentTransactions: List<Transaction> = emptyList(),
    val error: String?                  = null,
    val isSubmitting: Boolean           = false,
    val submitError: String?            = null,
    // True when the list on screen is a local cache and the most recent
    // refresh attempt failed — the error is intentionally suppressed in that
    // case (see load()) so this is the only signal the data may be stale.
    val isShowingStaleCache: Boolean    = false,
)

@HiltViewModel
class DisputesViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val database: CoopLinkDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(DisputesUiState())
    val state: StateFlow<DisputesUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    fun fileDispute(subject: String, description: String, category: String, transactionId: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                supabase.db["disputes"].insert(
                    NewDisputeRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        transaction_id = transactionId,
                        subject        = subject,
                        description    = description,
                        category       = category,
                        // Must be passed explicitly — kotlinx.serialization
                        // doesn't encode a property still at its default
                        // value, silently dropping it from the request and
                        // failing on a NOT NULL constraint (confirmed live
                        // for the identical pattern in AdminRepaymentsViewModel).
                        status         = DisputeStatus.OPEN,
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to file dispute", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not submit this dispute. Please try again.")
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)

            val uidForCache = supabase.auth.currentSessionOrNull()?.user?.id
            if (uidForCache != null) {
                runCatching {
                    val cachedMemberId = database.memberDao().getByUserId(uidForCache)?.id ?: return@runCatching
                    val cached = database.disputeDao().observeForMember(cachedMemberId).first()
                    if (cached.isNotEmpty()) _state.value = _state.value.copy(disputes = cached.map { it.toDomain() })
                }.onFailure { Log.w(TAG, "Failed to read disputes cache", it) }
            }

            try {
                val result = retrying {
                    val member = memberRepository.findCurrentMember()
                        ?: throw IllegalStateException("Could not determine your member record")

                    coroutineScope {
                        val disputesDeferred = async {
                            supabase.db["disputes"]
                                .select {
                                    filter { eq("member_id", member.id) }
                                    order("created_at", Order.DESCENDING)
                                }
                                .decodeList<Dispute>()
                        }
                        val transactionsDeferred = async {
                            runCatching {
                                supabase.db["transactions"]
                                    .select {
                                        filter { eq("member_id", member.id) }
                                        order("created_at", Order.DESCENDING)
                                        limit(20)
                                    }
                                    .decodeList<Transaction>()
                            }.getOrDefault(emptyList())
                        }

                        DisputesUiState(
                            isLoading          = false,
                            disputes           = disputesDeferred.await(),
                            recentTransactions = transactionsDeferred.await(),
                        )
                    }
                }
                _state.value = result

                runCatching {
                    database.disputeDao().upsertAll(result.disputes.map { it.toEntity() })
                }.onFailure { Log.w(TAG, "Failed to write disputes cache", it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load disputes", e)
                val hasCachedContent = _state.value.disputes.isNotEmpty()
                _state.value = _state.value.copy(
                    isLoading           = false,
                    error               = if (hasCachedContent) null else GENERIC_LOAD_ERROR,
                    isShowingStaleCache = hasCachedContent,
                )
            }
        }
    }
}
