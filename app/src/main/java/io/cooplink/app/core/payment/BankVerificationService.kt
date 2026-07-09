package io.cooplink.app.core.payment

import android.util.Log
import io.cooplink.app.core.network.SupabaseClient
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BankVerificationService"
private val json = Json { ignoreUnknownKeys = true }

data class BankOption(val name: String, val code: String)

@Serializable
data class BankAccountInfo(
    val accountName: String,
    val accountNumber: String,
    val bankCode: String,
)

@Serializable
private data class VerifyAccountResponse(
    val account_name: String? = null,
    val message: String? = null,
)

val NIGERIA_BANKS = listOf(
    BankOption("Access Bank", "044"),
    BankOption("GTBank", "058"),
    BankOption("First Bank", "011"),
    BankOption("Zenith Bank", "057"),
    BankOption("UBA", "033"),
    BankOption("Sterling Bank", "232"),
    BankOption("Stanbic IBTC", "221"),
    BankOption("Fidelity Bank", "070"),
    BankOption("Union Bank", "032"),
    BankOption("Ecobank", "050"),
    BankOption("Polaris Bank", "076"),
    BankOption("Keystone Bank", "082"),
    BankOption("FCMB", "214"),
    BankOption("Wema Bank", "035"),
    BankOption("Heritage Bank", "030"),
    BankOption("Opay", "100004"),
    BankOption("Kuda Bank", "50211"),
    BankOption("PalmPay", "100033"),
    BankOption("Moniepoint", "50515"),
)

/** Resolves an account number + bank code to the account holder's name before
 * a disbursement, so an admin can catch a typo'd account number before money
 * moves. Routed through a Supabase edge function rather than calling
 * Paystack directly — bank-resolution requires Paystack's *secret* key,
 * which can't be safely embedded in the app (anyone who decompiles the APK
 * would get it). No such edge function exists on this backend yet (confirmed
 * via a direct probe — HTTP 404), so this fails with a clear message until
 * one is added; it'll start working with no code changes once it exists. */
@Singleton
class BankVerificationService @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun verifyAccount(accountNumber: String, bankCode: String): Result<BankAccountInfo> = runCatching {
        val resp = supabase.functions.invoke(
            function = "verify-bank-account",
            body = buildJsonObject {
                put("account_number", accountNumber)
                put("bank_code", bankCode)
            },
        )
        val rawBody = resp.bodyAsText()
        Log.d(TAG, "verify-bank-account raw response: $rawBody")
        val parsed = json.decodeFromString<VerifyAccountResponse>(rawBody)
        val accountName = parsed.account_name
            ?: throw IllegalStateException(parsed.message ?: "Could not verify this account")

        BankAccountInfo(accountName = accountName, accountNumber = accountNumber, bankCode = bankCode)
    }.onFailure { Log.w(TAG, "Bank verification failed", it) }
}
