package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

object ReminderNotificationHelper {
    const val CHANNEL_DEADLINES = "studypulse_deadlines"
    const val CHANNEL_STUDY_SESSIONS = "studypulse_study_sessions"
    const val CHANNEL_ROUTINES = "studypulse_routines"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_DEADLINES,
                    context.getString(R.string.notifications_channel_deadlines),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts for upcoming assignment, homework, and exam deadlines."
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_STUDY_SESSIONS,
                    context.getString(R.string.notifications_channel_sessions),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Reminders before scheduled study blocks start."
                    enableVibration(true)
                },
                NotificationChannel(
                    CHANNEL_ROUTINES,
                    context.getString(R.string.notifications_channel_routines),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Reminders before daily school classes and routines."
                }
            )
            manager.createNotificationChannels(channels)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun showNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        title: String,
        body: String
    ): Boolean {
        ensureChannels(context)
        if (!hasNotificationPermission(context)) return false

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_bell)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun sendTestAlert(context: Context): Boolean {
        return showNotification(
            context = context,
            notificationId = 9001,
            channelId = CHANNEL_DEADLINES,
            title = "StudyPulse Reminders Active",
            body = "You'll get timely alerts before classes, assignment deadlines, and study sessions."
        )
    }
}
