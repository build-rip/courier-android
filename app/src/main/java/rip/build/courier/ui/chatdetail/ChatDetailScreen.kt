package rip.build.courier.ui.chatdetail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import rip.build.courier.domain.model.Message
import rip.build.courier.domain.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatRowID: Long,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onOpenMediaViewer: ((Long, Long) -> Unit)? = null,
    viewModel: ChatDetailViewModel = hiltViewModel(key = "chat_detail_$chatRowID")
) {
    LaunchedEffect(chatRowID) {
        viewModel.setChatRowID(chatRowID)
    }

    val chat by viewModel.chat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val baseUrl by viewModel.baseUrl.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Tapback picker state: (messageGUID, partIndex)
    var tapbackTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Show error messages as snackbar
    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Filter out messages with no visible content (no text, no rich text, no attachments, no pending status)
    val visibleMessages = remember(messages) {
        messages.filter { msg ->
            msg.text != null || (msg.richText != null && msg.richText.parts.isNotEmpty()) || msg.hasAttachments || msg.sendStatus != null
        }
    }

    // Reversed list for bottom-anchored display
    val reversedMessages = remember(visibleMessages) { visibleMessages.asReversed() }

    // Scroll to bottom (index 0 in reverse layout) when new messages arrive
    LaunchedEffect(visibleMessages.size) {
        if (visibleMessages.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // Tapback picker dialog
    tapbackTarget?.let { (guid, partIndex) ->
        // Find which tapback types the current user already has on this message+part
        val targetMessage = messages.find { it.guid == guid }
        val myReactions = remember(targetMessage?.reactions, partIndex) {
            (targetMessage?.reactions ?: emptyList())
                .filter { it.isFromMe && !it.isRemoval && it.partIndex == partIndex }
                .map { it.reactionType.name.lowercase() }
                .toSet()
        }
        TapbackPicker(
            myReactions = myReactions,
            onSelect = { type, emoji -> viewModel.sendTapback(type, guid, partIndex, emoji) },
            onRemove = { type, emoji -> viewModel.removeTapback(type, guid, partIndex, emoji) },
            onDismiss = { tapbackTarget = null }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(chat?.title ?: "") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            MessageInput(
                text = messageText,
                onTextChanged = viewModel::onMessageTextChanged,
                onSend = viewModel::sendMessage,
                modifier = Modifier.navigationBarsPadding()
            )
        }
    ) { padding ->
        // Pre-compute per-message metadata (timestamps, date separators, grouping)
        data class MessageMeta(
            val showDateSeparator: Boolean,
            val dateLabel: String,
            val showTimestamp: Boolean,
            val showDeliveryStatus: Boolean,
            val bubblePosition: BubblePosition
        )

        // Compute metadata in chronological order, then reverse for display
        val latestOutgoingStatusRowID = remember(visibleMessages, chat?.rowID, chat?.serviceName, chat?.isGroup) {
            val currentChat = chat
            if (currentChat?.isGroup == false && currentChat.serviceName == "instant") {
                visibleMessages.lastOrNull { it.isFromMe }?.rowID
            } else {
                null
            }
        }

        val reversedMetadata = remember(visibleMessages, latestOutgoingStatusRowID) {
            visibleMessages.mapIndexed { index, message ->
                val prevMessage = if (index > 0) visibleMessages[index - 1] else null
                val nextMessage = if (index < visibleMessages.size - 1) visibleMessages[index + 1] else null

                val currentDate = DateFormatter.dateSeparator(message.date)
                val prevDate = prevMessage?.let { DateFormatter.dateSeparator(it.date) }
                val nextDate = nextMessage?.let { DateFormatter.dateSeparator(it.date) }

                val showDateSeparator = currentDate != prevDate

                val showTimestamp = nextMessage == null ||
                    DateFormatter.shouldShowTimestamp(nextMessage.date, message.date)

                fun sameSender(a: Message, b: Message): Boolean {
                    if (a.isFromMe != b.isFromMe) return false
                    if (!a.isFromMe && a.senderID != b.senderID) return false
                    return true
                }

                val groupsWithPrev = prevMessage != null &&
                    !showDateSeparator &&
                    sameSender(prevMessage, message) &&
                    prevMessage.reactions.isEmpty() &&
                    !DateFormatter.shouldShowTimestamp(message.date, prevMessage.date)

                val groupsWithNext = nextMessage != null &&
                    currentDate == nextDate &&
                    sameSender(message, nextMessage) &&
                    message.reactions.isEmpty() &&
                    !showTimestamp

                val bubblePosition = when {
                    groupsWithPrev && groupsWithNext -> BubblePosition.MIDDLE
                    groupsWithPrev -> BubblePosition.LAST
                    groupsWithNext -> BubblePosition.FIRST
                    else -> BubblePosition.SOLO
                }

                // Show delivery/read status on the last outgoing message before
                // a direction change or the end of the conversation
                val showDeliveryStatus = message.rowID == latestOutgoingStatusRowID

                MessageMeta(showDateSeparator, currentDate, showTimestamp, showDeliveryStatus, bubblePosition)
            }.asReversed()
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            state = listState,
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(reversedMessages.size, key = { reversedMessages[it].rowID }) { index ->
                val message = reversedMessages[index]
                val meta = reversedMetadata[index]

                if (meta.showDateSeparator) {
                    DateSeparator(date = meta.dateLabel)
                }

                val attachments = if (message.hasAttachments) {
                    LaunchedEffect(message.rowID) {
                        viewModel.ensureAttachmentMetadata(message.rowID)
                    }
                    viewModel.observeAttachments(message.rowID)
                        .collectAsState(initial = emptyList()).value
                } else {
                    emptyList()
                }

                val itemPadding = when (meta.bubblePosition) {
                    BubblePosition.SOLO -> Modifier.padding(top = 3.dp, bottom = 3.dp)
                    BubblePosition.FIRST -> Modifier.padding(top = 3.dp, bottom = 1.dp)
                    BubblePosition.MIDDLE -> Modifier.padding(vertical = 1.dp)
                    BubblePosition.LAST -> Modifier.padding(top = 1.dp, bottom = 3.dp)
                }

                MessageBubble(
                    message = message,
                    showSender = chat?.isGroup == true,
                    showTimestamp = meta.showTimestamp,
                    showDeliveryStatus = meta.showDeliveryStatus,
                    onTapbackTarget = { partIndex -> tapbackTarget = Pair(message.guid, partIndex) },
                    attachments = attachments,
                    baseUrl = baseUrl,
                    onRetry = viewModel::retryMessage,
                    onDelete = viewModel::deletePendingMessage,
                    onRetryReaction = viewModel::retryTapback,
                    onDeleteReaction = viewModel::deletePendingReaction,
                    onRequestDownload = { attachment ->
                        viewModel.requestManualDownload(attachment.messageRowID, attachment.rowID)
                    },
                    onAttachmentClick = { attachment ->
                        onOpenMediaViewer?.invoke(attachment.messageRowID, attachment.rowID)
                    },
                    bubblePosition = meta.bubblePosition,
                    modifier = itemPadding
                )
            }
        }
    }
}

@Composable
private fun DateSeparator(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
