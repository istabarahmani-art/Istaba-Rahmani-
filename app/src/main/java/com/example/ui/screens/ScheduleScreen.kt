package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.RoutineItem
import com.example.data.StudySession
import com.example.data.UiState
import com.example.ui.components.EmptySectionCard
import com.example.ui.components.RoutineCard
import com.example.ui.components.StudySessionCard
import com.example.util.DateTimeFormatters

@Composable
fun ScheduleScreen(
    routinesState: UiState<List<RoutineItem>>,
    sessionsState: UiState<List<StudySession>>,
    onAddRoutine: (Int) -> Unit,
    onEditRoutine: (RoutineItem) -> Unit,
    onToggleRoutineReminder: (RoutineItem) -> Unit,
    onDeleteRoutine: (String) -> Unit,
    onAddSession: () -> Unit,
    onEditSession: (StudySession) -> Unit,
    onStartFocusTimer: (StudySession) -> Unit,
    onToggleSessionCompleted: (StudySession) -> Unit,
    onDeleteSession: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedDay by rememberSaveable { mutableIntStateOf(DateTimeFormatters.currentIsoDayOfWeek()) }

    val routines = (routinesState as? UiState.Success)?.data.orEmpty()
    val sessions = (sessionsState as? UiState.Success)?.data.orEmpty()
    val dayRoutines = routines.filter { it.dayOfWeek == selectedDay.toLong() }

    val errorMessage = (routinesState as? UiState.Error)?.message
        ?: (sessionsState as? UiState.Error)?.message

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Weekly School Schedule",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Organize recurring classes, labs, and focused study blocks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items((1..7).toList()) { day ->
                        val count = routines.count { it.dayOfWeek == day.toLong() }
                        val label = if (count > 0) {
                            "${DateTimeFormatters.dayNameShort(day)} ($count)"
                        } else {
                            DateTimeFormatters.dayNameShort(day)
                        }
                        FilterChip(
                            selected = selectedDay == day,
                            onClick = { selectedDay = day },
                            label = { Text(label) },
                            modifier = Modifier.testTag("day_chip_$day")
                        )
                    }
                }
            }

            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${DateTimeFormatters.dayNameFull(selectedDay)} Routines",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedButton(
                        onClick = { onAddRoutine(selectedDay) },
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Add Class")
                    }
                }
            }

            if (routinesState is UiState.Loading && routines.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (dayRoutines.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.CalendarToday,
                        title = "Free on ${DateTimeFormatters.dayNameFull(selectedDay)}",
                        subtitle = "No classes or routines added for this day yet.",
                        actionLabel = "Add ${DateTimeFormatters.dayNameShort(selectedDay)} Routine",
                        onAction = { onAddRoutine(selectedDay) }
                    )
                }
            } else {
                items(dayRoutines, key = { "sched_r_${it.id}" }) { routine ->
                    RoutineCard(
                        routine = routine,
                        onToggleReminder = { onToggleRoutineReminder(routine) },
                        onEdit = { onEditRoutine(routine) },
                        onDelete = { onDeleteRoutine(routine.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Study Sessions & Focus Blocks",
                        style = MaterialTheme.typography.titleLarge
                    )
                    OutlinedButton(
                        onClick = onAddSession,
                        modifier = Modifier.height(44.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Schedule")
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.MenuBook,
                        title = "No study sessions scheduled",
                        subtitle = "Set up study blocks with reminders and run a focus timer when it's time.",
                        actionLabel = "Schedule Study Session",
                        onAction = onAddSession
                    )
                }
            } else {
                items(sessions, key = { "sched_s_${it.id}" }) { session ->
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
