package io.cooplink.app.core.data

import android.util.Log
import io.cooplink.app.core.domain.CurrencyConfig
import io.cooplink.app.core.domain.SupportedCurrencies
import io.cooplink.app.core.network.SupabaseClient
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "CurrencyProvider"

@Serializable
private data class CooperativeCurrencyRow(
    val currency: String? = null,
    val currency_symbol: String? = null,
)

/** Holds the cooperative's active currency and reacts to changes made from
 * this device or any other client (web, another admin) via Supabase
 * Realtime — every screen reading [currency] recomposes the moment
 * cooperatives.currency/currency_symbol changes. */
@Singleton
class CurrencyProvider @Inject constructor(
    private val supabase: SupabaseClient,
    private val sessionPrefs: SessionPreferences,
) {
    private val _currency = MutableStateFlow(SupportedCurrencies.find("NGN"))
    val currency: StateFlow<CurrencyConfig> = _currency.asStateFlow()

    private var realtimeChannel: RealtimeChannel? = null

    suspend fun loadFromCooperative(cooperativeId: String) {
        try {
            val row = supabase.db["cooperatives"]
                .select(Columns.list("currency", "currency_symbol")) {
                    filter { eq("id", cooperativeId) }
                }
                .decodeSingleOrNull<CooperativeCurrencyRow>()

            val config = SupportedCurrencies.fromCooperative(row?.currency, row?.currency_symbol)
            _currency.value = config
            sessionPrefs.saveCurrency(config.code, config.symbol)
            Log.d(TAG, "Loaded: ${config.code} ${config.symbol} ${config.flag}")
        } catch (e: Exception) {
            val cachedCode = sessionPrefs.savedCurrencyFlow.first()
            val cachedSymbol = sessionPrefs.savedCurrencySymbolFlow.first()
            _currency.value = SupportedCurrencies.fromCooperative(cachedCode, cachedSymbol)
            Log.w(TAG, "Using cached currency after load failure", e)
        }
    }

    /** Reacts immediately when the currency changes on the web platform,
     * another admin's device, or the Currency Selector in Settings. */
    fun subscribeToChanges(cooperativeId: String, scope: CoroutineScope) {
        if (realtimeChannel != null) return
        scope.launch {
            try {
                val ch = supabase.realtime.channel("coop_currency_$cooperativeId")
                ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "cooperatives"
                    filter("id", FilterOperator.EQ, cooperativeId)
                }.onEach { action ->
                    val row = runCatching { action.decodeRecord<CooperativeCurrencyRow>() }.getOrNull() ?: return@onEach
                    val config = SupportedCurrencies.fromCooperative(row.currency, row.currency_symbol)
                    _currency.value = config
                    Log.d(TAG, "Real-time update: ${config.flag} ${config.code}")
                }.launchIn(scope)
                ch.subscribe()
                realtimeChannel = ch
                Log.d(TAG, "Subscribed to real-time currency changes")
            } catch (e: Exception) {
                Log.e(TAG, "Realtime currency subscription failed", e)
            }
        }
    }

    suspend fun unsubscribe() {
        realtimeChannel?.let { runCatching { it.unsubscribe() } }
        realtimeChannel = null
    }

    /** Updates Supabase (which broadcasts to every other client via
     * Realtime) and applies the change locally right away rather than
     * waiting for our own echo. */
    suspend fun setCurrency(cooperativeId: String, config: CurrencyConfig) {
        supabase.db["cooperatives"].update(
            CooperativeCurrencyRow(currency = config.code, currency_symbol = config.symbol),
        ) { filter { eq("id", cooperativeId) } }

        _currency.value = config
        sessionPrefs.saveCurrency(config.code, config.symbol)
        Log.d(TAG, "Switched to: ${config.flag} ${config.code}")
    }

    fun format(amount: Double): String {
        val config = _currency.value
        val fmt = java.text.NumberFormat.getNumberInstance(java.util.Locale.US)
        fmt.minimumFractionDigits = config.decimalPlaces
        fmt.maximumFractionDigits = config.decimalPlaces
        return "${config.symbol}${fmt.format(amount)}"
    }

    fun formatShort(amount: Double): String {
        val symbol = _currency.value.symbol
        return when {
            amount >= 1_000_000_000 -> "$symbol${"%.1f".format(amount / 1_000_000_000)}B"
            amount >= 1_000_000     -> "$symbol${"%.1f".format(amount / 1_000_000)}M"
            amount >= 1_000         -> "$symbol${"%.1f".format(amount / 1_000)}K"
            else                    -> format(amount)
        }
    }

    fun currentSymbol(): String = _currency.value.symbol
    fun currentCode(): String = _currency.value.code
    fun currentFlag(): String = _currency.value.flag
}
