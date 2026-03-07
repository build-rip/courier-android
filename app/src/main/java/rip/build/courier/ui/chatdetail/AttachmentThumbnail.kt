package rip.build.courier.ui.chatdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import rip.build.courier.domain.model.AttachmentInfo
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AttachmentThumbnail(
    attachment: AttachmentInfo,
    baseUrl: String,
    onRequestDownload: ((AttachmentInfo) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onClick: ((AttachmentInfo) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (attachment.isImage) {
        Box(
            modifier = modifier
                .combinedClickable(
                    onClick = {
                        if (attachment.isDownloaded) onClick?.invoke(attachment)
                    },
                    onLongClick = onLongPress
                )
        ) {
            val model: Any = if (attachment.isDownloaded) {
                File(attachment.localFilePath!!)
            } else {
                "$baseUrl/api/attachments/${attachment.rowID}"
            }

            AsyncImage(
                model = model,
                contentDescription = attachment.transferName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .defaultMinSize(minWidth = 160.dp, minHeight = 120.dp)
                    .widthIn(max = 240.dp)
                    .heightIn(max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
            )

            // Download progress overlay
            if (attachment.isDownloading) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .heightIn(max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { attachment.downloadProgress },
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }

            // Download button for too_large or exhausted failures
            if (attachment.isTooLarge || attachment.downloadExhausted) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.15f))
                        .clickable { onRequestDownload?.invoke(attachment) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (attachment.downloadExhausted) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry download",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Download failed",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = formatFileSize(attachment.totalBytes),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else if (attachment.isVideo) {
        Box(
            modifier = modifier
                .widthIn(max = 240.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.15f))
                .combinedClickable(
                    onClick = {
                        if (attachment.isDownloaded) onClick?.invoke(attachment)
                    },
                    onLongClick = onLongPress
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play video",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.transferName ?: "Video",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .padding(8.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongPress
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = attachment.transferName ?: "File",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
