package com.example.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.AssignmentItem
import com.example.data.AssignmentPriority
import com.example.data.RoutineItem
import com.example.data.StudySession
import com.example.ui.FocusTimerState
import com.example.util.DateTimeFormatters
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorSheet(
    initialItem: RoutineItem?,
    defaultDayOfWeek: Int,
    onDismiss: () -> Unit,
    onSave: (RoutineItem) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var subject by remember { mutableStateOf(initialItem?.subject ?: "") }
    var location by remember { mutableStateOf(initialItem?.location ?: "") }
    var dayOfWeek by remember { mutableLongStateOf(initialItem?.dayOfWeek ?: defaultDayOfWeek.toLong()) }
    var startMinute by remember { mutableLongStateOf(initialItem?.startMinuteOfDay ?: 540L) }
    var duration by remember { mutableLongStateOf(initialItem?.durationMinutes ?: 60L) }
    var reminderEnabled by remember { mutableStateOf(initialItem?.reminderEnabled ?: true) }
    var reminderLead by remember { mutableLongStateOf(initialItem?.reminderMinutesBefore ?: 15L) }
    var notes by remember { mutableStateOf(initialItem?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialItem == null) "Add School Routine" else "Edit School Routine",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(100); validationError = null },
                label = { Text("Class or Routine Name *") },
                placeholder = { Text("e.g., Biology Lecture, Morning Study") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("routine_title_input")
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(60) },
                    label = { Text("Subject") },
                    placeholder = { Text("Biology") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it.take(80) },
                    label = { Text("Room / Link") },
                    placeholder = { Text("Room 204") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Text("Day of Week", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).forEach { day ->
                    FilterChip(
                        selected = dayOfWeek == day.toLong(),
                        onClick = { dayOfWeek = day.toLong() },
                        label = { Text(DateTimeFormatters.dayNameShort(day)) }
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    val h = (startMinute / 60L).toInt()
                    val m = (startMinute % 60L).toInt()
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            startMinute = (hourOfDay * 60 + minute).toLong()
                        },
                        h,
                        m,
                        false
                    ).show()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(Icons.Rounded.AccessTime, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Starts at ${DateTimeFormatters.formatMinuteOfDay(startMinute)}")
            }

            Text("Duration", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30L, 45L, 60L, 90L, 120L).forEach { mins ->
                    FilterChip(
                        selected = duration == mins,
                        onClick = { duration = mins },
                        label = { Text("${mins}m") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Class Reminder Alert", style = MaterialTheme.typography.titleSmall)
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = { reminderEnabled = it }
                )
            }

            if (reminderEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5L, 10L, 15L, 30L, 60L).forEach { mins ->
                        FilterChip(
                            selected = reminderLead == mins,
                            onClick = { reminderLead = mins },
                            label = { Text(DateTimeFormatters.formatLeadTime(mins)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(500) },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Materials to bring, textbook chapters...") },
                minLines = 2,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.trim().isEmpty()) {
                            validationError = "Please enter a class or routine name."
                            return@Button
                        }
                        onSave(
                            RoutineItem(
                                id = initialItem?.id ?: "",
                                userId = initialItem?.userId ?: "",
                                title = title.trim(),
                                subject = subject.trim(),
                                location = location.trim(),
                                dayOfWeek = dayOfWeek,
                                startMinuteOfDay = startMinute,
                                durationMinutes = duration,
                                reminderMinutesBefore = reminderLead,
                                reminderEnabled = reminderEnabled,
                                notes = notes.trim(),
                                createdAt = initialItem?.createdAt,
                                updatedAt = initialItem?.updatedAt
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_routine_button")
                ) {
                    Text("Save Routine")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssignmentEditorSheet(
    initialItem: AssignmentItem?,
    onDismiss: () -> Unit,
    onSave: (AssignmentItem) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var subject by remember { mutableStateOf(initialItem?.subject ?: "") }
    var details by remember { mutableStateOf(initialItem?.details ?: "") }
    var dueAtMillis by remember {
        mutableLongStateOf(
            initialItem?.dueAtMillis ?: (System.currentTimeMillis() + 24L * 60L * 60_000L)
        )
    }
    var priority by remember {
        mutableStateOf(initialItem?.priorityEnum ?: AssignmentPriority.MEDIUM)
    }
    var reminderEnabled by remember { mutableStateOf(initialItem?.reminderEnabled ?: true) }
    var reminderLead by remember { mutableLongStateOf(initialItem?.reminderMinutesBefore ?: 60L) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialItem == null) "Add Assignment" else "Edit Assignment",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120); validationError = null },
                label = { Text("Assignment Title *") },
                placeholder = { Text("e.g., Chapter 5 Problem Set") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assignment_title_input")
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it.take(60) },
                label = { Text("Subject / Course") },
                placeholder = { Text("e.g., Chemistry") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Priority", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssignmentPriority.entries.forEach { level ->
                    FilterChip(
                        selected = priority == level,
                        onClick = { priority = level },
                        label = { Text(level.label) }
                    )
                }
            }

            Text("Due Date & Time", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = dueAtMillis
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                dueAtMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeFormatters.formatShortDate(dueAtMillis))
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = dueAtMillis }
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = dueAtMillis
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                }
                                dueAtMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeFormatters.formatTimeOnly(dueAtMillis))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Deadline Reminder", style = MaterialTheme.typography.titleSmall)
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
            }

            if (reminderEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15L, 60L, 180L, 1440L).forEach { mins ->
                        FilterChip(
                            selected = reminderLead == mins,
                            onClick = { reminderLead = mins },
                            label = { Text(DateTimeFormatters.formatLeadTime(mins)) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = details,
                onValueChange = { details = it.take(1000) },
                label = { Text("Instructions or Details") },
                placeholder = { Text("Pages, rubric details, submission portal...") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.trim().isEmpty()) {
                            validationError = "Please enter an assignment title."
                            return@Button
                        }
                        onSave(
                            AssignmentItem(
                                id = initialItem?.id ?: "",
                                userId = initialItem?.userId ?: "",
                                title = title.trim(),
                                subject = subject.trim(),
                                details = details.trim(),
                                dueAtMillis = dueAtMillis,
                                priority = priority.name,
                                completed = initialItem?.completed ?: false,
                                reminderMinutesBefore = reminderLead,
                                reminderEnabled = reminderEnabled,
                                createdAt = initialItem?.createdAt,
                                updatedAt = initialItem?.updatedAt
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_assignment_button")
                ) {
                    Text("Save Assignment")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudySessionEditorSheet(
    initialItem: StudySession?,
    onDismiss: () -> Unit,
    onSave: (StudySession) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialItem?.title ?: "") }
    var subject by remember { mutableStateOf(initialItem?.subject ?: "") }
    var scheduledAtMillis by remember {
        mutableLongStateOf(initialItem?.scheduledAtMillis ?: (System.currentTimeMillis() + 2L * 60L * 60_000L))
    }
    var duration by remember { mutableLongStateOf(initialItem?.durationMinutes ?: 45L) }
    var reminderEnabled by remember { mutableStateOf(initialItem?.reminderEnabled ?: true) }
    var reminderLead by remember { mutableLongStateOf(initialItem?.reminderMinutesBefore ?: 10L) }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (initialItem == null) "Schedule Study Session" else "Edit Study Session",
                style = MaterialTheme.typography.headlineSmall
            )

            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(120); validationError = null },
                label = { Text("Study Topic *") },
                placeholder = { Text("e.g., Algebra Practice Quiz") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("session_title_input")
            )

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it.take(60) },
                label = { Text("Subject") },
                placeholder = { Text("Mathematics") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("When", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = scheduledAtMillis
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                scheduledAtMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeFormatters.formatShortDate(scheduledAtMillis))
                }

                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = scheduledAtMillis }
                        TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val updated = Calendar.getInstance().apply {
                                    timeInMillis = scheduledAtMillis
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                }
                                scheduledAtMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.HOUR_OF_DAY),
                            cal.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(Icons.Rounded.AccessTime, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(DateTimeFormatters.formatTimeOnly(scheduledAtMillis))
                }
            }

            Text("Focus Duration", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25L, 45L, 60L, 90L).forEach { mins ->
                    FilterChip(
                        selected = duration == mins,
                        onClick = { duration = mins },
                        label = { Text("${mins}m") }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Session Start Reminder", style = MaterialTheme.typography.titleSmall)
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
            }

            if (reminderEnabled) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5L, 10L, 15L, 30L).forEach { mins ->
                        FilterChip(
                            selected = reminderLead == mins,
                            onClick = { reminderLead = mins },
                            label = { Text(DateTimeFormatters.formatLeadTime(mins)) }
                        )
                    }
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (title.trim().isEmpty()) {
                            validationError = "Please enter a study topic."
                            return@Button
                        }
                        onSave(
                            StudySession(
                                id = initialItem?.id ?: "",
                                userId = initialItem?.userId ?: "",
                                title = title.trim(),
                                subject = subject.trim(),
                                scheduledAtMillis = scheduledAtMillis,
                                durationMinutes = duration,
                                reminderMinutesBefore = reminderLead,
                                reminderEnabled = reminderEnabled,
                                completed = initialItem?.completed ?: false,
                                createdAt = initialItem?.createdAt,
                                updatedAt = initialItem?.updatedAt
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_session_button")
                ) {
                    Text("Save Session")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersStatusSheet(
    hasNotificationPermission: Boolean,
    activeRoutinesCount: Int,
    activeDeadlinesCount: Int,
    activeSessionsCount: Int,
    onRequestPermission: () -> Unit,
    onSendTestAlert: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text("Notification & Reminder Center", style = MaterialTheme.typography.headlineSmall)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (hasNotificationPermission) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (hasNotificationPermission) {
                            "Notifications Enabled"
                        } else {
                            "Notifications Permission Needed"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasNotificationPermission) {
                            "StudyPulse automatically schedules local alerts for your enabled class routines, assignment deadlines, and study sessions."
                        } else {
                            "Grant notification permission so StudyPulse can send upcoming deadline and study session reminders."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (!hasNotificationPermission) {
                        Button(
                            onClick = onRequestPermission,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Enable Notifications")
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Scheduled Alert Summary", style = MaterialTheme.typography.titleSmall)
                    Text("• $activeRoutinesCount weekly class reminders active", style = MaterialTheme.typography.bodyMedium)
                    Text("• $activeDeadlinesCount assignment deadline reminders active", style = MaterialTheme.typography.bodyMedium)
                    Text("• $activeSessionsCount study session reminders active", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Button(
                onClick = onSendTestAlert,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("send_test_alert_button")
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Send Test Reminder Now")
            }
        }
    }
}

@Composable
fun FocusTimerDialog(
    state: FocusTimerState,
    onTogglePause: () -> Unit,
    onComplete: () -> Unit,
    onStop: () -> Unit
) {
    val session = state.session ?: return
    val mins = state.remainingSeconds / 60L
    val secs = state.remainingSeconds % 60L
    val progress = if (state.totalSeconds > 0L) {
        1f - (state.remainingSeconds.toFloat() / state.totalSeconds.toFloat())
    } else 1f

    AlertDialog(
        onDismissRequest = onStop,
        title = {
            Text(text = session.title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (session.subject.isNotBlank()) {
                    Text(
                        text = session.subject,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", mins, secs),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isRunning) "Pause" else "Resume")
                    }
                    Button(
                        onClick = onComplete,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Finish")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onStop) {
                Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Close Timer")
            }
        }
    )
}
