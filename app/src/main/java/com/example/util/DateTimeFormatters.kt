package com.example.util

import com.example.data.AssignmentItem
import com.example.data.RoutineItem
import com.example.data.StudySession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeFormatters {
    private val dateTimeFmt = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
    private val shortDateFmt = SimpleDateFormat("MMM d", Locale.getDefault())
    private val timeOnlyFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

    fun currentIsoDayOfWeek(): Int {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun dayNameFull(isoDay: Int): String = when (isoDay) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Day $isoDay"
    }

    fun dayNameShort(isoDay: Int): String = when (isoDay) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        7 -> "Sun"
        else -> "D$isoDay"
    }

    fun formatMinuteOfDay(minuteOfDay: Long): String {
        val total = minuteOfDay.coerceIn(0L, 1439L).toInt()
        val hour24 = total / 60
        val min = total % 60
        val amPm = if (hour24 < 12) "AM" else "PM"
        val hour12 = when (val h = hour24 % 12) {
            0 -> 12
            else -> h
        }
        return String.format(Locale.getDefault(), "%d:%02d %s", hour12, min, amPm)
    }

    fun formatTimeRange(startMinute: Long, durationMinutes: Long): String {
        val endMinute = (startMinute + durationMinutes).coerceAtMost(1439L)
        return "${formatMinuteOfDay(startMinute)} – ${formatMinuteOfDay(endMinute)}"
    }

    fun formatDateTime(millis: Long): String = dateTimeFmt.format(Date(millis))

    fun formatShortDate(millis: Long): String = shortDateFmt.format(Date(millis))

    fun formatTimeOnly(millis: Long): String = timeOnlyFmt.format(Date(millis))

    fun formatRelativeDue(dueMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = dueMillis - nowMillis
        val absMinutes = kotlin.math.abs(diff) / 60_000L
        val hours = absMinutes / 60L
        val days = hours / 24L
        return when {
            diff < 0 -> when {
                days >= 1 -> "Overdue by ${days}d"
                hours >= 1 -> "Overdue by ${hours}h"
                else -> "Overdue"
            }
            days >= 2 -> "Due in ${days}d (${formatShortDate(dueMillis)})"
            days == 1L -> "Tomorrow • ${formatTimeOnly(dueMillis)}"
            hours >= 1 -> "In ${hours}h ${absMinutes % 60}m"
            else -> "In ${absMinutes.coerceAtLeast(1)}m"
        }
    }

    fun formatLeadTime(minutes: Long): String = when {
        minutes <= 0L -> "At time"
        minutes < 60L -> "${minutes}m before"
        minutes == 60L -> "1h before"
        minutes < 1440L -> "${minutes / 60L}h before"
        else -> "${minutes / 1440L}d before"
    }

    fun buildScheduleContextForAi(
        routines: List<RoutineItem>,
        assignments: List<AssignmentItem>,
        sessions: List<StudySession>
    ): String {
        val now = System.currentTimeMillis()
        val todayIso = currentIsoDayOfWeek()
        val sb = StringBuilder()
        sb.appendLine("Current Local Time: ${dateTimeFmt.format(Date(now))} (Today is ${dayNameFull(todayIso)}).")
        sb.appendLine()
        sb.appendLine("=== SCHOOL ROUTINES (Weekly Schedule) ===")
        if (routines.isEmpty()) {
            sb.appendLine("No routines added yet.")
        } else {
            routines.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinuteOfDay })).forEach { r ->
                val loc = if (r.location.isNotBlank()) " @ ${r.location}" else ""
                val subj = if (r.subject.isNotBlank()) " [${r.subject}]" else ""
                val note = if (r.notes.isNotBlank()) " — Notes: ${r.notes}" else ""
                sb.appendLine(
                    "- ${dayNameFull(r.dayOfWeek.toInt())}, ${formatTimeRange(r.startMinuteOfDay, r.durationMinutes)}: ${r.title}$subj$loc (Reminder: ${if (r.reminderEnabled) formatLeadTime(r.reminderMinutesBefore) else "Off"})$note"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("=== ASSIGNMENTS & DEADLINES ===")
        if (assignments.isEmpty()) {
            sb.appendLine("No assignments added yet.")
        } else {
            assignments.sortedBy { it.dueAtMillis }.forEach { a ->
                val status = if (a.completed) "COMPLETED" else "PENDING (${formatRelativeDue(a.dueAtMillis, now)})"
                val subj = if (a.subject.isNotBlank()) " [${a.subject}]" else ""
                val details = if (a.details.isNotBlank()) " — Details: ${a.details}" else ""
                sb.appendLine(
                    "- ${a.title}$subj | Priority: ${a.priority} | Due: ${formatDateTime(a.dueAtMillis)} | Status: $status$details"
                )
            }
        }
        sb.appendLine()
        sb.appendLine("=== PLANNED STUDY SESSIONS ===")
        if (sessions.isEmpty()) {
            sb.appendLine("No study sessions scheduled yet.")
        } else {
            sessions.sortedBy { it.scheduledAtMillis }.forEach { s ->
                val status = if (s.completed) "COMPLETED" else "PLANNED"
                val subj = if (s.subject.isNotBlank()) " [${s.subject}]" else ""
                sb.appendLine(
                    "- ${s.title}$subj | ${formatDateTime(s.scheduledAtMillis)} (${s.durationMinutes} min) | Status: $status"
                )
            }
        }
        return sb.toString()
    }
}
