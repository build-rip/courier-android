package rip.build.courier.ui.chatlist

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import rip.build.courier.data.remote.websocket.ConnectionState
import rip.build.courier.domain.model.Chat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (Long) -> Unit,
    onSyncStatusClick: () -> Unit = {},
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val pinnedChats by viewModel.pinnedChats.collectAsState()
    val unpinnedChats by viewModel.unpinnedChats.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val context = LocalContext.current

    var contextMenuChat by remember { mutableStateOf<Chat?>(null) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    val statusDotColor = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        ConnectionState.CONNECTING -> Color(0xFFFFC107)
        ConnectionState.DISCONNECTED -> Color(0xFFF44336)
    }

    val statusLabel = when (connectionState) {
        ConnectionState.CONNECTED -> "online"
        ConnectionState.CONNECTING -> "connecting"
        ConnectionState.DISCONNECTED -> "offline"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onSyncStatusClick() }
                            .padding(horizontal = 10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusDotColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (pinnedChats.isNotEmpty()) {
                    item(key = "pinned_grid") {
                        PinnedChatsGrid(
                            pinnedChats = pinnedChats,
                            onChatClick = onChatClick,
                            onTogglePin = { viewModel.togglePin(it) }
                        )
                        HorizontalDivider()
                    }
                }

                items(unpinnedChats, key = { it.rowID }) { chat ->
                    Box {
                        ChatListItem(
                            chat = chat,
                            onClick = { onChatClick(chat.rowID) },
                            onLongClick = { contextMenuChat = chat }
                        )
                        if (contextMenuChat?.rowID == chat.rowID) {
                            ChatContextMenu(
                                isPinned = false,
                                expanded = true,
                                onDismiss = { contextMenuChat = null },
                                onTogglePin = { viewModel.togglePin(chat.rowID) }
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}
