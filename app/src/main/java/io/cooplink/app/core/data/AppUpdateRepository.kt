package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.network.SupabaseClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppUpdateRepository"
private const val CONFIG_ROW_ID = "global"

@Serializable
data class AppReleaseConfig(
    val id: String = CONFIG_ROW_ID,
    val min_supported_version_code: Int = 1,
    val latest_version_code: Int? = null,
    val latest_version_name: String? = null,
    val update_message: String? = null,
    val play_store_url: String? = null,
)

// Gates app access on a minimum supported version code a super admin sets
// from Settings → App Updates (see AdminAppUpdateViewModel). Fails open on
// any error — a missing table, an unseeded row, or no connectivity must
// never lock users out; only an explicit fetched config that's actually
// behind does that.
@Singleton
class AppUpdateRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {
    suspend fun fetchConfig(): AppReleaseConfig? = runCatching {
        supabase.db["app_release_config"]
            .select { filter { eq("id", CONFIG_ROW_ID) } }
            .decodeSingleOrNull<AppReleaseConfig>()
    }.onFailure { Log.w(TAG, "Failed to fetch app release config", it) }.getOrNull()

    suspend fun updateConfig(
        minSupportedVersionCode: Int,
        latestVersionCode: Int?,
        latestVersionName: String?,
        updateMessage: String?,
        playStoreUrl: String?,
    ) {
        supabase.db["app_release_config"].update(
            buildJsonObject {
                put("min_supported_version_code", minSupportedVersionCode)
                put("latest_version_code", latestVersionCode)
                put("latest_version_name", latestVersionName)
                put("update_message", updateMessage)
                put("play_store_url", playStoreUrl)
            },
        ) { filter { eq("id", CONFIG_ROW_ID) } }
    }
}
