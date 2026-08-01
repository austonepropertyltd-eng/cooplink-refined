package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.domain.Cooperative
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CooperativeRepository"

// get_member_cooperative(p_user_id) returns a lean subset of `cooperatives`
// (confirmed via schema probing: cooperative_id, name, logo_url,
// whatsapp_number, currency, primary_color, organization_type) — enough for
// shell branding, but missing fields (slug, address, phone, email,
// secondary_color) that admin screens editing the full record still need via
// fetchCooperative(id).
@Serializable
private data class MemberCooperativeRow(
    @SerialName("cooperative_id")  val cooperativeId: String?  = null,
    val name: String?                                          = null,
    @SerialName("logo_url")        val logoUrl: String?        = null,
    @SerialName("whatsapp_number") val whatsappNumber: String? = null,
    val currency: String?                                      = null,
    @SerialName("primary_color")   val primaryColor: String?   = null,
    @SerialName("organization_type") val organizationType: String? = null,
)

@Singleton
class CooperativeRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun fetchCooperative(cooperativeId: String): Cooperative? = runCatching {
        val result = supabase.db["cooperatives"].select { filter { eq("id", cooperativeId) } }
        Log.d(TAG, "cooperatives raw response for $cooperativeId: ${result.data}")
        result.decodeSingleOrNull<Cooperative>()
    }.onFailure { Log.w(TAG, "Failed to fetch cooperative $cooperativeId", it) }.getOrNull()

    /** Every cooperative on the platform — for the super-admin cooperative
     * switcher only. An empty result for a real super admin account means
     * RLS isn't granting them read access to other cooperatives' rows, which
     * would need a backend fix, not a client one. */
    suspend fun fetchAllCooperatives(): List<Cooperative> = runCatching {
        supabase.db["cooperatives"].select().decodeList<Cooperative>()
    }.onFailure { Log.w(TAG, "Failed to fetch all cooperatives", it) }.getOrDefault(emptyList())

    /** Single-RPC-call cooperative lookup for the signed-in user — meant for
     * shell branding (name/logo/color), which doesn't need the full record.
     * The function is scoped to `members` internally, so it returns nothing
     * for staff/admin accounts (which have no members row); callers should
     * fall back to [fetchCooperative] with a separately-resolved cooperative
     * id in that case rather than treating a null result as an error. */
    suspend fun fetchCooperativeForUser(userId: String): Cooperative? = runCatching {
        val result = supabase.db.rpc(
            "get_member_cooperative",
            buildJsonObject { put("p_user_id", userId) },
        )
        val row = result.decodeList<MemberCooperativeRow>().firstOrNull() ?: return@runCatching null
        val id = row.cooperativeId ?: return@runCatching null
        Cooperative(
            id               = id,
            name             = row.name,
            whatsappNumber   = row.whatsappNumber,
            currency         = row.currency ?: "NGN",
            logoUrl          = row.logoUrl,
            primaryColor     = row.primaryColor,
            organizationType = row.organizationType,
        )
    }.onFailure { Log.w(TAG, "get_member_cooperative RPC failed for $userId", it) }.getOrNull()
}
