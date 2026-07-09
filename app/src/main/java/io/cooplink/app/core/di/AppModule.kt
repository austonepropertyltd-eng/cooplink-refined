package io.cooplink.app.core.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.cooplink.app.auth.AuthRepository
import io.cooplink.app.core.data.CoopLinkDatabase
import io.cooplink.app.core.data.SessionPreferences
import io.cooplink.app.core.data.SignedUrlManager
import io.cooplink.app.core.network.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient = SupabaseClient()

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): CoopLinkDatabase =
        Room.databaseBuilder(ctx, CoopLinkDatabase::class.java, CoopLinkDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides @Singleton
    fun provideAuthRepository(
        client: SupabaseClient,
        sessionPreferences: SessionPreferences,
        signedUrlManager: SignedUrlManager,
    ): AuthRepository = AuthRepository(client, sessionPreferences, signedUrlManager)
}

// ── Payment ───────────────────────────────────────────────────────────────────
// (PaymentManager and BiometricHelper are @Singleton with @Inject constructors
//  so Hilt creates them automatically — no @Provides needed here)
