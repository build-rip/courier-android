package rip.build.courier.ui.chatlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rip.build.courier.data.remote.websocket.ConnectionState
import rip.build.courier.data.remote.websocket.WebSocketEventHandler
import rip.build.courier.data.remote.websocket.WebSocketManager
import rip.build.courier.data.repository.ChatRepository
import rip.build.courier.domain.model.Chat
import rip.build.courier.domain.util.ContactResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val contactResolver: ContactResolver,
    private val webSocketEventHandler: WebSocketEventHandler,
    webSocketManager: WebSocketManager
) : ViewModel() {

    val connectionState: StateFlow<ConnectionState> = webSocketManager.connectionState

    val pinnedChats: StateFlow<List<Chat>> = chatRepository.observePinnedChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unpinnedChats: StateFlow<List<Chat>> = chatRepository.observeUnpinnedChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            webSocketEventHandler.triggerSync()
            _isRefreshing.value = false
        }
    }

    fun togglePin(chatRowID: Long) {
        viewModelScope.launch {
            val isPinned = pinnedChats.value.any { it.rowID == chatRowID }
            chatRepository.setPinned(chatRowID, !isPinned)
        }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        if (granted) {
            contactResolver.clearCache()
            refresh()
        }
    }
}
