package rip.build.courier.ui.chatdetail

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import rip.build.courier.domain.model.AttachmentInfo

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinkPreviewCard(
    url: String,
    title: String?,
    subtitle: String?,
    imageAttachment: AttachmentInfo?,
    baseUrl: String?,
    bubbleColor: Color,
    textColor: Color,
    onLongPress: (() -> Unit)?,
    maxBubbleWidth: Dp,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val domain = try {
        java.net.URI(url).host?.removePrefix("www.") ?: url
    } catch (_: Exception) {
        url
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxThumbHeight = minOf(maxBubbleWidth, screenHeight * 0.35f)
    val minThumbHeight = 80.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { uriHandler.openUri(url) },
                onLongClick = onLongPress
            )
    ) {
        if (imageAttachment != null && baseUrl != null && (imageAttachment.isDownloaded || imageAttachment.guid.isNotBlank())) {
            val imageModel: Any = if (imageAttachment.isDownloaded) {
                java.io.File(imageAttachment.localFilePath!!)
            } else {
                "$baseUrl/api/attachments/${imageAttachment.guid}"
            }
            AdaptiveThumbnail(
                imageModel = imageModel,
                contentDescription = title ?: domain,
                maxThumbHeight = maxThumbHeight,
                minThumbHeight = minThumbHeight
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = domain,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (title != null || subtitle != null) {
                    Modifier.padding(top = 2.dp)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun AdaptiveThumbnail(
    imageModel: Any,
    contentDescription: String,
    maxThumbHeight: Dp,
    minThumbHeight: Dp
) {
    SubcomposeAsyncImage(
        model = imageModel,
        contentDescription = contentDescription
    ) {
        val imageScope = this
        val intrinsicSize = painter.intrinsicSize
        if (intrinsicSize == Size.Unspecified || intrinsicSize.width <= 0 || intrinsicSize.height <= 0) {
            // Loading or failed — show placeholder space
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            )
        } else {
            val ratio = intrinsicSize.width / intrinsicSize.height

            if (ratio >= 1.3f) {
                // Wide image — fills width naturally, no blur needed
                SubcomposeAsyncImageContent(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ratio)
                )
            } else {
                // Square or tall — two-layer with blur background
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val naturalHeight = maxWidth / ratio
                    val containerHeight = naturalHeight.coerceIn(minThumbHeight, maxThumbHeight)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(containerHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background blur layer
                        imageScope.SubcomposeAsyncImageContent(
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .matchParentSize()
                                .then(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                        Modifier.blur(20.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                        )
                        // Dark scrim over blur/fallback
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        )
                        // Foreground — actual image at natural aspect ratio
                        imageScope.SubcomposeAsyncImageContent(
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }
        }
    }
}
