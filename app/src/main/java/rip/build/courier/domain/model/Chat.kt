package rip.build.courier.domain.model

import android.net.Uri

data class Chat(
    val rowID: Long,
    val guid: String,
    val chatIdentifier: String,
    val displayName: String?,
    val serviceName: String?,
    val isGroup: Boolean,
    val lastMessageDate: String?,
    val lastMessageText: String?,
    val lastMessageIsFromMe: Boolean?,
    val isPinned: Boolean = false,
    val hasUnreads: Boolean = false,
    val unreadCount: Int = 0,
    val lastReadMessageDate: String? = null,
    val resolvedName: String? = null,
    val avatarUri: Uri? = null,
    val avatarInitials: String? = null
) {
    val title: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: resolvedName?.takeIf { it.isNotBlank() }
            ?: chatIdentifier
}
