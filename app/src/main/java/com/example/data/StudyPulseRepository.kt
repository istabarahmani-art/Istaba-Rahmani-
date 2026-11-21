package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudyPulseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Please sign in with Google to access your StudyPulse data.")
        if (expectedUserId != null) {
            check(uid == expectedUserId) { "Signed-in account changed; refusing cross-user operation." }
        }
        return uid
    }

    private fun <T> observeSubcollection(
        userId: String,
        collectionName: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/$collectionName"
        emitAll(
            db.collection("users").document(uid).collection(collectionName)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        clazz,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRoutines(userId: String): Flow<List<RoutineItem>> =
        observeSubcollection(userId, "routines", RoutineItem::class.java)

    fun observeAssignments(userId: String): Flow<List<AssignmentItem>> =
        observeSubcollection(userId, "assignments", AssignmentItem::class.java)

    fun observeStudySessions(userId: String): Flow<List<StudySession>> =
        observeSubcollection(userId, "study_sessions", StudySession::class.java)

    // --- Routines CRUD ---

    suspend fun addRoutine(userId: String, item: RoutineItem): Result<Unit> {
        var docPath = "users/$userId/routines"
        return try {
            val uid = requireUserId(userId)
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 100) { "Class title must be 1–100 characters." }
            val subject = item.subject.trim().take(60)
            val location = item.location.trim().take(80)
            val notes = item.notes.trim().take(500)
            require(item.dayOfWeek in 1L..7L) { "Day of week must be Monday–Sunday." }
            require(item.startMinuteOfDay in 0L..1439L) { "Invalid start time." }
            require(item.durationMinutes in 5L..600L) { "Duration must be 5–600 minutes." }
            require(item.reminderMinutesBefore in 0L..1440L) { "Invalid reminder lead time." }

            val docRef = db.collection("users").document(uid).collection("routines").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to title,
                "subject" to subject,
                "location" to location,
                "dayOfWeek" to item.dayOfWeek,
                "startMinuteOfDay" to item.startMinuteOfDay,
                "durationMinutes" to item.durationMinutes,
                "reminderMinutesBefore" to item.reminderMinutesBefore,
                "reminderEnabled" to item.reminderEnabled,
                "notes" to notes,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateRoutine(userId: String, item: RoutineItem): Result<Unit> {
        var docPath = "users/$userId/routines/${item.id}"
        return try {
            val uid = requireUserId(userId)
            require(item.id.isNotBlank()) { "Routine ID is required." }
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 100) { "Class title must be 1–100 characters." }
            require(item.dayOfWeek in 1L..7L) { "Day of week must be Monday–Sunday." }
            require(item.startMinuteOfDay in 0L..1439L) { "Invalid start time." }
            require(item.durationMinutes in 5L..600L) { "Duration must be 5–600 minutes." }
            require(item.reminderMinutesBefore in 0L..1440L) { "Invalid reminder lead time." }

            val docRef = db.collection("users").document(uid).collection("routines").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to title,
                    "subject" to item.subject.trim().take(60),
                    "location" to item.location.trim().take(80),
                    "dayOfWeek" to item.dayOfWeek,
                    "startMinuteOfDay" to item.startMinuteOfDay,
                    "durationMinutes" to item.durationMinutes,
                    "reminderMinutesBefore" to item.reminderMinutesBefore,
                    "reminderEnabled" to item.reminderEnabled,
                    "notes" to item.notes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleRoutineReminder(userId: String, routineId: String, enabled: Boolean): Result<Unit> {
        val docPath = "users/$userId/routines/$routineId"
        return try {
            val uid = requireUserId(userId)
            require(routineId.isNotBlank()) { "Routine ID is required." }
            db.collection("users").document(uid).collection("routines").document(routineId)
                .update(
                    mapOf(
                        "reminderEnabled" to enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteRoutine(userId: String, routineId: String): Result<Unit> {
        val docPath = "users/$userId/routines/$routineId"
        return try {
            val uid = requireUserId(userId)
            require(routineId.isNotBlank()) { "Routine ID is required." }
            db.collection("users").document(uid).collection("routines").document(routineId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Assignments CRUD ---

    suspend fun addAssignment(userId: String, item: AssignmentItem): Result<Unit> {
        var docPath = "users/$userId/assignments"
        return try {
            val uid = requireUserId(userId)
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 120) { "Assignment title must be 1–120 characters." }
            require(item.dueAtMillis >= 0L) { "Due date is invalid." }
            require(item.reminderMinutesBefore in 0L..10080L) { "Invalid reminder lead time." }
            val priority = AssignmentPriority.fromRaw(item.priority).name

            val docRef = db.collection("users").document(uid).collection("assignments").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to title,
                "subject" to item.subject.trim().take(60),
                "details" to item.details.trim().take(1000),
                "dueAtMillis" to item.dueAtMillis,
                "priority" to priority,
                "completed" to item.completed,
                "reminderMinutesBefore" to item.reminderMinutesBefore,
                "reminderEnabled" to item.reminderEnabled,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAssignment(userId: String, item: AssignmentItem): Result<Unit> {
        val docPath = "users/$userId/assignments/${item.id}"
        return try {
            val uid = requireUserId(userId)
            require(item.id.isNotBlank()) { "Assignment ID is required." }
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 120) { "Assignment title must be 1–120 characters." }
            require(item.dueAtMillis >= 0L) { "Due date is invalid." }
            require(item.reminderMinutesBefore in 0L..10080L) { "Invalid reminder lead time." }
            val priority = AssignmentPriority.fromRaw(item.priority).name

            db.collection("users").document(uid).collection("assignments").document(item.id)
                .update(
                    mapOf(
                        "title" to title,
                        "subject" to item.subject.trim().take(60),
                        "details" to item.details.trim().take(1000),
                        "dueAtMillis" to item.dueAtMillis,
                        "priority" to priority,
                        "completed" to item.completed,
                        "reminderMinutesBefore" to item.reminderMinutesBefore,
                        "reminderEnabled" to item.reminderEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleAssignmentCompleted(userId: String, assignmentId: String, completed: Boolean): Result<Unit> {
        val docPath = "users/$userId/assignments/$assignmentId"
        return try {
            val uid = requireUserId(userId)
            require(assignmentId.isNotBlank()) { "Assignment ID is required." }
            db.collection("users").document(uid).collection("assignments").document(assignmentId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAssignment(userId: String, assignmentId: String): Result<Unit> {
        val docPath = "users/$userId/assignments/$assignmentId"
        return try {
            val uid = requireUserId(userId)
            require(assignmentId.isNotBlank()) { "Assignment ID is required." }
            db.collection("users").document(uid).collection("assignments").document(assignmentId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- Study Sessions CRUD ---

    suspend fun addStudySession(userId: String, item: StudySession): Result<Unit> {
        var docPath = "users/$userId/study_sessions"
        return try {
            val uid = requireUserId(userId)
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 120) { "Session title must be 1–120 characters." }
            require(item.scheduledAtMillis >= 0L) { "Scheduled time is invalid." }
            require(item.durationMinutes in 5L..480L) { "Duration must be 5–480 minutes." }
            require(item.reminderMinutesBefore in 0L..1440L) { "Invalid reminder lead time." }

            val docRef = db.collection("users").document(uid).collection("study_sessions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to title,
                "subject" to item.subject.trim().take(60),
                "scheduledAtMillis" to item.scheduledAtMillis,
                "durationMinutes" to item.durationMinutes,
                "reminderMinutesBefore" to item.reminderMinutesBefore,
                "reminderEnabled" to item.reminderEnabled,
                "completed" to item.completed,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateStudySession(userId: String, item: StudySession): Result<Unit> {
        val docPath = "users/$userId/study_sessions/${item.id}"
        return try {
            val uid = requireUserId(userId)
            require(item.id.isNotBlank()) { "Session ID is required." }
            val title = item.title.trim()
            require(title.isNotEmpty() && title.length <= 120) { "Session title must be 1–120 characters." }
            require(item.scheduledAtMillis >= 0L) { "Scheduled time is invalid." }
            require(item.durationMinutes in 5L..480L) { "Duration must be 5–480 minutes." }
            require(item.reminderMinutesBefore in 0L..1440L) { "Invalid reminder lead time." }

            db.collection("users").document(uid).collection("study_sessions").document(item.id)
                .update(
                    mapOf(
                        "title" to title,
                        "subject" to item.subject.trim().take(60),
                        "scheduledAtMillis" to item.scheduledAtMillis,
                        "durationMinutes" to item.durationMinutes,
                        "reminderMinutesBefore" to item.reminderMinutesBefore,
                        "reminderEnabled" to item.reminderEnabled,
                        "completed" to item.completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleStudySessionCompleted(userId: String, sessionId: String, completed: Boolean): Result<Unit> {
        val docPath = "users/$userId/study_sessions/$sessionId"
        return try {
            val uid = requireUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID is required." }
            db.collection("users").document(uid).collection("study_sessions").document(sessionId)
                .update(
                    mapOf(
                        "completed" to completed,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteStudySession(userId: String, sessionId: String): Result<Unit> {
        val docPath = "users/$userId/study_sessions/$sessionId"
        return try {
            val uid = requireUserId(userId)
            require(sessionId.isNotBlank()) { "Session ID is required." }
            db.collection("users").document(uid).collection("study_sessions").document(sessionId)
                .delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
