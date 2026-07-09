package io.cooplink.app.core.network

import io.cooplink.app.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class SupabaseClient @Inject constructor() {

    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        // Default is 10s; every screen's retrying{} helper only helps once a
        // call actually fails, so a bounded timeout matters more than usual
        // here — without one a dead/slow connection can hang indefinitely.
        requestTimeout = 15.seconds

        install(Auth) {
            autoSaveToStorage = true
            alwaysAutoRefresh = true
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
    }

    val auth      get() = client.auth
    val db        get() = client.postgrest
    val realtime  get() = client.realtime
    val storage   get() = client.storage
    val functions get() = client.functions
}
