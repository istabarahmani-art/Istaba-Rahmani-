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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.AssignmentItem
import com.example.data.AssignmentPriority
import com.example.data.UiState
import com.example.ui.components.AssignmentCard
import com.example.ui.components.EmptySectionCard

enum class TaskFilter(val label: String) {
    UPCOMING("Upcoming"),
    HIGH_PRIORITY("High Priority"),
    COMPLETED("Completed")
}

@Composable
fun TasksScreen(
    assignmentsState: UiState<List<AssignmentItem>>,
    onAddAssignment: () -> Unit,
    onEditAssignment: (AssignmentItem) -> Unit,
    onToggleCompleted: (AssignmentItem) -> Unit,
    onDeleteAssignment: (String) -> Unit,
    onAskAiHelp: (AssignmentItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by rememberSaveable { mutableStateOf(TaskFilter.UPCOMING) }

    val allAssignments = (assignmentsState as? UiState.Success)?.data.orEmpty()
    val filtered = when (selectedFilter) {
        TaskFilter.UPCOMING -> allAssignments.filter { !it.completed }
        TaskFilter.HIGH_PRIORITY -> allAssignments.filter {
            !it.completed && it.priorityEnum == AssignmentPriority.HIGH
        }
        TaskFilter.COMPLETED -> allAssignments.filter { it.completed }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Assignments & Deadlines",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Stay ahead of homework, essays, and exams with smart alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            modifier = Modifier.testTag("task_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (assignmentsState is UiState.Error) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = assignmentsState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }

            if (assignmentsState is UiState.Loading && allAssignments.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filtered.isEmpty()) {
                item {
                    EmptySectionCard(
                        icon = Icons.Rounded.AssignmentTurnedIn,
                        title = when (selectedFilter) {
                            TaskFilter.UPCOMING -> "No upcoming assignments"
                            TaskFilter.HIGH_PRIORITY -> "No high-priority tasks"
                            TaskFilter.COMPLETED -> "No completed tasks yet"
                        },
                        subtitle = "Tap below to add an assignment with a deadline reminder.",
                        actionLabel = "Add Assignment",
                        onAction = onAddAssignment
                    )
                }
            } else {
                items(filtered, key = { "task_${it.id}" }) { assignment ->
                    AssignmentCard(
                        assignment = assignment,
                        onToggleComplete = { onToggleCompleted(assignment) },
                        onAskAiHelp = { onAskAiHelp(assignment) },
                        onEdit = { onEditAssignment(assignment) },
                        onDelete = { onDeleteAssignment(assignment.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}
