package io.cooplink.app.core.domain

// Real values enforced by a CHECK constraint on cooperatives.organization_type
// (no default — every row now has one of these 7, and INSERT/UPDATE without
// a valid value is rejected with a 400 by the backend).
enum class OrganizationType(
    val key: String,
    val label: String,
    val description: String,
) {
    COOPERATIVE(
        key         = "cooperative",
        label       = "Cooperative Society",
        description = "Registered cooperative society",
    ),
    MICROFINANCE(
        key         = "microfinance",
        label       = "Microfinance Bank / MFB",
        description = "Licensed microfinance institution",
    ),
    INVESTMENT_CLUB(
        key         = "investment_club",
        label       = "Investment Club",
        description = "Group investment pool",
    ),
    THRIFT_AJO(
        key         = "thrift_ajo",
        label       = "Thrift / Ajo Group",
        description = "Rotating savings group (Ajo/Esusu)",
    ),
    SAVINGS_GROUP(
        key         = "savings_group",
        label       = "Savings Group",
        description = "Community savings group",
    ),
    SACCO(
        key         = "sacco",
        label       = "SACCO",
        description = "Savings and Credit Cooperative",
    ),
    NGO(
        key         = "ngo",
        label       = "NGO / Foundation",
        description = "Non-governmental organization",
    );

    companion object {
        fun from(key: String?): OrganizationType? =
            entries.find { it.key.equals(key, ignoreCase = true) }

        fun fromOrDefault(key: String?): OrganizationType = from(key) ?: COOPERATIVE
    }
}
