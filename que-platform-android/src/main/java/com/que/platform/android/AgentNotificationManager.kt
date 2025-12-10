package com.que.platform.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.que.core.AgentLogger

/**
 * Manages the foreground service notification and its updates.
 * Extracts notification logic from the main service.
 */
class AgentNotificationManager(private val context: Context) {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "QueAgentServiceChannel"
        const val NOTIFICATION_ID = 2001
        private const val ACTION_STOP_SERVICE = "com.que.platform.android.ACTION_STOP_SERVICE"
        private const val TAG = "AgentNotificationManager"
    }

    private val notificationManager: NotificationManager = 
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Que Agent Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(serviceChannel)
            AgentLogger.d(TAG, "Notification channel created")
        }
    }

    fun buildForegroundNotification(contentText: String): Notification {
        val stopIntent = Intent(context, QueAgentService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Que Agent Running")
            .setContentText(contentText)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun updateStatus(statusText: String) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildForegroundNotification(statusText))
        } catch (e: Exception) {
            AgentLogger.e(TAG, "Failed to update notification status", e)
        }
    }
}
