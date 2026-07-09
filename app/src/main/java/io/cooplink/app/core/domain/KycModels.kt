package io.cooplink.app.core.domain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

// members.kyc_status is now a real column (confirmed via schema probing) —
// mapped from its raw string value via fromRaw() rather than derived
// client-side from which fields are populated.
enum class KycStatus {
    NOT_SUBMITTED,
    PENDING,
    VERIFIED,
    REJECTED;

    val label: String get() = when (this) {
        NOT_SUBMITTED -> "Not Submitted"
        PENDING       -> "Under Review"
        VERIFIED      -> "Verified"
        REJECTED      -> "Rejected"
    }

    val color: Color get() = when (this) {
        NOT_SUBMITTED -> Color(0xFF718096)
        PENDING       -> Color(0xFFF4B400)
        VERIFIED      -> Color(0xFF1FA67A)
        REJECTED      -> Color(0xFFE53E3E)
    }

    val icon: ImageVector get() = when (this) {
        NOT_SUBMITTED -> Icons.Default.HourglassEmpty
        PENDING       -> Icons.Default.Schedule
        VERIFIED      -> Icons.Default.VerifiedUser
        REJECTED      -> Icons.Default.Cancel
    }

    companion object {
        fun fromRaw(raw: String?): KycStatus = when {
            raw == null -> NOT_SUBMITTED
            raw.equals("verified", ignoreCase = true)      -> VERIFIED
            raw.equals("approved", ignoreCase = true)      -> VERIFIED
            raw.equals("rejected", ignoreCase = true)      -> REJECTED
            raw.equals("pending", ignoreCase = true)       -> PENDING
            raw.equals("under_review", ignoreCase = true)  -> PENDING
            raw.equals("submitted", ignoreCase = true)     -> PENDING
            raw.equals("not_submitted", ignoreCase = true) -> NOT_SUBMITTED
            else -> NOT_SUBMITTED
        }
    }
}

/** Small color-coded status chip — used anywhere a member's KYC state needs
 * a compact inline indicator (admin member lists, KYC review). */
@Composable
fun KycBadge(status: KycStatus, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = status.color.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(status.icon, null, tint = status.color, modifier = Modifier.size(12.dp))
            Text(status.label, style = MaterialTheme.typography.labelSmall, color = status.color)
        }
    }
}

enum class IdType(val label: String) {
    NIN("National ID (NIN)"),
    BVN("Bank Verification Number (BVN)"),
    PASSPORT("International Passport"),
    DRIVERS_LICENSE("Driver's License"),
    VOTERS_CARD("Voter's Card"),
}

// bvn/nin/selfie_url/kyc_submitted_at/kyc_verified_at/kyc_notes are now real
// columns on `members` (confirmed via schema probing) — idDocumentUrl/
// selfieUrl/avatarUrl are pre-signed, directly-displayable URLs (resolved by
// KycRepository via SignedUrlManager), not raw storage paths.
data class KycData(
    val status: KycStatus       = KycStatus.NOT_SUBMITTED,
    val idType: IdType?         = null,
    val idNumber: String?       = null,
    val idDocumentUrl: String?  = null,
    val selfieUrl: String?      = null,
    val avatarUrl: String?      = null,
    val bvn: String?            = null,
    val nin: String?            = null,
    val submittedAt: String?    = null,
    val verifiedAt: String?     = null,
    val rejectionReason: String? = null,
)
