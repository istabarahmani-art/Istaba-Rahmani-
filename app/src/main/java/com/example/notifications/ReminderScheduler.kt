package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.AssignmentItem
import com.example.data.RoutineItem
import com.example.data.StudySession
import com.example.util.DateTimeFormatters
import java.util.Calendar

object ReminderScheduler {
    private const val TAG = "ReminderScheduler"
    private const val PREFS_NAME = "studypulse_alarm_registry"
    private const val KEY_ACTIVE_CODES = "active_request_codes"

    fun syncReminders(
        context: Context,
        routines: List<RoutineItem>,
        assignments: List<AssignmentItem>,
        sessions: List<StudySession>
    ) {
        val appContext = context.applicationContext
        ReminderNotificationHelper.ensureChannels(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Cancel previously scheduled alarms before re-registering active ones
        val previousCodes = prefs.getStringSet(KEY_ACTIVE_CODES, emptySet()).orEmpty()
        previousCodes.forEach { codeStr ->
            codeStr.toIntOrNull()?.let { cancelAlarm(appContext, alarmManager, it) }
        }

        val nowMillis = System.currentTimeMillis()
        val newCodes = mutableSetOf<String>()

        // 1. Assignments
        assignments
            .filter { !it.completed && it.reminderEnabled && it.dueAtMillis > nowMillis }
            .forEach { item ->
                val triggerAt = (item.dueAtMillis - item.reminderMinutesBefore * 60_000L)
                if (triggerAt > nowMillis) {
                    val code = ("assignment:${item.id}").hashCode()
                    val subjectPrefix = if (item.subject.isNotBlank()) "${item.subject}: " else ""
                    val leadLabel = DateTimeFormatters.formatLeadTime(item.reminderMinutesBefore)
                    scheduleAlarm(
                        context = appContext,
                        alarmManager = alarmManager,
                        requestCode = code,
                        triggerAtMillis = triggerAt,
                        channelId = ReminderNotificationHelper.CHANNEL_DEADLINES,
                        title = "Deadline $leadLabel: $subjectPrefix${item.title}",
                        body = "Due ${DateTimeFormatters.formatDateTime(item.dueAtMillis)}. Tap to review in StudyPulse."
                    )
                    newCodes.add(code.toString())
                }
            }

        // 2. Study Sessions
        sessions
            .filter { !it.completed && it.reminderEnabled && it.scheduledAtMillis > nowMillis }
            .forEach { session ->
                val triggerAt = session.scheduledAtMillis - session.reminderMinutesBefore * 60_000L
                if (triggerAt > nowMillis) {
                    val code = ("session:${session.id}").hashCode()
                    val subjectPrefix = if (session.subject.isNotBlank()) "${session.subject} • " else ""
                    scheduleAlarm(
                        context = appContext,
                        alarmManager = alarmManager,
                        requestCode = code,
                        triggerAtMillis = triggerAt,
                        channelId = ReminderNotificationHelper.CHANNEL_STUDY_SESSIONS,
                        title = "Study Session Soon: ${session.title}",
                        body = "${subjectPrefix}${session.durationMinutes} min focus block starts at ${DateTimeFormatters.formatTimeOnly(session.scheduledAtMillis)}."
                    )
                    newCodes.add(code.toString())
                }
            }

        // 3. Weekly Routines (next upcoming occurrence within 7 days)
        routines
            .filter { it.reminderEnabled }
            .forEach { routine ->
                val nextTrigger = nextRoutineTriggerMillis(routine, nowMillis)
                if (nextTrigger != null && nextTrigger > nowMillis) {
                    val code = ("routine:${routine.id}").hashCode()
                    val locText = if (routine.location.isNotBlank()) " in ${routine.location}" else ""
                    scheduleAlarm(
                        context = appContext,
                        alarmManager = alarmManager,
                        requestCode = code,
                        triggerAtMillis = nextTrigger,
                        channelId = ReminderNotificationHelper.CHANNEL_ROUTINES,
                        title = "Upcoming Class: ${routine.title}",
                        body = "Starts at ${DateTimeFormatters.formatMinuteOfDay(routine.startMinuteOfDay)}$locText."
                    )
                    newCodes.add(code.toString())
                }
            }

        prefs.edit().putStringSet(KEY_ACTIVE_CODES, newCodes).apply()
    }

    fun cancelAllReminders(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousCodes = prefs.getStringSet(KEY_ACTIVE_CODES, emptySet()).orEmpty()
        previousCodes.forEach { codeStr ->
            codeStr.toIntOrNull()?.let { cancelAlarm(appContext, alarmManager, it) }
        }
        prefs.edit().remove(KEY_ACTIVE_CODES).apply()
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        requestCode: Int,
        triggerAtMillis: Long,
        channelId: String,
        title: String,
        body: String
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_BODY, body)
            putExtra(ReminderReceiver.EXTRA_CHANNEL_ID, channelId)
            putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, requestCode)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm permission unavailable; falling back to inexact alarm", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(context: Context, alarmManager: AlarmManager, requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun nextRoutineTriggerMillis(routine: RoutineItem, nowMillis: Long): Long? {
        val targetCalendarDay = isoDayToCalendarDay(routine.dayOfWeek.toInt())
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, (routine.startMinuteOfDay / 60L).toInt())
            set(Calendar.MINUTE, (routine.startMinuteOfDay % 60L).toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val currentCalDay = cal.get(Calendar.DAY_OF_WEEK)
        var daysAhead = (targetCalendarDay - currentCalDay + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, daysAhead)
        var triggerAt = cal.timeInMillis - routine.reminderMinutesBefore * 60_000L
        if (triggerAt <= nowMillis) {
            cal.add(Calendar.DAY_OF_YEAR, 7)
            triggerAt = cal.timeInMillis - routine.reminderMinutesBefore * 60_000L
        }
        return triggerAt
    }

    private fun isoDayToCalendarDay(isoDay: Int): Int = when (isoDay) {
        1 -> Calendar.MONDAY
        2 -> Calendar.TUESDAY
        3 -> Calendar.WEDNESDAY
        4 -> Calendar.THURSDAY
        5 -> Calendar.FRIDAY
        6 -> Calendar.SATURDAY
        else -> Calendar.SUNDAY
    }
}
