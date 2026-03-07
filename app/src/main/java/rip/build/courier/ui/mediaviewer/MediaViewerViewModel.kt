package rip.build.courier.ui.mediaviewer

import android.app.Application
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import rip.build.courier.data.repository.MessageRepository
import rip.build.courier.domain.model.AttachmentInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MediaViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val messageRepository: MessageRepository,
    private val application: Application
) : ViewModel() {

    val chatRowID: Long = savedStateHandle.get<Long>("chatRowID") ?: -1L
    private val messageRowID: Long = savedStateHandle.get<Long>("messageRowID") ?: -1L
    private val attachmentRowID: Long = savedStateHandle.get<Long>("attachmentRowID") ?: -1L

    val mediaItems: StateFlow<List<AttachmentInfo>> = messageRepository
        .observeMediaForChat(chatRowID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val initialPageIndex: StateFlow<Int> = mediaItems
        .map { items ->
            items.indexOfFirst {
                it.messageRowID == messageRowID && it.rowID == attachmentRowID
            }.coerceAtLeast(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun getShareIntent(attachment: AttachmentInfo): Intent? {
        val path = attachment.localFilePath ?: return null
        val file = File(path)
        if (!file.exists()) return null

        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = attachment.mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun saveToDevice(attachment: AttachmentInfo) {
        val path = attachment.localFilePath ?: return
        val file = File(path)
        if (!file.exists()) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val collection = if (attachment.isImage) {
                            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        } else {
                            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        }

                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, attachment.transferName ?: file.name)
                            put(MediaStore.MediaColumns.MIME_TYPE, attachment.mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH,
                                if (attachment.isImage) Environment.DIRECTORY_PICTURES + "/Courier"
                                else Environment.DIRECTORY_MOVIES + "/Courier"
                            )
                        }

                        val uri = application.contentResolver.insert(collection, values)
                        if (uri != null) {
                            application.contentResolver.openOutputStream(uri)?.use { output ->
                                file.inputStream().use { input -> input.copyTo(output) }
                            }
                        }
                    } else {
                        val dir = if (attachment.isImage) {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                        } else {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                        }
                        val destDir = File(dir, "Courier")
                        destDir.mkdirs()
                        val dest = File(destDir, attachment.transferName ?: file.name)
                        file.copyTo(dest, overwrite = true)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "Saved", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(application, "Save failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
