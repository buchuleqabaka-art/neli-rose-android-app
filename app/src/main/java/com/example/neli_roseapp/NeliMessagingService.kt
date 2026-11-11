package com.example.neli_roseapp

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NeliMessagingService : FirebaseMessagingService() {

    // Firebase rotates tokens now and then, keep Firestore in sync
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FcmHelper.saveToken(token)
    }

    // runs when a message arrives while the app is open (closed-app messages are shown by the system)
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "Neli-Rose"
        val body = message.notification?.body ?: message.data["body"] ?: return
        FcmHelper.showNotification(this, title, body)
    }
}
