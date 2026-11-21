package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AssignmentItem
import com.example.data.RoutineItem
import com.example.data.StudyPulseRepository
import com.example.data.StudySession
import com.example.data.UiState
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusTimerState(
    val session: StudySession? = null,
    val remainingSeconds: Long = 0L,
    val totalSeconds: Long = 0L,
    val isRunning: Boolean = false
)

class StudyPulseViewModel(
    private val repository: StudyPulseRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val routinesState: StateFlow<UiState<List<RoutineItem>>> = repository.observeRoutines(currentUserId)
        .map<List<RoutineItem>, UiState<List<RoutineItem>>> { list ->
            UiState.Success(list.sortedWith(compareBy({ it.dayOfWeek }, { it.startMinuteOfDay })))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing routines", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load routines."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val assignmentsState: StateFlow<UiState<List<AssignmentItem>>> = repository.observeAssignments(currentUserId)
        .map<List<AssignmentItem>, UiState<List<AssignmentItem>>> { list ->
            UiState.Success(list.sortedWith(compareBy({ it.completed }, { it.dueAtMillis })))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing assignments", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load assignments."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val sessionsState: StateFlow<UiState<List<StudySession>>> = repository.observeStudySessions(currentUserId)
        .map<List<StudySession>, UiState<List<StudySession>>> { list ->
            UiState.Success(list.sortedWith(compareBy({ it.completed }, { it.scheduledAtMillis })))
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing study sessions", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load study sessions."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private val _focusTimer = MutableStateFlow(FocusTimerState())
    val focusTimer: StateFlow<FocusTimerState> = _focusTimer.asStateFlow()
    private var timerJob: Job? = null

    fun saveRoutine(item: RoutineItem, isEdit: Boolean) {
        viewModelScope.launch {
            val result = if (isEdit) {
                repository.updateRoutine(currentUserId, item)
            } else {
                repository.addRoutine(currentUserId, item)
            }
            result.onSuccess {
                _events.tryEmit(if (isEdit) "Routine updated" else "Class routine added")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not save routine")
            }
        }
    }

    fun toggleRoutineReminder(routine: RoutineItem) {
        viewModelScope.launch {
            repository.toggleRoutineReminder(currentUserId, routine.id, !routine.reminderEnabled)
                .onSuccess {
                    _events.tryEmit(
                        if (!routine.reminderEnabled) "Reminder enabled for ${routine.title}"
                        else "Reminder paused for ${routine.title}"
                    )
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to update reminder")
                }
        }
    }

    fun deleteRoutine(routineId: String) {
        viewModelScope.launch {
            repository.deleteRoutine(currentUserId, routineId)
                .onSuccess { _events.tryEmit("Routine removed") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "Failed to remove routine") }
        }
    }

    fun saveAssignment(item: AssignmentItem, isEdit: Boolean) {
        viewModelScope.launch {
            val result = if (isEdit) {
                repository.updateAssignment(currentUserId, item)
            } else {
                repository.addAssignment(currentUserId, item)
            }
            result.onSuccess {
                _events.tryEmit(if (isEdit) "Assignment updated" else "Assignment added")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not save assignment")
            }
        }
    }

    fun toggleAssignmentCompleted(assignment: AssignmentItem) {
        viewModelScope.launch {
            repository.toggleAssignmentCompleted(currentUserId, assignment.id, !assignment.completed)
                .onSuccess {
                    _events.tryEmit(
                        if (!assignment.completed) "Marked \"${assignment.title}\" complete!"
                        else "Reopened \"${assignment.title}\""
                    )
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to update assignment")
                }
        }
    }

    fun deleteAssignment(assignmentId: String) {
        viewModelScope.launch {
            repository.deleteAssignment(currentUserId, assignmentId)
                .onSuccess { _events.tryEmit("Assignment deleted") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "Failed to delete assignment") }
        }
    }

    fun saveStudySession(item: StudySession, isEdit: Boolean) {
        viewModelScope.launch {
            val result = if (isEdit) {
                repository.updateStudySession(currentUserId, item)
            } else {
                repository.addStudySession(currentUserId, item)
            }
            result.onSuccess {
                _events.tryEmit(if (isEdit) "Study session updated" else "Study session scheduled")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Could not save study session")
            }
        }
    }

    fun toggleStudySessionCompleted(session: StudySession) {
        viewModelScope.launch {
            repository.toggleStudySessionCompleted(currentUserId, session.id, !session.completed)
                .onSuccess {
                    _events.tryEmit(
                        if (!session.completed) "Great work! Study session completed."
                        else "Study session reopened."
                    )
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Failed to update study session")
                }
        }
    }

    fun deleteStudySession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteStudySession(currentUserId, sessionId)
                .onSuccess { _events.tryEmit("Study session deleted") }
                .onFailure { err -> _events.tryEmit(err.localizedMessage ?: "Failed to delete study session") }
        }
    }

    // --- Built-in Focus Study Timer ---

    fun startFocusTimer(session: StudySession) {
        timerJob?.cancel()
        val totalSecs = (session.durationMinutes.coerceIn(5L, 480L)) * 60L
        _focusTimer.value = FocusTimerState(
            session = session,
            remainingSeconds = totalSecs,
            totalSeconds = totalSecs,
            isRunning = true
        )
        runTimerLoop()
    }

    fun togglePauseFocusTimer() {
        val current = _focusTimer.value
        if (current.session == null) return
        if (current.isRunning) {
            timerJob?.cancel()
            _focusTimer.value = current.copy(isRunning = false)
        } else {
            _focusTimer.value = current.copy(isRunning = true)
            runTimerLoop()
        }
    }

    fun completeActiveFocusSession() {
        val session = _focusTimer.value.session
        stopFocusTimer()
        if (session != null && !session.completed) {
            toggleStudySessionCompleted(session)
        }
    }

    fun stopFocusTimer() {
        timerJob?.cancel()
        _focusTimer.value = FocusTimerState()
    }

    private fun runTimerLoop() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val current = _focusTimer.value
                if (!current.isRunning || current.session == null) break
                if (current.remainingSeconds <= 1L) {
                    _focusTimer.value = current.copy(remainingSeconds = 0L, isRunning = false)
                    _events.tryEmit("Focus session complete! Nice job.")
                    break
                } else {
                    _focusTimer.value = current.copy(remainingSeconds = current.remainingSeconds - 1L)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    private companion object {
        const val TAG = "StudyPulseVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
