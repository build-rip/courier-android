package rip.build.courier.data.repository

import android.content.Context
import android.util.Log
import rip.build.courier.data.local.dao.AttachmentDao
import rip.build.courier.data.remote.auth.AuthPreferences
import rip.build.courier.domain.model.AttachmentKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttachmentDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val attachmentDao: AttachmentDao,
    private val authPreferences: AuthPreferences,
    private val okHttpClient: OkHttpClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val downloadChannel = Channel<List<AttachmentKey>>(Channel.UNLIMITED)
    private val semaphore = Semaphore(3)
    private val attachmentsDir = File(context.filesDir, "attachments").also { it.mkdirs() }

    init {
        // Crash recovery: reset stuck downloads
        scope.launch {
            val stuck = attachmentDao.getStuckDownloads()
            for (entity in stuck) {
                attachmentDao.updateDownloadState(entity.messageRowID, entity.rowID, "pending", 0, null)
            }
        }

        // Process download queue
        scope.launch {
            for (keys in downloadChannel) {
                for (key in keys) {
                    scope.launch {
                        semaphore.withPermit {
                            downloadAttachment(key)
                        }
                    }
                }
            }
        }
    }

    fun enqueueDownloads(keys: List<AttachmentKey>) {
        if (keys.isEmpty()) return
        scope.launch {
            downloadChannel.send(keys)
        }
    }

    fun requestManualDownload(messageRowID: Long, rowID: Long) {
        scope.launch {
            // Reset state and attempts from too_large/failed to pending, then enqueue
            val attachment = attachmentDao.getById(messageRowID, rowID) ?: return@launch
            if (attachment.downloadId == null) return@launch
            attachmentDao.resetForManualRetry(messageRowID, rowID)
            downloadChannel.send(listOf(AttachmentKey(messageRowID = messageRowID, attachmentRowID = rowID)))
        }
    }

    private suspend fun downloadAttachment(key: AttachmentKey) {
        val entity = attachmentDao.getById(key.messageRowID, key.attachmentRowID) ?: return
        if (entity.downloadState != "pending") return
        val downloadId = entity.downloadId ?: run {
            attachmentDao.updateDownloadState(entity.messageRowID, entity.rowID, "unavailable", 0, null)
            return
        }

        val hostUrl = authPreferences.getHostUrl() ?: return
        val url = "$hostUrl/api/attachments/$downloadId"

        attachmentDao.updateDownloadState(entity.messageRowID, entity.rowID, "downloading", 0, null)

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                Log.w(TAG, "Download failed for ${entity.messageRowID}:${entity.rowID}: HTTP ${response.code}")
                attachmentDao.markDownloadFailed(entity.messageRowID, entity.rowID)
                response.close()
                return
            }

            val body = response.body ?: run {
                attachmentDao.markDownloadFailed(entity.messageRowID, entity.rowID)
                return
            }

            val ext = extensionFromName(entity.transferName) ?: extensionFromMime(entity.mimeType) ?: "bin"
            val filePrefix = "${entity.messageRowID}_${entity.rowID}"
            val targetFile = File(attachmentsDir, "$filePrefix.$ext")
            val tempFile = File(attachmentsDir, "$filePrefix.$ext.tmp")

            try {
                var totalRead = 0L
                var lastProgressUpdate = 0L
                tempFile.outputStream().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            // Update progress every ~64KB
                            if (totalRead - lastProgressUpdate >= 65536) {
                                attachmentDao.updateDownloadProgress(entity.messageRowID, entity.rowID, totalRead)
                                lastProgressUpdate = totalRead
                            }
                        }
                    }
                }

                // Only mark the attachment complete once the cached file is really in place.
                check(tempFile.renameTo(targetFile)) {
                    "Failed to move attachment into cache: ${targetFile.absolutePath}"
                }
                attachmentDao.updateDownloadState(entity.messageRowID, entity.rowID, "completed", totalRead, targetFile.absolutePath)
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download failed for ${entity.messageRowID}:${entity.rowID}", e)
            attachmentDao.markDownloadFailed(entity.messageRowID, entity.rowID)
        }
    }

    private fun extensionFromName(name: String?): String? =
        name?.substringAfterLast('.', "")?.ifEmpty { null }

    private fun extensionFromMime(mime: String?): String? = when {
        mime == null -> null
        mime.startsWith("image/jpeg") -> "jpg"
        mime.startsWith("image/png") -> "png"
        mime.startsWith("image/gif") -> "gif"
        mime.startsWith("image/heic") -> "heic"
        mime.startsWith("image/webp") -> "webp"
        mime.startsWith("video/mp4") -> "mp4"
        mime.startsWith("video/quicktime") -> "mov"
        else -> mime.substringAfter('/').take(10)
    }

    fun deleteAllFiles() {
        attachmentsDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private const val TAG = "AttachmentDownload"
    }
}
