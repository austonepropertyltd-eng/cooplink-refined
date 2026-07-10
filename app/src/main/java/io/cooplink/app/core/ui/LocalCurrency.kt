package io.cooplink.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import io.cooplink.app.core.data.CurrencyProvider
import io.cooplink.app.core.domain.CurrencyConfig

val LocalCurrency = compositionLocalOf<CurrencyProvider> {
    error("No CurrencyProvider provided — wrap this content in CompositionLocalProvider(LocalCurrency provides ...)")
}

/** Reads the cooperative's current currency reactively — recomposes the
 * caller immediately when it changes (Settings picker or Realtime from
 * another device), unlike [CurrencyProvider.format] called on its own. */
@Composable
fun currentCurrency(): CurrencyConfig {
    val provider = LocalCurrency.current
    val config by provider.currency.collectAsState()
    return config
}
