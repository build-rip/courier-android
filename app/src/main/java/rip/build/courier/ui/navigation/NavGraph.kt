package rip.build.courier.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import rip.build.courier.PairingDeepLink
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.ui.debug.SyncDebugScreen
import rip.build.courier.ui.mediaviewer.MediaViewerScreen
import rip.build.courier.ui.pairing.PairingScreen
import kotlinx.coroutines.flow.StateFlow

@Composable
fun NavGraph(
    authPreferences: AuthPreferences = hiltViewModel<NavGraphViewModel>().authPreferences,
    deepLink: StateFlow<PairingDeepLink?>? = null
) {
    val navController = rememberNavController()
    // Use null initial value to distinguish "not yet loaded" from "not paired"
    val isPaired by authPreferences.isPaired.collectAsState(initial = null)
    val pairingDeepLink = deepLink?.collectAsState()?.value

    // Don't render anything until we know the paired state, to avoid flashing the pairing screen
    if (isPaired == null) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }

    val startDestination = if (isPaired == true && pairingDeepLink == null) Routes.CHAT_LIST else Routes.PAIRING

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.PAIRING) {
            PairingScreen(
                onPaired = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.PAIRING) { inclusive = true }
                    }
                },
                deepLink = pairingDeepLink
            )
        }

        composable(Routes.CHAT_LIST) {
            ChatListDetailScreen(
                onUnbind = {
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(Routes.CHAT_LIST) { inclusive = true }
                    }
                },
                onOpenMediaViewer = { chatRowID, messageRowID, attachmentRowID ->
                    navController.navigate(Routes.mediaViewer(chatRowID, messageRowID, attachmentRowID))
                },
                onOpenSyncDebug = {
                    navController.navigate(Routes.SYNC_DEBUG)
                }
            )
        }

        composable(Routes.SYNC_DEBUG) {
            SyncDebugScreen(
                onBack = { navController.popBackStack() },
                onUnbind = {
                    navController.navigate(Routes.PAIRING) {
                        popUpTo(Routes.CHAT_LIST) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.MEDIA_VIEWER,
            arguments = listOf(
                navArgument("chatRowID") { type = NavType.LongType },
                navArgument("messageRowID") { type = NavType.LongType },
                navArgument("attachmentRowID") { type = NavType.LongType }
            )
        ) {
            MediaViewerScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
