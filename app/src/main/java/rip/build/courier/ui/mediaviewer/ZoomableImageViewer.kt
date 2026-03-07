package rip.build.courier.ui.mediaviewer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.rememberZoomableState
import java.io.File

@Composable
fun ZoomableImageViewer(
    filePath: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val zoomableState = rememberZoomableState(
        zoomSpec = ZoomSpec(maxZoomFactor = 5f)
    )

    ZoomableAsyncImage(
        model = File(filePath),
        contentDescription = contentDescription,
        modifier = modifier.fillMaxSize(),
        contentScale = ContentScale.Fit,
        state = rememberZoomableImageState(zoomableState),
        onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 2f)
    )
}
