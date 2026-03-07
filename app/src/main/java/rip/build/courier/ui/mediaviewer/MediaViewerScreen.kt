package rip.build.courier.ui.mediaviewer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MediaViewerScreen(
    onBack: () -> Unit,
    viewModel: MediaViewerViewModel = hiltViewModel()
) {
    val mediaItems by viewModel.mediaItems.collectAsState()
    val initialPageIndex by viewModel.initialPageIndex.collectAsState()
    val context = LocalContext.current

    var overlayVisible by remember { mutableStateOf(true) }
    var initialScrollDone by remember { mutableStateOf(false) }

    if (mediaItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    val pagerState = rememberPagerState(
        initialPage = initialPageIndex,
        pageCount = { mediaItems.size }
    )

    // Scroll to the correct initial page once media items load
    LaunchedEffect(initialPageIndex, mediaItems.size) {
        if (!initialScrollDone && mediaItems.isNotEmpty() && initialPageIndex > 0) {
            pagerState.scrollToPage(initialPageIndex)
            initialScrollDone = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
        ) { page ->
            val item = mediaItems[page]
            val isCurrentPage = pagerState.currentPage == page

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { overlayVisible = !overlayVisible }
            ) {
                if (item.isImage && item.localFilePath != null) {
                    ZoomableImageViewer(
                        filePath = item.localFilePath,
                        contentDescription = item.transferName
                    )
                } else if (item.isVideo && item.localFilePath != null) {
                    VideoPlayer(
                        filePath = item.localFilePath,
                        isVisible = isCurrentPage
                    )
                }
            }
        }

        val currentItem = mediaItems.getOrNull(pagerState.currentPage)

        MediaViewerOverlay(
            visible = overlayVisible,
            currentIndex = pagerState.currentPage,
            totalCount = mediaItems.size,
            onBack = onBack,
            onShare = {
                currentItem?.let { item ->
                    viewModel.getShareIntent(item)?.let { intent ->
                        context.startActivity(Intent.createChooser(intent, "Share"))
                    }
                }
            },
            onSave = {
                currentItem?.let { viewModel.saveToDevice(it) }
            }
        )
    }
}
