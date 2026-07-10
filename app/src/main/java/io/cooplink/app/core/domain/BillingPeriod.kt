package io.cooplink.app.core.domain

enum class BillingPeriod(
    val key: String,
    val label: String,
    val shortLabel: String,
    val months: Int,
    val defaultDiscount: Int,
    val badge: String,
) {
    MONTHLY(key = "monthly", label = "Monthly", shortLabel = "mo", months = 1, defaultDiscount = 0, badge = ""),
    QUARTERLY(key = "quarterly", label = "Quarterly", shortLabel = "3 mo", months = 3, defaultDiscount = 10, badge = "Save 10%"),
    BIANNUAL(key = "biannual", label = "6 Months", shortLabel = "6 mo", months = 6, defaultDiscount = 20, badge = "Save 20%"),
    ANNUAL(key = "annual", label = "Annual", shortLabel = "yr", months = 12, defaultDiscount = 35, badge = "🔥 Save 35%");

    companion object {
        fun from(key: String): BillingPeriod = entries.find { it.key == key } ?: MONTHLY
    }
}
