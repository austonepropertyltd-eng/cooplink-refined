package io.cooplink.app.core.ui

import androidx.compose.runtime.compositionLocalOf
import io.cooplink.app.core.data.CurrencyProvider

val LocalCurrency = compositionLocalOf<CurrencyProvider> {
    error("No CurrencyProvider provided — wrap this content in CompositionLocalProvider(LocalCurrency provides ...)")
}
