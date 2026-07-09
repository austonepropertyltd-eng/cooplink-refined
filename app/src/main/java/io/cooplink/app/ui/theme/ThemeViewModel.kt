package io.cooplink.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.data.ThemePreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val prefs: ThemePreferences,
) : ViewModel() {

    val isDark: StateFlow<Boolean> = prefs.isDarkFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun toggle() {
        viewModelScope.launch {
            prefs.setDark(!isDark.value)
        }
    }
}
