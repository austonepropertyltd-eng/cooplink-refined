package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.domain.MemberDetails
import io.cooplink.app.core.network.SupabaseClient
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SignedUrlManager"
private const val SIGN_URLS_TIMEOUT_MS = 8_000L
// The edge function issues 1hr tokens; cache for 50 minutes so a URL is
// always refreshed well before it would actually expire.
private const val CACHE_TTL_SECONDS = 3_000L

data class SignUrlPath(val bucket: String, val path: String)

@Serializable
private data class SignedUrlResult(
    val bucket: String,
    val path: String,
    @SerialName("signed_url") val signedUrl: String? = null,
    val error: String? = null,
)

@Serializable
private data class SignUrlResponse(val signed: List<SignedUrlResult> = emptyList())

/**
 * Mints short-lived signed URLs for the private "avatars" and "kyc-documents"
 * storage buckets via the server-side `sign-urls` edge function — the client
 * never calls Supabase Storage's sign endpoint directly, since that function
 * is the one place RLS-equivalent ownership/role checks (KYC docs: owner or
 * coop_admin/super_admin; avatars: owner only) are enforced.
 */
@Singleton
class SignedUrlManager @Inject constructor(
    private val supabase: SupabaseClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // path key ("$bucket/$path") -> (signedUrl, expiryEpochSeconds)
    private val cache = mutableMapOf<String, Pair<String, Long>>()

    suspend fun getSignedUrl(bucket: String, path: String?): String? {
        if (path.isNullOrBlank()) return null

        // Already a full URL — if it's a still-valid signed URL, use it as-is;
        // otherwise try to recover the underlying storage path and re-sign it.
        if (path.startsWith("http://") || path.startsWith("https://")) {
            if (path.contains("token=")) return path
            val extracted = extractPathFromUrl(path, bucket) ?: return path
            return getSignedUrl(bucket, extracted)
        }

        if (!isAuthenticated()) {
            Log.w(TAG, "Not authenticated — cannot sign URL for $bucket/$path")
            return null
        }

        val cacheKey = "$bucket/$path"
        val now = System.currentTimeMillis() / 1000
        cache[cacheKey]?.let { (url, expiry) ->
            if (now < expiry - 300) {
                Log.d(TAG, "Cache hit: $cacheKey")
                return url
            }
        }

        return batchSign(listOf(SignUrlPath(bucket, path)))[cacheKey]
    }

    /** Signs many paths in a single round trip. Returns a map keyed by
     * "$bucket/$path" (rather than a positional list) so callers can't get
     * silently misaligned if the function reorders or drops a failed entry. */
    suspend fun batchSign(paths: List<SignUrlPath>): Map<String, String?> {
        val distinct = paths.distinctBy { "${it.bucket}/${it.path}" }
        if (distinct.isEmpty() || !isAuthenticated()) return emptyMap()

        return try {
            val resp = withTimeout(SIGN_URLS_TIMEOUT_MS) {
                supabase.functions.invoke(
                    function = "sign-urls",
                    body = buildJsonObject {
                        put("paths", buildJsonArray {
                            distinct.forEach { p ->
                                add(buildJsonObject {
                                    put("bucket", p.bucket)
                                    put("path", p.path)
                                })
                            }
                        })
                    },
                )
            }
            val parsed = json.decodeFromString<SignUrlResponse>(resp.bodyAsText())
            val now = System.currentTimeMillis() / 1000
            parsed.signed.associate { item ->
                val key = "${item.bucket}/${item.path}"
                if (item.signedUrl != null) cache[key] = item.signedUrl to (now + CACHE_TTL_SECONDS)
                key to item.signedUrl
            }
        } catch (e: Exception) {
            Log.w(TAG, "sign-urls batch failed for ${distinct.size} path(s)", e)
            distinct.associate { "${it.bucket}/${it.path}" to null }
        }
    }

    suspend fun getAvatarUrl(userId: String, existingPath: String? = null): String? {
        val path = existingPath?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "$userId/avatar.jpg"
        return getSignedUrl("avatars", path)
    }

    suspend fun getKycDocUrl(userId: String, memberId: String, docType: String = "id_document"): String? {
        val path = when (docType) {
            "selfie" -> "$userId/selfies/$memberId-selfie.jpg"
            else     -> "$userId/id-docs/$memberId-id.jpg"
        }
        return getSignedUrl("kyc-documents", path)
    }

    /** Batch-signs every member's avatar in one call — keyed by member id so
     * admin list screens can look results up per row after a single fetch. */
    suspend fun batchSignAvatars(members: List<MemberDetails>): Map<String, String?> {
        val targets = members.mapNotNull { m ->
            val userId = m.userId ?: return@mapNotNull null
            val path = m.avatarUrl?.takeIf { it.isNotBlank() && !it.startsWith("http") } ?: "$userId/avatar.jpg"
            m.id to SignUrlPath("avatars", path)
        }
        if (targets.isEmpty()) return emptyMap()

        val signed = batchSign(targets.map { it.second })
        return targets.associate { (memberId, p) -> memberId to signed["${p.bucket}/${p.path}"] }
    }

    // Supabase storage URL patterns:
    // .../storage/v1/object/public/{bucket}/{path}
    // .../storage/v1/object/authenticated/{bucket}/{path}
    // .../storage/v1/object/sign/{bucket}/{path}
    private fun extractPathFromUrl(url: String, bucket: String): String? {
        listOf(
            "/storage/v1/object/public/$bucket/",
            "/storage/v1/object/authenticated/$bucket/",
            "/storage/v1/object/sign/$bucket/",
            "/storage/v1/object/$bucket/",
        ).forEach { pattern ->
            val idx = url.indexOf(pattern)
            if (idx >= 0) return url.substring(idx + pattern.length).substringBefore("?")
        }
        return null
    }

    private fun isAuthenticated(): Boolean = supabase.auth.currentSessionOrNull() != null

    fun clearCache() = cache.clear()
}
