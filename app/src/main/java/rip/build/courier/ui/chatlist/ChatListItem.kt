package rip.build.courier.ui.chatlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rip.build.courier.domain.model.Chat
import rip.build.courier.domain.util.DateFormatter
import rip.build.courier.ui.theme.CourierBlue

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: Chat,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ContactAvatar(
            photoUri = chat.avatarUri,
            initials = chat.avatarInitials,
            isGroup = chat.isGroup,
            size = 48.dp
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = DateFormatter.relativeTimestamp(chat.lastMessageDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (chat.hasUnreads) CourierBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (chat.hasUnreads) FontWeight.SemiBold else null,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = chat.lastMessageText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (chat.hasUnreads) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (chat.hasUnreads) FontWeight.Medium else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (chat.hasUnreads) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(CourierBlue)
            )
        }
    }
}
