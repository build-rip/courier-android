package rip.build.courier

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import rip.build.courier.service.WebSocketForegroundService
import rip.build.courier.ui.navigation.NavGraph
import rip.build.courier.ui.theme.CourierTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PairingDeepLink(val host: String, val code: String)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _deepLink = MutableStateFlow<PairingDeepLink?>(null)
    val deepLink: StateFlow<PairingDeepLink?> = _deepLink.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()

        startForegroundService(
            Intent(this, WebSocketForegroundService::class.java)
        )

        setContent {
            CourierTheme {
                NavGraph(deepLink = deepLink)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "courier" && uri.host == "pair") {
            val host = uri.getQueryParameter("host")
            val code = uri.getQueryParameter("code")
            if (host != null && code != null) {
                _deepLink.value = PairingDeepLink(host, code)
            }
        }
    }

    fun consumeDeepLink() {
        _deepLink.value = null
    }
}
