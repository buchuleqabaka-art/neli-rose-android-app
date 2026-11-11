package com.example.neli_roseapp

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging

// Small helper for everything FCM: channel, token, topic, showing notifications
object FcmHelper {

    const val CHANNEL_ID = "neli_care_alerts"
    private const val TOPIC_ALL = "residents"
    private const val TAG = "FcmHelper"

    // channel is required for notifications on Android 8+ (minSdk is 26, so always safe)
    fun createChannel(ctx: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Care alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Medication, schedule and care updates from Neli-Rose" }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    // get this device's token, save it to Firestore and join the broadcast topic
    fun registerDevice() {
        runCatching {
            val fcm = FirebaseMessaging.getInstance()
            fcm.token.addOnSuccessListener { saveToken(it) }
            fcm.subscribeToTopic(TOPIC_ALL)
        }.onFailure { Log.w(TAG, "FCM setup failed", it) }
    }

    // store the token on users/{uid} so the backend can message this person
    fun saveToken(token: String) {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge())
                .addOnFailureListener { Log.w(TAG, "Could not save FCM token", it) }
        }
    }

    // builds and shows a notification that opens the app when tapped
    fun showNotification(ctx: Context, title: String, body: String) {
        // Android 13+ needs the runtime permission, otherwise skip quietly
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(ctx, R.color.neli_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(ctx).notify(System.currentTimeMillis().toInt(), notification)
    }
}
