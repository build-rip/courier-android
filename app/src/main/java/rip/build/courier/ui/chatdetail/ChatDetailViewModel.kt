package rip.build.courier.ui.chatdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.data.repository.AttachmentDownloadManager
import rip.build.courier.data.repository.ChatRepository
import rip.build.courier.data.repository.MessageRepository
import rip.build.courier.domain.model.AttachmentInfo
import rip.build.courier.domain.model.Chat
import rip.build.courier.domain.model.Message
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val attachmentDownloadManager: AttachmentDownloadManager,
    authPreferences: AuthPreferences
) : ViewModel() {

    private val _chatRowID = MutableStateFlow(savedStateHandle.get<Long>("chatRowID") ?: -1L)

    fun setChatRowID(id: Long) {
        _chatRowID.value = id
    }

    private val activeChatRowID = _chatRowID.filter { it != -1L }

    init {
        viewModelScope.launch {
            activeChatRowID.collect { chatRowID ->
                launch {
                    messageRepository.syncChat(chatRowID).onSuccess {
                        attachmentDownloadManager.enqueueDownloads(
                            messageRepository.getPendingAttachmentKeys(chatRowID)
                        )
                    }.onFailure {
                        _errorMessage.emit(it.message ?: "Couldn't sync conversation")
                    }
                }
                launch {
                    chatRepository.markAsRead(chatRowID).onFailure {
                        _errorMessage.emit(it.message ?: "Couldn't confirm read state")
                    }
                }
            }
        }
    }

    val chat: StateFlow<Chat?> = activeChatRowID
        .flatMapLatest { chatRepository.observeChat(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val messages: StateFlow<List<Message>> = activeChatRowID
        .flatMapLatest { messageRepository.observeMessages(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val baseUrl: StateFlow<String?> = authPreferences.hostUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage: SharedFlow<String> = _errorMessage

    fun onMessageTextChanged(text: String) {
        _messageText.value = text
    }

    fun sendMessage() {
        val text = _messageText.value.trim()
        if (text.isEmpty()) return
        val id = _chatRowID.value
        if (id == -1L) return

        _messageText.value = ""
        viewModelScope.launch {
            messageRepository.sendMessage(id, text)
        }
    }

    fun sendTapback(type: String, messageGUID: String, partIndex: Int = 0, emoji: String? = null) {
        val id = _chatRowID.value
        if (id == -1L) return

        viewModelScope.launch {
            messageRepository.sendTapback(id, type, messageGUID, partIndex, emoji)
        }
    }

    fun removeTapback(type: String, messageGUID: String, partIndex: Int = 0, emoji: String? = null) {
        val id = _chatRowID.value
        if (id == -1L) return

        viewModelScope.launch {
            messageRepository.removeTapback(id, type, messageGUID, partIndex, emoji)
        }
    }

    fun retryMessage(rowID: Long) {
        viewModelScope.launch {
            messageRepository.retryMessage(rowID)
        }
    }

    fun deletePendingMessage(rowID: Long) {
        viewModelScope.launch {
            messageRepository.deletePendingMessage(rowID)
        }
    }

    fun retryTapback(rowID: Long) {
        viewModelScope.launch {
            messageRepository.retryTapback(rowID)
        }
    }

    fun deletePendingReaction(rowID: Long) {
        viewModelScope.launch {
            messageRepository.deletePendingReaction(rowID)
        }
    }

    fun requestManualDownload(messageRowID: Long, rowID: Long) {
        attachmentDownloadManager.requestManualDownload(messageRowID, rowID)
    }

    fun observeAttachments(messageRowID: Long): Flow<List<AttachmentInfo>> =
        messageRepository.observeAttachments(messageRowID)

    fun ensureAttachmentMetadata(messageRowID: Long) {
        viewModelScope.launch {
            attachmentDownloadManager.enqueueDownloads(
                messageRepository.observeAttachments(messageRowID)
                    .first()
                    .filter { it.downloadState == "pending" }
                    .map { attachment ->
                        rip.build.courier.domain.model.AttachmentKey(
                            messageRowID = attachment.messageRowID,
                            attachmentRowID = attachment.rowID
                        )
                    }
            )
        }
    }
}
