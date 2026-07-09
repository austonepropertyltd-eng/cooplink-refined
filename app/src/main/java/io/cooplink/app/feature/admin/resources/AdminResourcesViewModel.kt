package io.cooplink.app.feature.admin.resources

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cooplink.app.core.security.InactivityManager
import io.cooplink.app.core.util.MarketingResourceManager
import io.cooplink.app.core.util.MarketingResourceType
import io.cooplink.app.core.util.SLIDE_ASSET_PATHS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AdminResourcesViewModel @Inject constructor(
    val resourceManager: MarketingResourceManager,
    val inactivityManager: InactivityManager,
) : ViewModel() {

    private val _slides = MutableStateFlow<List<Bitmap>>(emptyList())
    val slides: StateFlow<List<Bitmap>> = _slides.asStateFlow()

    private val _fullScreenBitmaps = MutableStateFlow<List<Bitmap>?>(null)
    val fullScreenBitmaps: StateFlow<List<Bitmap>?> = _fullScreenBitmaps.asStateFlow()

    init {
        viewModelScope.launch {
            _slides.value = withContext(Dispatchers.IO) {
                SLIDE_ASSET_PATHS.mapNotNull { resourceManager.loadBitmap(it) }
            }
        }
    }

    fun showFullScreen(assetPath: String, type: MarketingResourceType) {
        viewModelScope.launch {
            _fullScreenBitmaps.value = withContext(Dispatchers.IO) {
                when (type) {
                    MarketingResourceType.PDF -> resourceManager.renderPdfPages(assetPath)
                    MarketingResourceType.IMAGE, MarketingResourceType.IMAGE_SET ->
                        listOfNotNull(resourceManager.loadBitmap(assetPath))
                }
            }
        }
    }

    fun dismissFullScreen() {
        _fullScreenBitmaps.value = null
    }
}
