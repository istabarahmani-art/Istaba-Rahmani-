package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AssignmentItem
import com.example.data.RoutineItem
import com.example.data.StudySession
import com.example.data.UiState
import com.example.ui.components.AssignmentCard
import com.example.ui.components.EmptySectionCard
import com.example.ui.components.RoutineCard
import com.example.ui.components.StudySessionCard
import com.example.util.DateTimeFormatters

@Composable
fun TodayScreen(
    userName: String?,
    hasNotificationPermission: Boolean,
    routinesState: UiState<List<RoutineItem>>,
    assignmentsState: UiState<List<AssignmentItem>>,
    sessionsState: UiState<List<StudySession>>,
    onRequestNotificationPermission: () -> Unit,
    onOpenReminderCenter: () -> Unit,
    onSignOut: () -> Unit,
    onAddRoutine: () -> Unit,
    onEditRoutine: (RoutineItem) -> Unit,
    onToggleRoutineReminder: (RoutineItem) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onAddAssignment: () -> Unit,
    onEditAssignment: (AssignmentItem) -> Unit,
    onToggleAssignmentCompleted: (AssignmentItem) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onAddSession: () -> Unit,
    onEditSession: (StudySession) -> Unit,
    onStartFocusTimer: (StudySession) -> Unit,
    onToggleSessionCompleted: (StudySession) -> Unit,
    onDeleteSession: (String) -> Unit,
    onAskAiQuestion: (String) -> Unit,
    onNavigateToTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayIso = DateTimeFormatters.currentIsoDayOfWeek()
    val routines = (routinesState as? UiState.Success)?.data.orEmpty()
    val assignments = (assignmentsState as? UiState.Success)?.data.orEmpty()
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()

    val todayRoutines = routines.filter { it.dayOfWeek == todayIso.toLong() }
    val upcomingAssignments = assignments.filter { !it.completed }.take(4)
    val upcomingSessions = sessions.filter { !it.completed }.take(3)

    val firstError = listOfNotNull(
        (routinesState as? UiState.Error)?.message,
        (assignmentsState as? UiState.Error)?.message,
        (sessionsState as? UiState.Error)?.message
    ).firstOrNull()

    val isLoading = routinesState is UiState.Loading ||
        assignmentsState is UiState.Loading ||
        sessionsState is UiState.Loading

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateTimeFormatters.dayNameFull(todayIso),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (!userName.isNullOrBlank()) {
                                "Hi, ${userName.substringBefore(" ")}"
                            } else {
                                "Welcome back"
                            },
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenReminderCenter,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("reminder_center_button")
                        ) {
                            Icon(
                                imageVector = if (hasNotificationPermission) {
                                    Icons.Rounded.NotificationsActive
                                } else {
                                    Icons.Rounded.NotificationsNone
                                },
                                contentDescription = "Notification settings",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Logout,
                                contentDescription = "Sign out",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (!hasNotificationPermission) {
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(id = R.string.notifications_permission_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = stringResource(id = R.string.notifications_permission_body),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Button(
                                onClick = onRequestNotificationPermission,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("enable_notifications_button")
                            ) {
                                Text(stringResource(id = R.string.action_enable_notifications))
                            }
                        }
                    }
                }
            }

            item {
                val nextDeadline = upcomingAssignments.firstOrNull()
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.secondary
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "TODAY'S PULSE",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${todayRoutines.size} classes today • ${assignments.count { !it.completed }} open tasks",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            if (nextDeadline != null) {
                                Text(
                                    text = "Next deadline: ${nextDeadline.title} (${DateTimeFormatters.formatRelativeDue(nextDeadline.dueAtMillis)})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                                )
                            } else {
                                Text(
                                    text = "You're all caught up on deadlines! Plan a study session or ask StudyPulse AI.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { onAskAiQuestion("What is my schedule today and what should I prioritize?") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("hero_ask_ai_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ask AI About Today")
                                }
                                FilledTonalButton(
                                    onClick = onOpenReminderCenter,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Alerts")
                                }
                            }
                        }
                    }
                }
            }

            if (firstError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = firstError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            if (isLoading && routines.isEmpty() && assignments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Today's School Routines
            item {
                SectionHeader(
                    title = "Today's Classes & Routines",
                    actionLabel = "Full Week",
                    onAction = { onNavigateToTab(1) }
                )
            }
            if (todayRoutines.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.Schedule,
                        title = "No routines scheduled for today",
                        subtitle = "Add your weekly school classes or daily study habits to get timely alerts.",
                        actionLabel = "Add Class Routine",
                        onAction = onAddRoutine
                    )
                }
            } else {
                items(todayRoutines, key = { "today_r_${it.id}" }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onToggleReminder = { onToggleRoutineReminder(routine) },
                        onEdit = { onEditRoutine(routine) },
                        onDelete = { onDeleteRoutine(routine.id) }
                    )
                }
            }

            // Upcoming Deadlines
            item {
                SectionHeader(
                    title = "Upcoming Deadlines",
                    actionLabel = "All Tasks",
                    onAction = { onNavigateToTab(2) }
                )
            }
            if (upcomingAssignments.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.Assignment,
                        title = "No pending assignments",
                        subtitle = "Track homework, essays, and exam dates so nothing slips past you.",
                        actionLabel = "Add Assignment",
                        onAction = onAddAssignment
                    )
                }
            } else {
                items(upcomingAssignments, key = { "today_a_${it.id}" }) { assignment ->
                    AssignmentCard(
                        assignment = assignment,
                        onToggleComplete = { onToggleAssignmentCompleted(assignment) },
                        onAskAiHelp = {
                            val subj = if (assignment.subject.isNotBlank()) " for ${assignment.subject}" else ""
                            onAskAiQuestion(
                                "Help me break down and study for my assignment \"${assignment.title}\"$subj. ${assignment.details}"
                            )
                        },
                        onEdit = { onEditAssignment(assignment) },
                        onDelete = { onDeleteAssignment(assignment.id) }
                    )
                }
            }

            // Study Sessions
            item {
                SectionHeader(
                    title = "Planned Study Sessions",
                    actionLabel = "Add Session",
                    onAction = onAddSession
                )
            }
            if (upcomingSessions.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.MenuBook,
                        title = "No study blocks planned",
                        subtitle = "Schedule focused study sessions with automatic start reminders and a built-in timer.",
                        actionLabel = "Schedule Study Session",
                        onAction = onAddSession
                    )
                }
            } else {
                items(upcomingSessions, key = { "today_s_${it.id}" }) { session ->
                    StudySessionCard(
                        session = session,
                        onStartFocus = { onStartFocusTimer(session) },
                        onToggleComplete = { onToggleSessionCompleted(session) },
                        onEdit = { onEditSession(session) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        TextButton(onClick = onAction, modifier = Modifier.height(48.dp)) {
            Text(actionLabel)
        }
    }
}
