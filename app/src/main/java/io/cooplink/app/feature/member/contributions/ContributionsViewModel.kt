package io.cooplink.app.feature.member.contributions

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.CooperativeRepository
import io.cooplink.app.core.data.MemberRepository
import io.cooplink.app.core.domain.Contribution
import io.cooplink.app.core.domain.CooperativeBankAccount
import io.cooplink.app.core.domain.SavingsInterestMethod
import io.cooplink.app.core.network.NetworkMonitor
import io.cooplink.app.core.network.SupabaseClient
import io.cooplink.app.core.notifications.SoundManager
import io.cooplink.app.core.sync.AddContributionPayload
import io.cooplink.app.core.sync.OfflineQueueManager
import io.cooplink.app.core.sync.QueuedOperationType
import io.cooplink.app.core.util.ReceiptGenerator
import io.cooplink.app.core.util.retrying
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

private const val TAG = "ContributionsVM"
private const val GENERIC_LOAD_ERROR = "Could not load your contributions. Pull down to retry."
private val json = Json { ignoreUnknownKeys = true }

@Serializable
private data class NewContributionRequest(
    val member_id: String,
    val cooperative_id: String?,
    val amount: Double,
    // No default — see NewRepaymentRequest.type in AdminRepaymentsViewModel
    // for why a defaulted property is silently dropped from the request even
    // when the call site passes exactly that value.
    val status: String,
)

@Serializable
data class VirtualAccountInfo(
    val account_number: String? = null,
    val bank_name: String?      = null,
    val account_name: String?   = null,
)

data class ContributionsUiState(
    val isLoading: Boolean          = true,
    val contributions: List<Contribution> = emptyList(),
    val error: String?              = null,
    val isSubmitting: Boolean       = false,
    val submitError: String?        = null,
    val isGeneratingAccount: Boolean = false,
    val virtualAccount: VirtualAccountInfo? = null,
    val showContactAdminDialog: Boolean = false,
    val showSavingsGoalComingSoon: Boolean = false,
    val receiptUri: android.net.Uri? = null,
    val receiptAmount: String? = null,
    val bankAccounts: List<CooperativeBankAccount> = emptyList(),
    // Annualized projection at the cooperative's configured rate — only
    // populated when the admin has chosen "Simple Estimate" as the method;
    // null means either no rate is set or the cooperative uses manual
    // periodic crediting instead (which doesn't need a live estimate shown).
    val estimatedAnnualInterest: Double? = null,
)

@HiltViewModel
class ContributionsViewModel @Inject constructor(
    private val supabase: SupabaseClient,
    private val memberRepository: MemberRepository,
    private val cooperativeRepository: CooperativeRepository,
    private val networkMonitor: NetworkMonitor,
    private val offlineQueueManager: OfflineQueueManager,
    private val soundManager: SoundManager,
    val receiptGenerator: ReceiptGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(ContributionsUiState())
    val state: StateFlow<ContributionsUiState> = _state.asStateFlow()

    fun clearReceipt() { _state.value = _state.value.copy(receiptUri = null, receiptAmount = null) }

    init { load() }

    fun refresh() = load()

    // Directly inserts a pending contribution row — the initialize-payment edge
    // function is currently returning BOOT_ERROR (fails to start), so contributions
    // are recorded directly against the table rather than routed through it.
    fun addContribution(amount: Double) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, submitError = null)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")

                if (!networkMonitor.isOnline.value) {
                    // No point attempting a call that can't reach the server —
                    // queue it for SyncWorker to replay once back online, and
                    // show it optimistically now so the member isn't blocked.
                    offlineQueueManager.enqueue(
                        QueuedOperationType.ADD_CONTRIBUTION,
                        json.encodeToString(
                            AddContributionPayload.serializer(),
                            AddContributionPayload(member.id, member.cooperativeId, amount),
                        ),
                    )
                    val optimistic = Contribution(
                        id            = "pending-${System.currentTimeMillis()}",
                        memberId      = member.id,
                        amount        = amount,
                        status        = "pending (will sync when online)",
                        cooperativeId = member.cooperativeId,
                        createdAt     = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date()),
                    )
                    _state.value = _state.value.copy(
                        isSubmitting  = false,
                        contributions = listOf(optimistic) + _state.value.contributions,
                    )
                    soundManager.playSuccess()
                    return@launch
                }

                supabase.db["contributions"].insert(
                    NewContributionRequest(
                        member_id      = member.id,
                        cooperative_id = member.cooperativeId,
                        amount         = amount,
                        // Must be passed explicitly — kotlinx.serialization
                        // doesn't encode a property still at its default
                        // value, silently dropping it from the request and
                        // failing on a NOT NULL constraint (confirmed live
                        // for the identical pattern in AdminRepaymentsViewModel).
                        status         = "pending",
                    ),
                )
                _state.value = _state.value.copy(isSubmitting = false)
                soundManager.playSuccess()
                generateReceipt(member.fullName ?: "Member", member.displayId, "Contribution", amount)
                load()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add contribution", e)
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    submitError  = "Could not submit your contribution. Please try again.",
                )
            }
        }
    }

    fun clearSubmitError() {
        _state.value = _state.value.copy(submitError = null)
    }

    // A failure here shouldn't undo the contribution that already succeeded —
    // the receipt is a nice-to-have on top, not the source of truth.
    private fun generateReceipt(memberName: String, memberId: String, type: String, amount: Double) {
        runCatching {
            val now = java.text.SimpleDateFormat("MMM d, yyyy h:mm a", java.util.Locale.US).format(java.util.Date())
            val reference = "REF-${System.currentTimeMillis()}"
            val uri = receiptGenerator.generateReceipt(
                receiptNumber   = reference,
                memberName      = memberName,
                memberId        = memberId,
                cooperativeName = "CoopLink",
                transactionType = type,
                amount          = amount,
                date            = now,
                reference       = reference,
            )
            if (uri != null) {
                _state.value = _state.value.copy(receiptUri = uri, receiptAmount = amount.toNairaString())
            }
        }.onFailure { Log.w(TAG, "Failed to generate receipt", it) }
    }

    private fun Double.toNairaString(): String {
        val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
        fmt.minimumFractionDigits = 2; fmt.maximumFractionDigits = 2
        return "₦${fmt.format(this)}"
    }

    // create-virtual-account exists on the backend (confirmed reachable) — call
    // it for real, and only fall back to the "contact your admin" message if
    // the call itself fails (network, missing config on this cooperative, etc).
    fun generateVirtualAccount() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isGeneratingAccount = true)
            try {
                val member = memberRepository.findCurrentMember()
                    ?: throw IllegalStateException("Could not determine your member record")

                val resp = supabase.functions.invoke(
                    function = "create-virtual-account",
                    body = buildJsonObject {
                        put("member_id", member.id)
                        put("cooperative_id", member.cooperativeId)
                    },
                )
                val rawBody = resp.bodyAsText()
                Log.d(TAG, "create-virtual-account raw response: $rawBody")
                val info = json.decodeFromString<VirtualAccountInfo>(rawBody)
                _state.value = _state.value.copy(isGeneratingAccount = false, virtualAccount = info)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate virtual account", e)
                _state.value = _state.value.copy(isGeneratingAccount = false, showContactAdminDialog = true)
            }
        }
    }

    fun dismissContactAdminDialog() {
        _state.value = _state.value.copy(showContactAdminDialog = false)
    }

    // No savings_goals table and no members.savings_goal column exist anywhere
    // in the schema — this can't be persisted yet, so it's an honest gap rather
    // than a silent local-only fake.
    fun setGoalNotYetAvailable() {
        _state.value = _state.value.copy(showSavingsGoalComingSoon = true)
    }

    fun dismissSavingsGoalComingSoon() {
        _state.value = _state.value.copy(showSavingsGoalComingSoon = false)
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val (contributions, bankAccounts, estimatedInterest) = retrying {
                    val uid = supabase.auth.currentSessionOrNull()?.user?.id
                        ?: throw IllegalStateException("Not authenticated")

                    val member = memberRepository.findCurrentMember()
                    val memberId = member?.id ?: uid

                    val contributions = supabase.db["contributions"]
                        .select {
                            filter { eq("member_id", memberId) }
                            order("created_at", Order.DESCENDING)
                        }
                        .decodeList<Contribution>()

                    val bankAccounts = member?.cooperativeId
                        ?.let { memberRepository.fetchCooperativeBankAccounts(it) }
                        ?: emptyList()

                    val estimatedInterest = member?.cooperativeId
                        ?.let { cooperativeRepository.fetchCooperative(it) }
                        ?.takeIf { it.savingsInterestMethod == SavingsInterestMethod.SIMPLE_ESTIMATE && (it.savingsInterestRate ?: 0.0) > 0.0 }
                        ?.let { coop -> contributions.sumOf { it.amount } * ((coop.savingsInterestRate ?: 0.0) / 100.0) }

                    Triple(contributions, bankAccounts, estimatedInterest)
                }
                // .copy() rather than a fresh ContributionsUiState(...) — a
                // reload (pull-to-refresh, or the one addContribution() fires
                // after submitting) must not wipe a just-generated virtual
                // account or a just-shown receipt out from under the member.
                _state.value = _state.value.copy(
                    isLoading     = false,
                    contributions = contributions,
                    bankAccounts  = bankAccounts,
                    estimatedAnnualInterest = estimatedInterest,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contributions", e)
                _state.value = _state.value.copy(isLoading = false, error = GENERIC_LOAD_ERROR)
            }
        }
    }
}
