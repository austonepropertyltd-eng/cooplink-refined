package io.cooplink.app.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

// The real, on-disk asset layout — nested under Marketing/ with sub-folders,
// not the flat marketing/ path some specs have assumed. Verified via a
// direct filesystem check before wiring this, since a wrong path here just
// throws FileNotFoundException at share/preview time.
private const val PRESENTATION_PDF = "Marketing/brochure/CoopLink_Presentation.pdf"
private const val MEMBER_JOURNEY_PNG = "Marketing/infographics/infographic_member_journey.png"
private const val STATS_OVERVIEW_PNG = "Marketing/infographics/infographic_stats.png"

val SLIDE_ASSET_PATHS: List<String> = listOf(
    "Marketing/slides/slide_01_cover.png",
    "Marketing/slides/slide_02_problem.png",
    "Marketing/slides/slide_03_solution.png",
    "Marketing/slides/slide_04_how_it_works.png",
    "Marketing/slides/slide_05_member_features.png",
    "Marketing/slides/slide_06_admin_features.png",
    "Marketing/slides/slide_07_security.png",
    "Marketing/slides/slide_08_who_its_for.png",
    "Marketing/slides/slide_09_pricing.png",
    "Marketing/slides/slide_10_closing.png",
)

enum class MarketingResourceType { PDF, IMAGE, IMAGE_SET }

data class MarketingResource(
    val id: String,
    val title: String,
    val description: String,
    val type: MarketingResourceType,
    val assetPaths: List<String>,
    val mimeType: String,
)

val MARKETING_RESOURCES = listOf(
    MarketingResource(
        id = "presentation",
        title = "Full Presentation (PDF)",
        description = "7-page professional presentation for government, MFBs and cooperatives",
        type = MarketingResourceType.PDF,
        assetPaths = listOf(PRESENTATION_PDF),
        mimeType = "application/pdf",
    ),
    MarketingResource(
        id = "member_journey",
        title = "Member Journey Infographic",
        description = "Step-by-step visual showing how members join and use CoopLink",
        type = MarketingResourceType.IMAGE,
        assetPaths = listOf(MEMBER_JOURNEY_PNG),
        mimeType = "image/png",
    ),
    MarketingResource(
        id = "stats_overview",
        title = "Statistics Overview",
        description = "CoopLink at a glance — key numbers and platform highlights",
        type = MarketingResourceType.IMAGE,
        assetPaths = listOf(STATS_OVERVIEW_PNG),
        mimeType = "image/png",
    ),
    MarketingResource(
        id = "all_slides",
        title = "Share All 10 Slides",
        description = "Share the complete slide deck with any contact",
        type = MarketingResourceType.IMAGE_SET,
        assetPaths = SLIDE_ASSET_PATHS,
        mimeType = "image/png",
    ),
)

@Singleton
class MarketingResourceManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cacheRoot: File
        get() = File(context.cacheDir, "marketing").apply { mkdirs() }

    /** Assets live inside the APK and can't be opened as a real [File] (which
     * both [FileProvider] and [PdfRenderer] require) — copy once into
     * cacheDir, reusing the copy on subsequent calls. */
    fun materialize(assetPath: String): File {
        val dest = File(cacheRoot, assetPath.substringAfterLast('/'))
        if (!dest.exists()) {
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return dest
    }

    fun shareUriFor(assetPath: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", materialize(assetPath))

    /** Builds a chooser [Intent] for one or more assets. The caller is
     * responsible for launching it (typically via a pause/resume-wrapped
     * ActivityResultLauncher, matching the rest of this app's share flows). */
    fun buildShareIntent(resource: MarketingResource, chooserTitle: String): Intent {
        val uris = ArrayList(resource.assetPaths.map { shareUriFor(it) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = resource.mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = resource.mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }
        intent.putExtra(Intent.EXTRA_SUBJECT, resource.title)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(intent, chooserTitle)
    }

    fun loadBitmap(assetPath: String): Bitmap? = runCatching {
        context.assets.open(assetPath).use { android.graphics.BitmapFactory.decodeStream(it) }
    }.getOrNull()

    fun renderPdfPages(assetPath: String): List<Bitmap> {
        val file = materialize(assetPath)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        }
    }
}
