package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FitKuchniaMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Nowy token FCM: $token")
        try {
            FirebaseMessaging.getInstance().subscribeToTopic("all_users")
                .addOnSuccessListener {
                    Log.d(TAG, "Zasubskrybowano pomyślnie do tematu 'all_users'")
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Błąd podczas subskrybowania do tematu 'all_users': ${e.message}")
                }
        } catch (e: Throwable) {
            Log.w(TAG, "Wyjątek w onNewToken: ${e.message}")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "Odebrano wiadomość FCM od: ${remoteMessage.from}")

        val title = remoteMessage.notification?.title
            ?: remoteMessage.data["title"]
            ?: "FitKuchnia"
        val body = remoteMessage.notification?.body
            ?: remoteMessage.data["body"]
            ?: remoteMessage.data["message"]
            ?: "Sprawdź nowe aktualizacje w aplikacji!"

        showNotification(applicationContext, title, body)
    }

    companion object {
        private const val TAG = "FitKuchniaFCM"
        const val CHANNEL_ID = "fitkuchnia_announcements_channel"
        const val CHANNEL_NAME = "Aktualizacje i Ogłoszenia FitKuchnia"

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Powiadomienia o nowych aktualizacjach, przepisach i ważnych komunikatach"
                    enableVibration(true)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.createNotificationChannel(channel)
            }
        }

        fun showNotification(context: Context, title: String, body: String) {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notificationId = (System.currentTimeMillis() % 100000).toInt()
            notificationManager?.notify(notificationId, notificationBuilder.build())
        }

        fun subscribeToAllUsersTopic(context: Context? = null, onComplete: ((Boolean) -> Unit)? = null) {
            try {
                if (context != null) {
                    val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                    val code = availability.isGooglePlayServicesAvailable(context)
                    if (code != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                        Log.w(TAG, "Google Play Services nie jest dostępne (kod: $code). Pomijam subskrypcję FCM.")
                        onComplete?.invoke(false)
                        return
                    }
                }
                val fcm = FirebaseMessaging.getInstance()
                fcm.isAutoInitEnabled = true
                fcm.subscribeToTopic("all_users")
                    .addOnCompleteListener { task ->
                        val success = task.isSuccessful
                        if (success) {
                            Log.d(TAG, "Subskrypcja do 'all_users' zakończona sukcesem")
                        } else {
                            Log.w(TAG, "Subskrypcja do 'all_users' nie powiodła się: ${task.exception?.message}")
                        }
                        onComplete?.invoke(success)
                    }
            } catch (e: Throwable) {
                Log.w(TAG, "Wyjątek podczas subskrypcji do 'all_users'", e)
                onComplete?.invoke(false)
            }
        }
    }
}
