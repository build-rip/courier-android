package rip.build.courier.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rip.build.courier.domain.model.Chat
import rip.build.courier.ui.theme.CourierBlue

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PinnedChatsGrid(
    pinnedChats: List<Chat>,
    onChatClick: (Long) -> Unit,
    onTogglePin: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        pinnedChats.forEach { chat ->
            var showMenu by remember { mutableStateOf(false) }
            Box {
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .combinedClickable(
                            onClick = { onChatClick(chat.rowID) },
                            onLongClick = { showMenu = true }
                        )
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        ContactAvatar(
                            photoUri = chat.avatarUri,
                            initials = chat.avatarInitials,
                            isGroup = chat.isGroup,
                            size = 64.dp
                        )
                        if (chat.hasUnreads) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(CourierBlue)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                            )
                        }
                    }
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                ChatContextMenu(
                    isPinned = true,
                    expanded = showMenu,
                    onDismiss = { showMenu = false },
                    onTogglePin = { onTogglePin(chat.rowID) }
                )
            }
        }
    }
}
