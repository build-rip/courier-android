package rip.build.courier.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import rip.build.courier.ui.chatdetail.ChatDetailScreen
import rip.build.courier.ui.chatlist.ChatListScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ChatListDetailScreen(
    onUnbind: () -> Unit = {},
    onOpenMediaViewer: (chatRowID: Long, messageRowID: Long, attachmentRowID: Long) -> Unit = { _, _, _ -> },
    onOpenSyncDebug: () -> Unit = {}
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>(
        scaffoldDirective = calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(
            currentWindowAdaptiveInfo()
        )
    )
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavigableListDetailPaneScaffold(
        navigator = navigator,
        listPane = {
            AnimatedPane {
                ChatListScreen(
                    onChatClick = { chatRowID ->
                        scope.launch {
                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, chatRowID)
                        }
                    },
                    onSyncStatusClick = onOpenSyncDebug
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val chatRowID = navigator.currentDestination?.contentKey
                if (chatRowID != null) {
                    val listIsVisible = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
                    ChatDetailScreen(
                        chatRowID = chatRowID,
                        onBack = {
                            scope.launch {
                                navigator.navigateBack()
                            }
                        },
                        showBackButton = !listIsVisible,
                        onOpenMediaViewer = { messageRowID, attachmentRowID ->
                            onOpenMediaViewer(chatRowID, messageRowID, attachmentRowID)
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a conversation",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
    }
}
