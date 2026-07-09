package io.cooplink.app.core.util

import android.content.Context
import android.content.Intent

/** Builds a CSV from [header]/[rows] and hands it to the system share sheet as plain text.
 * Pass [launch] (e.g. an ActivityResultLauncher::launch) to open the chooser through a
 * launcher that gives a "we've returned" callback instead of a plain fire-and-forget
 * startActivity — needed so an inactivity-timer pause around this call has somewhere to
 * resume from. */
fun shareCsv(
    context: Context,
    filename: String,
    header: List<String>,
    rows: List<List<String>>,
    launch: (Intent) -> Unit = { context.startActivity(it) },
) {
    val csv = buildString {
        appendLine(header.joinToString(",") { it.csvEscape() })
        rows.forEach { row -> appendLine(row.joinToString(",") { it.csvEscape() }) }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, filename)
        putExtra(Intent.EXTRA_TEXT, csv)
    }
    launch(Intent.createChooser(intent, "Share $filename"))
}

private fun String.csvEscape() = "\"${replace("\"", "\"\"")}\""
