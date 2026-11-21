package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class AssignmentPriority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High");

    companion object {
        fun fromRaw(raw: String): AssignmentPriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class RoutineItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val location: String = "",
    val dayOfWeek: Long = 1L,
    val startMinuteOfDay: Long = 540L,
    val durationMinutes: Long = 60L,
    val reminderMinutesBefore: Long = 15L,
    val reminderEnabled: Boolean = true,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AssignmentItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val details: String = "",
    val dueAtMillis: Long = 0L,
    val priority: String = AssignmentPriority.MEDIUM.name,
    val completed: Boolean = false,
    val reminderMinutesBefore: Long = 60L,
    val reminderEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: AssignmentPriority
        get() = AssignmentPriority.fromRaw(priority)
}

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val subject: String = "",
    val scheduledAtMillis: Long = 0L,
    val durationMinutes: Long = 45L,
    val reminderMinutesBefore: Long = 10L,
    val reminderEnabled: Boolean = true,
    val completed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
