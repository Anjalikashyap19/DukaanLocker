package com.iadv.dukaanlocker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.iadv.dukaanlocker.MainActivity
import com.iadv.dukaanlocker.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DukaanFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID = "dukaan_notifications_v2"
        const val CHANNEL_NAME = "Dukaan Notifications"
        const val ALERT_CHANNEL_ID = "dukaan_alerts_v1"
        const val ALERT_CHANNEL_NAME = "Document Alerts"

        // Urgent expiry alerts get their own high-importance channel
        private val ALERT_TYPES = setOf("EXPIRING_SOON", "EXPIRED")
        private val RENEWAL_TYPES = setOf("EXPIRING_SOON", "EXPIRED", "RENEWAL_REQUESTED", "RENEWAL_COMPLETED")
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: ${token.take(20)}...")
        // Token refresh may happen while logged out. ApiClient skips the
        // request until a JWT exists; the next successful login/app launch
        // registers the current token again.
        CoroutineScope(Dispatchers.IO).launch {
            com.iadv.dukaanlocker.api.ApiClient.registerDeviceToken(this@DukaanFirebaseMessagingService, token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received: ${message.notification?.title}")

        val title = message.notification?.title ?: message.data["title"] ?: "DukaanLocker"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val type = message.data["type"] ?: "GENERAL"

        showNotification(
            title = title,
            body = body,
            type = type,
            route = message.data["route"],
            shopId = message.data["shopId"],
            documentType = message.data["documentType"]
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String,
        route: String?,
        shopId: String?,
        documentType: String?
    ) {
        createNotificationChannels()

        val channelId = if (type in ALERT_TYPES) ALERT_CHANNEL_ID else CHANNEL_ID

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_type", type)
            putExtra("notification_route", route)
            putExtra("notification_shop_id", shopId)
            putExtra("notification_document_type", documentType)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // "Renew" quick action on expiry alerts: opens the app on the
        // Notifications screen where the user confirms the renewal request.
        if (type in RENEWAL_TYPES) {
            val renewIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notification_type", type)
                putExtra("notification_route", "renewal")
                putExtra("notification_shop_id", shopId)
                putExtra("notification_document_type", documentType)
                putExtra("notification_action", "renew")
            }
            val renewPendingIntent = PendingIntent.getActivity(
                this, 1, renewIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                NotificationCompat.Action(R.drawable.ic_notification, "Renew", renewPendingIntent)
            )
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val defaultChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for document expiry, missing documents, and more"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                ALERT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Urgent alerts for expiring and expired documents"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400, 200, 400)
                setSound(
                    android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            notificationManager.createNotificationChannel(defaultChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun sendTokenToServer(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            com.iadv.dukaanlocker.api.ApiClient.registerDeviceToken(this@DukaanFirebaseMessagingService, token)
        }
    }
}
