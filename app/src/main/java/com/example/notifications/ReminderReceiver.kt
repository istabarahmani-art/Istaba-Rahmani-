package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "StudyPulse Reminder"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "Check your upcoming school schedule."
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
            ?: ReminderNotificationHelper.CHANNEL_ROUTINES
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 1001)

        ReminderNotificationHelper.showNotification(
            context = context,
            notificationId = notificationId,
            channelId = channelId,
            title = title,
            body = body
        )
    }

    companion object {
        const val EXTRA_TITLE = "extra_reminder_title"
        const val EXTRA_BODY = "extra_reminder_body"
        const val EXTRA_CHANNEL_ID = "extra_reminder_channel_id"
        const val EXTRA_NOTIFICATION_ID = "extra_reminder_notification_id"
    }
}
