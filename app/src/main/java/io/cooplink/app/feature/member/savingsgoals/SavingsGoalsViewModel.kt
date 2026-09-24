package io.cooplink.app.feature.member.savingsgoals

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.data.toDomain
import io.cooplink.app.core.data.toEntity
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

private const val TAG = "SavingsGoalsVM"
private const val GENERIC_LOAD_ERROR = "Could not load your savings goals. Pull down to retry."

object SavingsGoalStatus {
    const val ACTIVE    = "active"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
}

@Serializable
data class SavingsGoal(
    val id: String,
    val member_id: String,
    val cooperative_id: String? = null,
    val goal_name: String,
    val target_amount: Double,
    val current_amount: Double = 0.0,
    val target_date: String? = null,
    val status: String = SavingsGoalStatus.ACTIVE,
    val created_at: String? = null,
)

@Serializable
private data class NewSavingsGoalRequest(
    val member_id: String,
    val cooperative_id: String?,
    val goal_name: String,
    val target_amount: Double,
    val target_date: String?,
    // No default — see NewRepaymentRequest.type in AdminRepaymentsViewModel
    // for why a defaulted property is silently dropped from the request even
    // when the call site passes exactly that value.
    val status: String,
)

@Serializable
private data class SavingsGoalAmountPatch(val current_amount: Double, val status: String)

data class SavingsGoalsUiState(
    val isLoading: Boolean         = true,
    val goals: List<SavingsGoal>   = emptyList(),
    val error: String?             = null,
    val isSubmitting: Boolean      = false,
    val submitError: String?       = null,
)

@HiltViewModel
class SavingsGoalsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val database: CoopLinkDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(SavingsGoalsUiState())
    val state: StateFlow<SavingsGoalsUiState> = _state.asStateFlow()

    init { load() }

    fun refresh() = load()
    fun clearSubmitError() { _state.value = _state.value.copy(submitError = null) }

    fun createGoal(goalName: String, targetAmount: Double, targetDate: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")
                supabase.db["savings_goals"].insert(
                    NewSavingsGoalRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        goal_name      = goalName,
                        target_amount  = targetAmount,
                        target_date    = targetDate,
                        // Must be passed explicitly — kotlinx.serialization
                        // doesn't encode a property still at its default
                        // value, silently dropping it from the request and
                        // failing on a NOT NULL constraint (confirmed live
                        // for the identical pattern in AdminRepaymentsViewModel).
                        status         = SavingsGoalStatus.ACTIVE,
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create savings goal", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not create this goal. Please try again.")
            }
        }
    }

    /** Adds [amount] towards a goal's current progress — a manual top-up
     * recorded directly on the goal row, separate from any wallet/transaction
     * flow (savings_goals has no linkage to transactions in the schema). */
    fun addToGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val newAmount = goal.current_amount + amount
                val newStatus = if (newAmount >= goal.target_amount) SavingsGoalStatus.COMPLETED else goal.status
                supabase.db["savings_goals"]
                    .update(SavingsGoalAmountPatch(newAmount, newStatus)) { filter { eq("id", goal.id) } }
                _state.value = _state.value.copy(isSubmitting = false)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update savings goal ${goal.id}", e)
                _state.value = _state.value.copy(isSubmitting = false, submitError = "Could not update this goal. Please try again.")
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
                    val cached = database.savingsGoalDao().observeForMember(cachedMemberId).first()
                    if (cached.isNotEmpty()) _state.value = _state.value.copy(goals = cached.map { it.toDomain() })
                }.onFailure { Log.w(TAG, "Failed to read savings goals cache", it) }
            }

            try {
                val result = retrying {
                    val member = memberRepository.findCurrentMember()
                        ?: throw IllegalStateException("Could not determine your member record")

                    val goals = supabase.db["savings_goals"]
                        .select {
                            filter { eq("member_id", member.id) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<SavingsGoal>()

                    SavingsGoalsUiState(isLoading = false, goals = goals)
                }
                _state.value = result

                runCatching {
                    database.savingsGoalDao().upsertAll(result.goals.map { it.toEntity() })
                }.onFailure { Log.w(TAG, "Failed to write savings goals cache", it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load savings goals", e)
                val hasCachedContent = _state.value.goals.isNotEmpty()
                _state.value = _state.value.copy(isLoading = false, error = if (hasCachedContent) null else GENERIC_LOAD_ERROR)
            }
        }
    }
}
