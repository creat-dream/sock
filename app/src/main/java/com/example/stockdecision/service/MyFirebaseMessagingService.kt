package com.example.stockdecision.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.stockdecision.R
import com.example.stockdecision.ui.MainActivity
import com.example.stockdecision.utils.NotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging Service for receiving stock price alerts
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        const val CHANNEL_ID_STOCK_ALERTS = "stock_alerts"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * Called when a new FCM token is generated
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Store token locally - will be sent to server when app opens
        val sharedPrefs = getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putString("fcm_token", token)
            .putBoolean("token_sync_needed", true)
            .apply()
    }

    /**
     * Called when a message is received
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Message received from: ${remoteMessage.from}")

        // Check if message contains notification payload
        remoteMessage.notification?.let {
            Log.d(TAG, "Notification: ${it.title} - ${it.body}")
            showNotification(it.title ?: "股票提醒", it.body ?: "", remoteMessage.data)
        }

        // Handle data payload
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            handleDataPayload(remoteMessage.data)
        }
    }

    /**
     * Handle data payload from FCM message
     */
    private fun handleDataPayload(data: Map<String, String>) {
        when (data["type"]) {
            "PRICE_ALERT" -> {
                val stockId = data["stockId"]
                val symbol = data["symbol"]
                val currentPrice = data["currentPrice"]
                val returnRate = data["returnRate"]
                
                Log.d(TAG, "Price alert for $symbol: $$currentPrice (return: $returnRate%)")
                
                // Broadcast to update UI if needed
                sendBroadcast(Intent("com.example.stockdecision.STOCK_TRIGGERED").apply {
                    putExtra("stock_id", stockId)
                    putExtra("stock_symbol", symbol)
                    putExtra("current_price", currentPrice?.toDoubleOrNull() ?: 0.0)
                    putExtra("return_rate", returnRate?.toDoubleOrNull() ?: 0.0)
                })
            }
        }
    }

    /**
     * Show notification for stock alert
     */
    private fun showNotification(title: String, message: String, data: Map<String, String>) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create intent to open MainActivity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("notification_type", data["type"])
            putExtra("stock_id", data["stockId"])
            putExtra("stock_symbol", data["symbol"])
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID_STOCK_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))

        // Generate unique notification ID
        val notificationId = data["stockId"]?.hashCode() 
            ?: System.currentTimeMillis().toInt()

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    /**
     * Create notification channel for Android O+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "股票提醒"
            val descriptionText = "股票价格达到目标时的提醒通知"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(CHANNEL_ID_STOCK_ALERTS, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "Notification channel created")
        }
    }
}
