package io.cooplink.app.core.domain

data class CurrencyConfig(
    val code: String,
    val symbol: String,
    val name: String,
    val locale: String,
    val flag: String,
    val decimalPlaces: Int = 2,
)

object SupportedCurrencies {
    val all = listOf(
        CurrencyConfig(code = "NGN", symbol = "₦",    name = "Nigerian Naira",         locale = "en-NG", flag = "🇳🇬"),
        CurrencyConfig(code = "GHS", symbol = "GH₵",   name = "Ghanaian Cedi",          locale = "en-GH", flag = "🇬🇭"),
        CurrencyConfig(code = "KES", symbol = "KSh",   name = "Kenyan Shilling",        locale = "en-KE", flag = "🇰🇪"),
        CurrencyConfig(code = "UGX", symbol = "USh",   name = "Ugandan Shilling",       locale = "en-UG", flag = "🇺🇬", decimalPlaces = 0),
        CurrencyConfig(code = "TZS", symbol = "TSh",   name = "Tanzanian Shilling",     locale = "en-TZ", flag = "🇹🇿", decimalPlaces = 0),
        CurrencyConfig(code = "ZAR", symbol = "R",     name = "South African Rand",    locale = "en-ZA", flag = "🇿🇦"),
        CurrencyConfig(code = "XOF", symbol = "CFA",   name = "West African CFA Franc", locale = "fr-SN", flag = "🌍", decimalPlaces = 0),
        CurrencyConfig(code = "USD", symbol = "$",     name = "US Dollar",              locale = "en-US", flag = "🇺🇸"),
        CurrencyConfig(code = "GBP", symbol = "£",     name = "British Pound",          locale = "en-GB", flag = "🇬🇧"),
        CurrencyConfig(code = "EUR", symbol = "€",     name = "Euro",                   locale = "en-EU", flag = "🇪🇺"),
    )

    fun find(code: String): CurrencyConfig =
        all.find { it.code.equals(code, ignoreCase = true) } ?: all.first()

    fun fromCooperative(currency: String?, symbol: String? = null): CurrencyConfig {
        val config = find(currency ?: "NGN")
        return if (!symbol.isNullOrBlank()) config.copy(symbol = symbol) else config
    }
}
