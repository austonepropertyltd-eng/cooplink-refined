package io.cooplink.app.auth

import android.util.Log
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.domain.AuthUser
import io.cooplink.app.core.domain.UserRole
import io.cooplink.app.core.network.SupabaseClient
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AuthRepository"
private const val MEMBER_LOGIN_MAX_ATTEMPTS = 2   // initial attempt + 1 retry
private const val MEMBER_LOGIN_RETRY_DELAY_MS = 1_000L
private const val MEMBER_LOGIN_CALL_TIMEOUT_MS = 5_000L
private const val WARMUP_PING_TIMEOUT_MS = 4_000L
private const val HTTP_BAD_GATEWAY = 502

@Serializable
private data class MemberLoginResponse(
    val access_token: String,
    val refresh_token: String,
    val user_id: String? = null,
    val member_id: String? = null,
    val cooperative_id: String? = null,
)

@Serializable
private data class UserRoleRow(
    val role: String? = null,
    val cooperative_id: String? = null,
    val tenant_id: String? = null,
)

@Singleton
class AuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionPreferences: SessionPreferences,
    private val signedUrlManager: SignedUrlManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ── Member login ──────────────────────────────────────────────────────────
    // Each attempt is bounded by MEMBER_LOGIN_CALL_TIMEOUT_MS (5s) — shorter than
    // SupabaseClient's 15s client-wide timeout, which withTimeout can safely
    // override downward (confirmed: a shorter per-call timeout does cancel the
    // call early; only raising it above the client-wide setting doesn't work).
    // Retries once on 502 Bad Gateway (a known transient failure mode on cold
    // start) or a timeout. onRetrying fires before the retry so the UI can show
    // that a second attempt is in progress.
    suspend fun loginMember(
        memberId: String,
        password: String,
        onRetrying: (attempt: Int, maxAttempts: Int) -> Unit = { _, _ -> },
    ): Result<AuthUser> {
        var attempt = 0
        while (true) {
            attempt++
            val startedAt = System.currentTimeMillis()
            val result = runCatching {
                Log.d(TAG, "Member login: $memberId (attempt $attempt/$MEMBER_LOGIN_MAX_ATTEMPTS)")
                withTimeout(MEMBER_LOGIN_CALL_TIMEOUT_MS) {
                    val resp = supabase.functions.invoke(
                        function = "resolve-member-login",
                        body = buildJsonObject {
                            put("login_id", memberId)
                            put("password", password)
                        },
                    )
                    val data = json.decodeFromString<MemberLoginResponse>(resp.bodyAsText())
                    supabase.auth.importAuthToken(
                        accessToken  = data.access_token,
                        refreshToken = data.refresh_token,
                        retrieveUser = true,
                    )
                    fetchCurrentUser(forcedRole = UserRole.MEMBER)!!
                }
            }
            val elapsedMs = System.currentTimeMillis() - startedAt
            Log.d(TAG, "Member login attempt $attempt completed in ${elapsedMs}ms (success=${result.isSuccess})")

            val error = result.exceptionOrNull()
            val isBadGateway = error is RestException && error.statusCode == HTTP_BAD_GATEWAY
            val isTimeout = error is HttpRequestTimeoutException || error is TimeoutCancellationException
            val isTransient = isBadGateway || isTimeout
            if (result.isSuccess || !isTransient || attempt >= MEMBER_LOGIN_MAX_ATTEMPTS) {
                return result
            }

            Log.w(TAG, "resolve-member-login transient failure (attempt $attempt/$MEMBER_LOGIN_MAX_ATTEMPTS): $error — retrying")
            onRetrying(attempt + 1, MEMBER_LOGIN_MAX_ATTEMPTS)
            delay(MEMBER_LOGIN_RETRY_DELAY_MS * attempt)
        }
    }

    // Best-effort cold-start warm-up: fires the same edge function with an
    // intentionally invalid body just to wake up its container before the user
    // finishes typing their password. Any response (including an auth or
    // validation error) means the function is now warm; failures are swallowed
    // since this is purely a latency optimization, never a correctness check.
    suspend fun pingLoginFunction() {
        withTimeoutOrNull(WARMUP_PING_TIMEOUT_MS) {
            runCatching {
                supabase.functions.invoke(
                    function = "resolve-member-login",
                    body = buildJsonObject { put("ping", true) },
                )
            }
        }
    }

    // ── Admin login ───────────────────────────────────────────────────────────
    suspend fun loginAdmin(email: String, password: String): Result<AuthUser> =
        runCatching {
            Log.d(TAG, "Admin login: $email")
            supabase.auth.signInWith(Email) {
                this.email    = email
                this.password = password
            }
            fetchCurrentUser()!!
        }

    // ── Logout ────────────────────────────────────────────────────────────────
    suspend fun logout(): Result<Unit> = runCatching {
        signedUrlManager.clearCache()
        supabase.auth.signOut()
    }

    // ── Resolve current user + role ───────────────────────────────────────────
    suspend fun fetchCurrentUser(forcedRole: UserRole? = null): AuthUser? {
        val session = supabase.auth.currentSessionOrNull() ?: return null
        val uid     = session.user?.id ?: return null

        if (forcedRole != null) {
            sessionPreferences.saveCachedRole(forcedRole.name)
            return buildUser(uid, session, forcedRole, null, null)
        }

        // Query user_roles table. Select all columns rather than an explicit list —
        // the schema doesn't reliably have every column the app expects (e.g.
        // tenant_id isn't always present), and an explicit list hard-fails the
        // whole query if any one of them is missing.
        // Let failures (timeouts, network errors) propagate — silently treating
        // a failed lookup as "no row" risks misassigning a role.
        val row = supabase.db["user_roles"]
            .select {
                filter { eq("user_id", uid) }
            }
            .decodeSingleOrNull<UserRoleRow>()

        Log.d(TAG, "user_roles row for $uid → role='${row?.role}'")

        val role = if (row != null) mapRole(row.role) else resolveRoleFromMembers(uid)
        Log.d(TAG, "Resolved: $role  isAdmin=${role.isAdmin}")

        sessionPreferences.saveCachedRole(role.name)

        return buildUser(uid, session, role, row?.cooperative_id, row?.tenant_id)
    }

    // No user_roles row — fall back to checking whether this uid has a members
    // record. No members row means this is a staff/admin account.
    private suspend fun resolveRoleFromMembers(uid: String): UserRole {
        Log.d(TAG, "No user_roles row for $uid — checking members table")

        val memberRow = supabase.db["members"]
            .select(Columns.list("id")) {
                filter { eq("user_id", uid) }
            }
            .decodeSingleOrNull<Map<String, String>>()

        return if (memberRow == null) {
            Log.d(TAG, "No members row for $uid — treating as admin account → COOP_ADMIN")
            UserRole.COOP_ADMIN
        } else {
            Log.d(TAG, "members row found for $uid → MEMBER")
            UserRole.MEMBER
        }
    }

    private fun mapRole(raw: String?): UserRole = when {
        raw == null                                          -> UserRole.UNKNOWN
        raw.equals("super_admin",    ignoreCase = true)     -> UserRole.SUPER_ADMIN
        raw.equals("coop_admin",     ignoreCase = true)     -> UserRole.COOP_ADMIN
        raw.equals("admin",          ignoreCase = true)     -> UserRole.COOP_ADMIN
        raw.equals("cooperative_admin", ignoreCase = true)  -> UserRole.COOP_ADMIN
        raw.equals("treasurer",      ignoreCase = true)     -> UserRole.TREASURER
        raw.equals("accountant",     ignoreCase = true)     -> UserRole.ACCOUNTANT
        raw.equals("loan_officer",   ignoreCase = true)     -> UserRole.LOAN_OFFICER
        raw.equals("loan officer",   ignoreCase = true)     -> UserRole.LOAN_OFFICER
        raw.equals("auditor",        ignoreCase = true)     -> UserRole.AUDITOR
        raw.equals("member",         ignoreCase = true)     -> UserRole.MEMBER
        raw.equals("gov_admin",      ignoreCase = true)     -> UserRole.GOV_ADMIN
        raw.equals("fed_admin",      ignoreCase = true)     -> UserRole.FED_ADMIN
        else -> {
            // Unknown string but a row exists — treat as admin staff
            Log.w(TAG, "Unrecognised role '$raw' — defaulting to COOP_ADMIN")
            UserRole.COOP_ADMIN
        }
    }

    private fun buildUser(
        uid: String, session: UserSession,
        role: UserRole, coopId: String?, tenantId: String?,
    ) = AuthUser(
        id            = uid,
        email         = session.user?.email,
        memberId      = null,
        role          = role,
        cooperativeId = coopId,
        tenantId      = tenantId,
        fullName      = session.user?.userMetadata?.get("full_name")?.toString(),
        avatarUrl     = session.user?.userMetadata?.get("avatar_url")?.toString(),
    )

    fun isLoggedIn(): Boolean = supabase.auth.currentSessionOrNull() != null

    // ── Cooperative scoping (admin screens) ───────────────────────────────────
    suspend fun currentCooperativeId(): String? {
        val uid = supabase.auth.currentSessionOrNull()?.user?.id ?: return null
        return supabase.db["user_roles"]
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull<UserRoleRow>()
            ?.cooperative_id
    }

    suspend fun resetMemberPassword(memberId: String): Result<Unit> = runCatching {
        supabase.functions.invoke(
            "reset-member-password",
            body = buildJsonObject { put("member_id", memberId) },
        )
    }

    suspend fun resetAdminPassword(email: String): Result<Unit> = runCatching {
        supabase.auth.resetPasswordForEmail(email)
    }
}
