package com.edipismailoglu.educationtrackingapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val notificationTitle = remoteMessage.notification?.title ?: "📢 New Notification"
        val notificationBody = remoteMessage.notification?.body ?: "You have a new message"

        val type = remoteMessage.data["type"] // يمكن أن تكون: new_task أو new_answer
        val postId = remoteMessage.data["postId"] ?: ""
        val submissionId = remoteMessage.data["submissionId"] ?: ""

        when (type) {
            "new_task" -> {
                Log.d("FCM", "✅ New Task Received! Title: $notificationTitle | PostID: $postId")
            }
            "new_answer" -> {
                Log.d("FCM", "✅ New Answer Submitted! Title: $notificationTitle | SubmissionID: $submissionId")
            }
            else -> {
                Log.d("FCM", "📩 Generic Notification received: $notificationTitle - $notificationBody")
            }
        }

        // عرض الإشعار على الجهاز كالعادة
        showNotification(notificationTitle, notificationBody)
    }


    private fun showNotification(title: String, body: String) {
        val channelId = "default_channel_id"

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // تأكد من وجود الأيقونة في drawable
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // إنشاء قناة إشعارات لأندرويد 8 فما فوق
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Default Notifications", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        // عرض الإشعار بمعرف فريد
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    override fun onNewToken(token: String) {
        // طباعة التوكن الجديد (يحدث عند إعادة التثبيت أو تحديث الجهاز)
        Log.d("FCM", "New FCM Token: $token")
    }
}
