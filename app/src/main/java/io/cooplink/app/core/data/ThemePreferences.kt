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

private val Context.themeDataStore by preferencesDataStore("theme_prefs")

@Singleton
class ThemePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val store = context.themeDataStore
    private val keyIsDark = booleanPreferencesKey("is_dark")

    // Member app has always been dark-only until now, so default to dark —
    // existing users see no change unless they explicitly opt into light.
    val isDarkFlow: Flow<Boolean> = store.data.map { it[keyIsDark] ?: true }

    suspend fun setDark(dark: Boolean) {
        store.edit { it[keyIsDark] = dark }
    }
}
