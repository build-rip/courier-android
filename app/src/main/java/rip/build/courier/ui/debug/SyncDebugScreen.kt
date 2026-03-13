package rip.build.courier.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import rip.build.courier.data.remote.websocket.ConnectionState
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncDebugScreen(
    onBack: () -> Unit,
    onUnbind: () -> Unit = {},
    viewModel: SyncDebugViewModel = hiltViewModel()
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isReconnecting by viewModel.isReconnecting.collectAsState()
    val lastConnectedTime by viewModel.lastConnectedTime.collectAsState()
    val lastEventTime by viewModel.lastEventTime.collectAsState()
    val reconnectAttempts by viewModel.reconnectAttempts.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    val lastSyncResult by viewModel.lastSyncResult.collectAsState()
    val syncProgress by viewModel.syncProgress.collectAsState()
    val wsEventCount by viewModel.wsEventCount.collectAsState()
    val hostUrl by viewModel.hostUrl.collectAsState(initial = null)
    val isSyncing = syncProgress != null

    var showResetConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showUnbindConfirm by remember { mutableStateOf(false) }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Sync Cursor?") },
            text = { Text("This resets local conversation event cursors and forces the next sync to rebuild each conversation from the bridge event log.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetSyncCursor()
                    showResetConfirm = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Local Data?") },
            text = { Text("This deletes all local events and derived conversation state. Data will be rebuilt from the bridge event log.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearLocalData()
                    showClearConfirm = false
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showUnbindConfirm) {
        AlertDialog(
            onDismissRequest = { showUnbindConfirm = false },
            title = { Text("Unbind from Bridge?") },
            text = { Text("This will disconnect from the current bridge, clear all local data, and return to the pairing screen. You will need a new pairing code to reconnect.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unbindFromBridge()
                    showUnbindConfirm = false
                    onUnbind()
                }) { Text("Unbind", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showUnbindConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Connection Section
                SectionHeader("Connection")

                val statusColor = when (connectionState) {
                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                    ConnectionState.CONNECTING -> Color(0xFFFFC107)
                    ConnectionState.DISCONNECTED -> Color(0xFFF44336)
                }
                InfoRow("Status") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val statusText = if (isReconnecting && connectionState == ConnectionState.DISCONNECTED) {
                            "DISCONNECTED (reconnecting...)"
                        } else {
                            connectionState.name
                        }
                        Text(statusText, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                InfoRow("Connected since", formatRelativeTime(lastConnectedTime))
                InfoRow("Last WS event", formatRelativeTime(lastEventTime))
                InfoRow("Reconnect attempts", reconnectAttempts.toString())
                InfoRow("Last error", lastError ?: "none")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Sync Section
                SectionHeader("Sync")

                val chatsStat = syncProgress?.let { "${it.chatsSynced} / ${it.chatsTotal}" }
                    ?: lastSyncResult?.chatsSynced?.toString()
                    ?: "-"
                val messagesStat = syncProgress?.messagesCount?.toString()
                    ?: lastSyncResult?.messagesCount?.toString()
                    ?: "-"
                val reactionsStat = syncProgress?.reactionsCount?.toString()
                    ?: lastSyncResult?.reactionsCount?.toString()
                    ?: "-"

                InfoRow("Chats", chatsStat)
                InfoRow("Messages", messagesStat)
                InfoRow("Reactions", reactionsStat)

                val syncSummary = lastSyncResult?.let { r ->
                    val detail = if (r.success) {
                        "OK"
                    } else {
                        "ERR ${r.error}"
                    }
                    "$detail\n${formatRelativeTime(r.time)}"
                } ?: "never"
                InfoRow("Last sync", syncSummary)

                InfoRow("WS events this session", wsEventCount.toString())
                InfoRow("Host URL", hostUrl ?: "not set")

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Attachments Section
                SectionHeader("Attachments")

                val attachTotal by viewModel.attachmentTotal.collectAsState(initial = 0)
                val attachCompleted by viewModel.attachmentCompleted.collectAsState(initial = 0)
                val attachPending by viewModel.attachmentPending.collectAsState(initial = 0)
                val attachDownloading by viewModel.attachmentDownloading.collectAsState(initial = 0)
                val attachFailed by viewModel.attachmentFailed.collectAsState(initial = 0)

                InfoRow("Total", attachTotal.toString())
                InfoRow("Completed", "$attachCompleted / $attachTotal")
                InfoRow("Downloading", attachDownloading.toString())
                InfoRow("Queued", attachPending.toString())
                InfoRow("Failed", attachFailed.toString())

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Actions Section
                SectionHeader("Actions")

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { viewModel.forceSync() },
                    enabled = !isSyncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSyncing) "Syncing..." else "Force Sync")
                }

                OutlinedButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset Event Cursors")
                }

                OutlinedButton(
                    onClick = { viewModel.reconnectWebSocket() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reconnect")
                }

                Button(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear Local Data")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Button(
                    onClick = { showUnbindConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Unbind from Bridge")
                }
            }
        }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

@Composable
private fun InfoRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Box(modifier = Modifier.weight(0.6f)) {
            content()
        }
    }
}

private fun formatRelativeTime(instant: Instant?): String {
    if (instant == null) return "-"
    val duration = Duration.between(instant, Instant.now())
    return when {
        duration.seconds < 5 -> "just now"
        duration.seconds < 60 -> "${duration.seconds}s ago"
        duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
        duration.toHours() < 24 -> "${duration.toHours()}h ago"
        else -> "${duration.toDays()}d ago"
    }
}
