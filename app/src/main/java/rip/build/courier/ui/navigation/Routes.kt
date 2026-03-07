package rip.build.courier.ui.navigation

object Routes {
    const val PAIRING = "pairing"
    const val CHAT_LIST = "chat_list"
    const val SYNC_DEBUG = "sync_debug"
    const val MEDIA_VIEWER = "media_viewer/{chatRowID}/{messageRowID}/{attachmentRowID}"

    fun mediaViewer(chatRowID: Long, messageRowID: Long, attachmentRowID: Long): String =
        "media_viewer/$chatRowID/$messageRowID/$attachmentRowID"
}
