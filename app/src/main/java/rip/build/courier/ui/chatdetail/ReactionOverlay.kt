package rip.build.courier.ui.chatdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rip.build.courier.domain.model.Reaction
import rip.build.courier.domain.model.SendStatus

@Composable
fun ReactionOverlay(
    reactions: List<Reaction>,
    onRetryReaction: ((Long) -> Unit)? = null,
    onDeleteReaction: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val visibleReactions = reactions.filter { !it.isRemoval }
    val grouped = visibleReactions.groupBy { it.displayEmoji }

    if (grouped.isEmpty()) return

    Row(
        modifier = modifier
            .offset(y = (-4).dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        grouped.forEach { (emoji, list) ->
            // Check if any reaction in this group is pending or failed
            val pendingReaction = list.firstOrNull { it.sendStatus != null }
            val isSending = pendingReaction?.sendStatus == SendStatus.SENDING
            val isFailed = pendingReaction?.sendStatus == SendStatus.FAILED

            val label = buildString {
                append(emoji)
                if (isFailed) append("!")
                if (list.size > 1) append(list.size)
            }

            val reactionAlpha = when {
                isSending -> 0.5f
                else -> 1f
            }

            if (isFailed && pendingReaction != null) {
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .alpha(reactionAlpha)
                            .clickable { showMenu = true }
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        pendingReaction.sendError?.let { error ->
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
                                showMenu = false
                                onRetryReaction?.invoke(pendingReaction.rowID)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                onDeleteReaction?.invoke(pendingReaction.rowID)
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    modifier = Modifier.alpha(reactionAlpha)
                )
            }
        }
    }
}
