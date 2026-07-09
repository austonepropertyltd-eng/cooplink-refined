package io.cooplink.app.core.util

import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Formats a Postgres/Supabase ISO timestamp ("2026-02-28T00:00:00...") as
 * "Feb 28, 2026". Only the date-only prefix is parsed — java.time would need
 * API 26+ and desugaring isn't enabled (minSdk 24), and the full timestamp's
 * variable fractional-seconds/timezone suffix isn't needed for a date label.
 */
fun formatIsoDate(iso: String?): String? {
    if (iso.isNullOrBlank() || iso.length < 10) return null
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val output = SimpleDateFormat("MMM d, yyyy", Locale.US)
        output.format(input.parse(iso.take(10)) ?: return null)
    }.getOrNull()
}
