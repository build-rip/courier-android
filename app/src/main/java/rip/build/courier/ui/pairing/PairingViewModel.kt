package rip.build.courier.ui.pairing

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rip.build.courier.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PairingUiState(
    val hostUrl: String = "",
    val pairingCode: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isPaired: Boolean = false,
    val showManualEntry: Boolean = false
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState

    fun onHostUrlChanged(url: String) {
        _uiState.update { it.copy(hostUrl = url, error = null) }
    }

    fun onPairingCodeChanged(code: String) {
        _uiState.update { it.copy(pairingCode = code.uppercase(), error = null) }
    }

    fun onQrCodeScanned(jsonContent: String) {
        try {
            // Parse {"host":"http://...","code":"A3K9M2"}
            val hostMatch = Regex("\"host\"\\s*:\\s*\"([^\"]+)\"").find(jsonContent)
            val codeMatch = Regex("\"code\"\\s*:\\s*\"([^\"]+)\"").find(jsonContent)

            val host = hostMatch?.groupValues?.get(1)
            val code = codeMatch?.groupValues?.get(1)

            if (host != null && code != null) {
                _uiState.update { it.copy(hostUrl = host, pairingCode = code, error = null) }
                pair()
            } else {
                _uiState.update { it.copy(error = "Invalid QR code format") }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to parse QR code") }
        }
    }

    fun toggleManualEntry() {
        _uiState.update { it.copy(showManualEntry = !it.showManualEntry) }
    }

    fun pair() {
        val state = _uiState.value
        if (state.hostUrl.isBlank() || state.pairingCode.isBlank()) {
            _uiState.update { it.copy(error = "Host URL and pairing code are required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = authRepository.pair(
                hostUrl = state.hostUrl.trimEnd('/'),
                code = state.pairingCode,
                deviceName = Build.MODEL
            )

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isLoading = false, isPaired = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "Pairing failed")
                    }
                }
            )
        }
    }
}
