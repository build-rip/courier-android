package rip.build.courier.ui.chatlist

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ChatContextMenu(
    isPinned: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        DropdownMenuItem(
            text = { Text(if (isPinned) "Unpin" else "Pin") },
            onClick = {
                onTogglePin()
                onDismiss()
            }
        )
    }
}
