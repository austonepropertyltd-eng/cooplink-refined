package io.cooplink.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingDataStore by preferencesDataStore("onboarding_prefs")

@Singleton
class OnboardingPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.onboardingDataStore
    private val keyDone = booleanPreferencesKey("onboarding_done")

    val isDoneFlow: Flow<Boolean> = store.data.map { it[keyDone] ?: false }

    suspend fun setDone() {
        store.edit { it[keyDone] = true }
    }
}
