package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.domain.IdType
import io.cooplink.app.core.domain.KycData
import io.cooplink.app.core.domain.KycStatus
import io.cooplink.app.core.network.SupabaseClient
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KycRepository"

// All real columns on `members` (confirmed via schema probing): id_type,
// id_number, id_document_url, selfie_url, bvn, nin, kyc_status,
// kyc_submitted_at, kyc_verified_at, kyc_notes. user_id is needed to derive
// the {authUserId}/... storage paths the sign-urls edge function expects.
@Serializable
private data class KycColumnsRow(
    val id: String?                = null,
    val user_id: String?           = null,
    val id_type: String?           = null,
    val id_number: String?         = null,
    val id_document_url: String?   = null,
    val selfie_url: String?        = null,
    val bvn: String?               = null,
    val nin: String?                = null,
    val kyc_status: String?        = null,
    val kyc_submitted_at: String?  = null,
    val kyc_verified_at: String?   = null,
    val kyc_notes: String?         = null,
)

private val COLUMNS = Columns.list(
    "id", "user_id", "id_type", "id_number", "id_document_url", "selfie_url",
    "bvn", "nin", "kyc_status", "kyc_submitted_at", "kyc_verified_at", "kyc_notes",
)

// java.time needs API 26+ and desugaring isn't enabled (minSdk 24) — a plain
// SimpleDateFormat UTC timestamp is used instead, matching the pattern
// established elsewhere in this codebase (see ProfileViewModel).
private fun nowIso(): String {
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return fmt.format(java.util.Date())
}

@Singleton
class KycRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val signedUrlManager: SignedUrlManager,
) {
    private suspend fun KycColumnsRow.toKycData(preferredUid: String? = null): KycData = coroutineScope {
        val idType = id_type?.let { raw -> IdType.entries.find { it.name.equals(raw, ignoreCase = true) } }
        val uid = preferredUid ?: user_id ?: id

        val idDocDeferred = async { uid?.let { signedUrlManager.getKycDocUrl(it, id ?: it, "id_document") } }
        val selfieDeferred = async { uid?.let { signedUrlManager.getKycDocUrl(it, id ?: it, "selfie") } }

        KycData(
            status         = KycStatus.fromRaw(kyc_status),
            idType         = idType,
            idNumber       = id_number,
            idDocumentUrl  = if (!id_document_url.isNullOrBlank()) idDocDeferred.await() else null,
            selfieUrl      = if (!selfie_url.isNullOrBlank()) selfieDeferred.await() else null,
            bvn            = bvn,
            nin            = nin,
            submittedAt    = kyc_submitted_at,
            verifiedAt     = kyc_verified_at,
            rejectionReason = kyc_notes,
        )
    }

    // Always "my own" record (the member self-service screen) — the live
    // session's auth uid is guaranteed to match wherever uploadKycImage()
    // actually wrote to, unlike members.user_id, which is null/stale on some
    // migrated rows and would otherwise sign a URL for a path that was never
    // written (see KycViewModel.uploadIdDocument for the matching upload-time
    // fix). getKycDataForCooperative (admin, other members) can't use this
    // shortcut and keeps the user_id ?: id fallback.
    suspend fun getKycData(memberId: String): KycData = runCatching {
        val result = supabase.db["members"].select(COLUMNS) { filter { eq("id", memberId) } }
        val row = result.decodeSingleOrNull<KycColumnsRow>() ?: return@runCatching KycData()
        row.toKycData(preferredUid = supabase.auth.currentSessionOrNull()?.user?.id)
    }.onFailure { Log.w(TAG, "Failed to load KYC data for $memberId", it) }.getOrDefault(KycData())

    /** All members of a cooperative with a KYC submission, keyed by member id
     * — used for the admin review list. */
    suspend fun getKycDataForCooperative(cooperativeId: String): Map<String, KycData> = runCatching {
        val result = supabase.db["members"].select(COLUMNS) { filter { eq("cooperative_id", cooperativeId) } }
        result.decodeList<KycColumnsRow>()
            .filter { !it.id.isNullOrBlank() && it.kyc_status != null }
            .associate { row -> row.id!! to row.toKycData() }
    }.onFailure { Log.w(TAG, "Failed to load KYC data for cooperative $cooperativeId", it) }.getOrDefault(emptyMap())

    /** Uploads to the private "kyc-documents" bucket at the RLS-required path
     * ({authUserId}/id-docs/... or {authUserId}/selfies/...) and returns the
     * raw storage path (not a public URL — the bucket is private; display
     * URLs are resolved on demand via SignedUrlManager). */
    suspend fun uploadKycImage(memberId: String, bytes: ByteArray, docType: String): Result<String> = runCatching {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id
            ?: throw IllegalStateException("Not authenticated")
        val path = when (docType) {
            "selfie" -> "$uid/selfies/$memberId-selfie.jpg"
            else     -> "$uid/id-docs/$memberId-id.jpg"
        }
        supabase.storage.from("kyc-documents").upload(path, bytes) { upsert = true }
        path
    }.onFailure { Log.w(TAG, "KYC image upload ($docType) for $memberId failed", it) }

    // Direct UPDATEs to `members` are blocked by RLS — this RPC is the only
    // path members have to edit their own row, scoped server-side to
    // auth.uid() (memberId is accepted here only for the failure log, not
    // sent to the function).
    suspend fun submitKyc(
        memberId: String,
        idType: IdType,
        idNumber: String,
        idDocumentPath: String?,
    ): Result<Unit> = runCatching {
        supabase.db.rpc(
            "update_member_self",
            buildJsonObject {
                putJsonObject("p_updates") {
                    put("id_type", idType.name)
                    put("id_number", idNumber)
                    // Omitted (not sent as explicit null) when there's no new
                    // upload this session — a resubmission (e.g. correcting
                    // the ID number after a rejection) would otherwise wipe
                    // out an already-uploaded document that's still valid.
                    idDocumentPath?.let { put("id_document_url", it) }
                    put("kyc_status", "pending")
                    put("kyc_submitted_at", nowIso())
                }
            },
        )
        Unit
    }.onFailure { Log.e(TAG, "Failed to submit KYC for $memberId", it) }

    suspend fun approveKyc(memberId: String): Result<Unit> = runCatching {
        supabase.db["members"].update(
            buildJsonObject {
                put("kyc_status", "verified")
                put("kyc_verified_at", nowIso())
            },
        ) { filter { eq("id", memberId) } }
        Unit
    }.onFailure { Log.e(TAG, "approveKyc failed for $memberId", it) }

    suspend fun rejectKyc(memberId: String, reason: String): Result<Unit> = runCatching {
        supabase.db["members"].update(
            buildJsonObject { put("kyc_status", "rejected"); put("kyc_notes", reason) },
        ) { filter { eq("id", memberId) } }
        Unit
    }.onFailure { Log.e(TAG, "rejectKyc failed for $memberId", it) }

    /** Best-effort SMS notification — failure here shouldn't undo an
     * already-recorded approve/reject decision, so it's swallowed silently. */
    suspend fun notifyKycDecision(phone: String?, message: String) {
        if (phone.isNullOrBlank()) return
        runCatching {
            supabase.functions.invoke(
                "send-sms",
                body = buildJsonObject {
                    put("recipients", kotlinx.serialization.json.buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(phone)) })
                    put("message", message)
                },
            )
        }.onFailure { Log.w(TAG, "KYC decision SMS notification failed", it) }
    }
}
