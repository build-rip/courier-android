package rip.build.courier.ui.chatdetail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rip.build.courier.domain.model.AttachmentInfo
import rip.build.courier.domain.model.Message
import rip.build.courier.domain.model.ReplyContext
import rip.build.courier.domain.model.RichText
import rip.build.courier.domain.model.RichTextPart
import rip.build.courier.domain.model.SendStatus
import rip.build.courier.domain.util.DateFormatter
import rip.build.courier.ui.theme.CourierBlue
import rip.build.courier.ui.theme.CourierGreen
import rip.build.courier.ui.theme.ReceivedGray
import rip.build.courier.ui.theme.ReceivedGrayDark

enum class BubblePosition { SOLO, FIRST, MIDDLE, LAST }

private sealed class MessageSegment {
    abstract val partIndex: Int
    data class Text(val parts: List<RichTextPart>, override val partIndex: Int) : MessageSegment()
    data class Attachment(val attachment: AttachmentInfo, override val partIndex: Int) : MessageSegment()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    showSender: Boolean = false,
    showTimestamp: Boolean = true,
    showDeliveryStatus: Boolean = false,
    onTapbackTarget: ((Int) -> Unit)? = null,
    attachments: List<AttachmentInfo> = emptyList(),
    baseUrl: String? = null,
    onRetry: ((Long) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
    onRetryReaction: ((Long) -> Unit)? = null,
    onDeleteReaction: ((Long) -> Unit)? = null,
    onRequestDownload: ((AttachmentInfo) -> Unit)? = null,
    onAttachmentClick: ((AttachmentInfo) -> Unit)? = null,
    bubblePosition: BubblePosition = BubblePosition.SOLO,
    modifier: Modifier = Modifier
) {
    val hasText = if (message.richText != null && message.richText.parts.isNotEmpty()) {
        message.richText.parts.any {
            it.attributes.attachmentIndex == null && it.text.replace("\uFFFC", "").isNotEmpty()
        }
    } else {
        message.text?.replace("\uFFFC", "")?.isNotBlank() == true
    }
    val displayableAttachments = attachments.filter { !it.isUndisplayablePluginPayload }
    val hasDisplayableAttachments = displayableAttachments.isNotEmpty()
    val attachmentsLoaded = attachments.isNotEmpty()
    val attachmentsStillLoading = message.hasAttachments && !attachmentsLoaded

    // For messages with inline attachments (richText contains attachment placeholders),
    // build an ordered list of interleaved text/attachment segments with correct part indices
    val inlineSegments = if (message.richText != null && baseUrl != null && attachments.isNotEmpty()) {
        buildInlineSegments(message.richText, attachments)
    } else null

    // Skip messages with no displayable content (after attachments have loaded)
    // But always show pending/failed messages even with no attachments
    if (!hasText && !hasDisplayableAttachments && !attachmentsStillLoading && message.sendStatus == null) return

    val isFromMe = message.isFromMe
    val isInstantService = message.service == "instant"
    val isSending = message.sendStatus == SendStatus.SENDING
    val isFailed = message.sendStatus == SendStatus.FAILED

    val isDark = isSystemInDarkTheme()
    val bubbleColor = when {
        isFromMe && isInstantService -> CourierBlue
        isFromMe -> CourierGreen
        isDark -> ReceivedGrayDark
        else -> ReceivedGray
    }

    val textColor = if (isFromMe) Color.White else if (isDark) Color.White else Color.Black

    val alignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleShape = when (bubblePosition) {
        BubblePosition.SOLO -> RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = if (isFromMe) 18.dp else 4.dp,
            bottomEnd = if (isFromMe) 4.dp else 18.dp
        )
        BubblePosition.FIRST -> RoundedCornerShape(
            topStart = 18.dp, topEnd = 18.dp,
            bottomStart = 6.dp, bottomEnd = 6.dp
        )
        BubblePosition.MIDDLE -> RoundedCornerShape(6.dp)
        BubblePosition.LAST -> RoundedCornerShape(
            topStart = 6.dp, topEnd = 6.dp,
            bottomStart = if (isFromMe) 18.dp else 4.dp,
            bottomEnd = if (isFromMe) 4.dp else 18.dp
        )
    }

    val bubbleAlpha = if (isSending) 0.6f else 1f

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .alpha(bubbleAlpha),
        contentAlignment = alignment
    ) {
        val maxBubbleWidth = maxWidth * 0.8f

        Column(
            modifier = Modifier.widthIn(max = maxBubbleWidth),
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
        ) {
            if (showSender && !isFromMe && message.senderID != null) {
                Text(
                    text = message.senderID,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
                )
            }

            // Reply context preview
            if (message.replyContext != null) {
                ReplyContextPreview(
                    replyContext = message.replyContext,
                    isFromMe = isFromMe
                )
            }

            // Native part indices: attachments first (0..N-1), then text at N
            // For text-only messages (or link previews), text is at index 0
            val textPartIndex = if (hasDisplayableAttachments) displayableAttachments.size else 0

            if (message.balloonBundleID == "com.apple.messages.URLBalloonProvider" && message.text != null) {
                LinkPreviewCard(
                    url = message.linkPreviewURL ?: message.text,
                    title = message.linkPreviewTitle,
                    subtitle = message.linkPreviewSubtitle,
                    imageAttachment = displayableAttachments.filter { it.isImage }.maxByOrNull { it.totalBytes },
                    baseUrl = baseUrl,
                    bubbleColor = bubbleColor,
                    textColor = textColor,
                    onLongPress = { onTapbackTarget?.invoke(0) },
                    maxBubbleWidth = maxBubbleWidth
                )
            } else if (inlineSegments != null) {
                // Inline rendering: interleave text and attachments based on richText order
                val inlineBubbleShape = RoundedCornerShape(18.dp)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
                ) {
                    for (segment in inlineSegments) {
                        when (segment) {
                            is MessageSegment.Text -> {
                                Column {
                                    Box(
                                        modifier = Modifier
                                            .clip(inlineBubbleShape)
                                            .background(bubbleColor)
                                            .combinedClickable(
                                                onClick = {},
                                                onLongClick = { onTapbackTarget?.invoke(segment.partIndex) }
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        RichTextContent(
                                            parts = segment.parts,
                                            textColor = textColor,
                                            isFromMe = isFromMe,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                    val reactions = message.reactions.filter { it.partIndex == segment.partIndex }
                                    if (reactions.isNotEmpty()) {
                                        ReactionOverlay(
                                            reactions = reactions,
                                            onRetryReaction = onRetryReaction,
                                            onDeleteReaction = onDeleteReaction
                                        )
                                    }
                                }
                            }
                            is MessageSegment.Attachment -> {
                                Column {
                                    AttachmentThumbnail(
                                        attachment = segment.attachment,
                                        baseUrl = baseUrl!!,
                                        onRequestDownload = onRequestDownload,
                                        onLongPress = { onTapbackTarget?.invoke(segment.partIndex) },
                                        onClick = onAttachmentClick
                                    )
                                    val reactions = message.reactions.filter { it.partIndex == segment.partIndex }
                                    if (reactions.isNotEmpty()) {
                                        ReactionOverlay(
                                            reactions = reactions,
                                            onRetryReaction = onRetryReaction,
                                            onDeleteReaction = onDeleteReaction
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {

                // Render attachments outside the colored bubble (native style)
                if (hasDisplayableAttachments && baseUrl != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        displayableAttachments.forEachIndexed { index, attachment ->
                            Column {
                                AttachmentThumbnail(
                                    attachment = attachment,
                                    baseUrl = baseUrl,
                                    onRequestDownload = onRequestDownload,
                                    onLongPress = { onTapbackTarget?.invoke(index) },
                                    onClick = onAttachmentClick
                                )
                                // Show reactions for this attachment part
                                val attachmentReactions = message.reactions.filter { it.partIndex == index }
                                if (attachmentReactions.isNotEmpty()) {
                                    ReactionOverlay(
                                        reactions = attachmentReactions,
                                        onRetryReaction = onRetryReaction,
                                        onDeleteReaction = onDeleteReaction
                                    )
                                }
                            }
                        }
                    }
                } else if (attachmentsStillLoading) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 160.dp, minHeight = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (hasText) {
                    Box(
                        modifier = Modifier
                            .clip(bubbleShape)
                            .background(bubbleColor)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onTapbackTarget?.invoke(textPartIndex) }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val richText = message.richText
                        if (richText != null && richText.parts.isNotEmpty()) {
                            RichTextContent(
                                parts = richText.parts,
                                textColor = textColor,
                                isFromMe = isFromMe,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        } else {
                            message.text?.replace("\uFFFC", "")?.trimStart()?.ifEmpty { null }?.let { text ->
                                Text(
                                    text = text,
                                    color = textColor,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                }
            }

            // Show reactions for text bubble (non-inline only; inline handles per-segment)
            if (inlineSegments == null) {
                val textReactions = message.reactions.filter { it.partIndex == textPartIndex }
                if (textReactions.isNotEmpty()) {
                    ReactionOverlay(
                        reactions = textReactions,
                        onRetryReaction = onRetryReaction,
                        onDeleteReaction = onDeleteReaction
                    )
                }
            }

            // Status row: timestamp + send/delivery/read status indicators
            val statusText = when {
                message.dateRead != null -> DateFormatter.readReceiptLabel(message.dateRead)
                message.dateDelivered != null -> "Delivered"
                else -> null
            }
            val hasDeliveryInfo = showDeliveryStatus && isFromMe && message.sendStatus == null &&
                (message.dateRead != null || message.dateDelivered != null)
            if (isSending || isFailed || showTimestamp || hasDeliveryInfo) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(10.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Sending",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (isFailed) {
                        var showErrorMenu by remember { mutableStateOf(false) }

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showErrorMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Send failed",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Not delivered",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            DropdownMenu(
                                expanded = showErrorMenu,
                                onDismissRequest = { showErrorMenu = false }
                            ) {
                                message.sendError?.let { error ->
                                    Text(
                                        text = error,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .widthIn(max = 240.dp)
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Retry") },
                                    onClick = {
                                        showErrorMenu = false
                                        onRetry?.invoke(message.rowID)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showErrorMenu = false
                                        onDelete?.invoke(message.rowID)
                                    }
                                )
                            }
                        }
                    } else {
                        if (showTimestamp) {
                            Text(
                                text = DateFormatter.timeOnly(message.date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (hasDeliveryInfo && statusText != null) {
                            if (showTimestamp) {
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    }
                }
            }

            if (message.replyCount > 0) {
                Text(
                    text = if (message.replyCount == 1) "1 Reply" else "${message.replyCount} Replies",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF007AFF),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ReplyContextPreview(
    replyContext: ReplyContext,
    isFromMe: Boolean
) {
    val previewText = replyContext.parentText
        ?: if (replyContext.parentHasAttachments) "Photo" else "Message"

    Row(
        modifier = Modifier.padding(bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        // Thin vertical connecting line
        Box(
            modifier = Modifier
                .padding(
                    start = if (isFromMe) 0.dp else 8.dp,
                    end = if (isFromMe) 8.dp else 0.dp
                )
                .width(2.dp)
                .height(24.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    shape = CircleShape
                )
        )

        Text(
            text = previewText,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RichTextContent(
    parts: List<RichTextPart>,
    textColor: Color,
    isFromMe: Boolean,
    style: TextStyle
) {
    val linkColor = if (isFromMe) {
        Color.White.copy(alpha = 0.9f)
    } else {
        Color(0xFF007AFF)
    }

    val mentionColor = if (isFromMe) {
        Color.White
    } else {
        Color(0xFF007AFF)
    }

    val annotatedString = remember(parts, textColor, linkColor, mentionColor) {
        buildRichAnnotatedString(parts, textColor, linkColor, mentionColor)
    }

    Text(
        text = annotatedString,
        style = style
    )
}

private fun buildRichAnnotatedString(
    parts: List<RichTextPart>,
    textColor: Color,
    linkColor: Color,
    mentionColor: Color
): AnnotatedString = buildAnnotatedString {
    for (part in parts) {
        val attrs = part.attributes

        // Skip attachment placeholder parts — images are rendered separately
        if (attrs.attachmentIndex != null) continue

        // Strip any remaining U+FFFC characters from text
        val partText = part.text.replace("\uFFFC", "")
        if (partText.isEmpty()) continue

        val decorations = mutableListOf<TextDecoration>()
        if (attrs.strikethrough) decorations.add(TextDecoration.LineThrough)
        if (attrs.underline || attrs.link != null) decorations.add(TextDecoration.Underline)

        val combinedDecoration = if (decorations.isNotEmpty()) {
            decorations.reduce { acc, dec -> acc + dec }
        } else {
            TextDecoration.None
        }

        val color = when {
            attrs.link != null -> linkColor
            attrs.mention != null -> mentionColor
            else -> textColor
        }

        val spanStyle = SpanStyle(
            color = color,
            fontWeight = if (attrs.bold || attrs.mention != null) FontWeight.Bold else null,
            fontStyle = if (attrs.italic) FontStyle.Italic else null,
            textDecoration = combinedDecoration
        )

        if (attrs.link != null) {
            val linkAnnotation = LinkAnnotation.Url(
                url = attrs.link,
                styles = TextLinkStyles(style = spanStyle)
            )
            withLink(linkAnnotation) {
                append(partText)
            }
        } else {
            withStyle(spanStyle) {
                append(partText)
            }
        }
    }
}

/**
 * For messages with inline attachments, parse richText into an ordered list of
 * interleaved text and attachment segments. Each segment gets a sequential part index
 * matching the native part numbering (text0, attachment1, text2, attachment3, ...).
 * Returns null if the richText has no attachment references (not an inline message).
 */
private fun buildInlineSegments(
    richText: RichText,
    attachments: List<AttachmentInfo>
): List<MessageSegment>? {
    val hasAttachmentRefs = richText.parts.any { it.attributes.attachmentIndex != null }
    if (!hasAttachmentRefs) return null

    val segments = mutableListOf<MessageSegment>()
    var partIndex = 0
    var textBuffer = mutableListOf<RichTextPart>()

    fun flushTextBuffer() {
        if (textBuffer.isEmpty()) return
        val cleaned = textBuffer.mapNotNull { part ->
            val cleanedText = part.text.replace("\uFFFC", "")
            if (cleanedText.isNotEmpty()) part.copy(text = cleanedText) else null
        }
        if (cleaned.isNotEmpty()) {
            segments.add(MessageSegment.Text(cleaned, partIndex))
        }
        // Always increment: even an empty text range between attachments occupies a part slot
        partIndex++
        textBuffer = mutableListOf()
    }

    for (part in richText.parts) {
        if (part.attributes.attachmentIndex != null) {
            flushTextBuffer()

            val attIdx = part.attributes.attachmentIndex
            val attachment = attachments.getOrNull(attIdx)
            if (attachment != null && !attachment.isUndisplayablePluginPayload) {
                segments.add(MessageSegment.Attachment(attachment, partIndex))
            }
            partIndex++
        } else {
            textBuffer.add(part)
        }
    }

    flushTextBuffer()
    return if (segments.isNotEmpty()) segments else null
}
