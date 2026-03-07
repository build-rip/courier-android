package rip.build.courier.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import rip.build.courier.MainActivity
import rip.build.courier.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val SERVICE_CHANNEL_ID = "bridge_service"
        const val MESSAGE_CHANNEL_ID = "messages"
        const val SERVICE_NOTIFICATION_ID = 1
        private var messageNotificationCounter = 100
    }

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)

        val serviceChannel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            context.getString(R.string.notification_channel_service),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }

        val messageChannel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_HIGH
        )

        manager.createNotificationChannels(listOf(serviceChannel, messageChannel))
    }

    fun buildServiceNotification(): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Courier Bridge")
            .setContentText("Connected")
            .setOngoing(true)
            .setContentIntent(pendingIntent)
    }

    fun showMessageNotification(sender: String, text: String?, chatRowID: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra("chatRowID", chatRowID)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, chatRowID.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(sender)
            .setContentText(text ?: "Attachment")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(messageNotificationCounter++, notification)
    }
}
