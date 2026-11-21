package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Assignment
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.attemptAutoSignIn
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.AssignmentItem
import com.example.data.RoutineItem
import com.example.data.StudyPulseRepository
import com.example.data.StudySession
import com.example.data.UiState
import com.example.notifications.ReminderNotificationHelper
import com.example.notifications.ReminderScheduler
import com.example.ui.components.AssignmentEditorSheet
import com.example.ui.components.FocusTimerDialog
import com.example.ui.components.RemindersStatusSheet
import com.example.ui.components.RoutineEditorSheet
import com.example.ui.components.StudySessionEditorSheet
import com.example.ui.screens.AssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.TasksScreen
import com.example.ui.screens.TodayScreen
import com.example.util.DateTimeFormatters
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private data class StudyPulseTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val StudyPulseTabs = listOf(
    StudyPulseTab(R.string.nav_today, Icons.Rounded.Today, Icons.Outlined.Today, "nav_today"),
    StudyPulseTab(R.string.nav_schedule, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_schedule"),
    StudyPulseTab(R.string.nav_tasks, Icons.Rounded.CheckCircle, Icons.Outlined.CheckCircle, "nav_tasks"),
    StudyPulseTab(R.string.nav_assistant, Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_assistant")
)

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedStudyPulseShell(
                currentUserId = uid,
                userDisplayName = user.displayName,
                onSignOut = {
                    ReminderScheduler.cancelAllReminders(context)
                    signOut(credentialManager = credentialManager, scope = scope)
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedStudyPulseShell(
    currentUserId: String,
    userDisplayName: String?,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val mainViewModel: StudyPulseViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                StudyPulseViewModel(StudyPulseRepository(db), currentUserId)
            }
        }
    )

    val assistantViewModel: StudyAssistantViewModel = viewModel(key = "ai_$currentUserId")

    val routinesState by mainViewModel.routinesState.collectAsStateWithLifecycle()
    val assignmentsState by mainViewModel.assignmentsState.collectAsStateWithLifecycle()
    val sessionsState by mainViewModel.sessionsState.collectAsStateWithLifecycle()
    val focusTimer by mainViewModel.focusTimer.collectAsStateWithLifecycle()
    val aiMessages by assistantViewModel.messages.collectAsStateWithLifecycle()
    val aiStreamState by assistantViewModel.streamState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddMenu by remember { mutableStateOf(false) }

    var routineToEdit by remember { mutableStateOf<RoutineItem?>(null) }
    var showRoutineSheet by remember { mutableStateOf(false) }
    var defaultRoutineDay by remember { mutableIntStateOf(DateTimeFormatters.currentIsoDayOfWeek()) }

    var assignmentToEdit by remember { mutableStateOf<AssignmentItem?>(null) }
    var showAssignmentSheet by remember { mutableStateOf(false) }

    var sessionToEdit by remember { mutableStateOf<StudySession?>(null) }
    var showSessionSheet by remember { mutableStateOf(false) }

    var showReminderCenter by remember { mutableStateOf(false) }
    var hasNotificationPermission by remember {
        mutableStateOf(ReminderNotificationHelper.hasNotificationPermission(context))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted || ReminderNotificationHelper.hasNotificationPermission(context)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                if (hasNotificationPermission) "Reminders enabled! You'll get alerts for upcoming deadlines & sessions."
                else "Notifications are currently disabled. You can allow them in Settings anytime."
            )
        }
    }

    fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            hasNotificationPermission = ReminderNotificationHelper.hasNotificationPermission(context)
        }
    }

    LaunchedEffect(Unit) {
        ReminderNotificationHelper.ensureChannels(context)
        mainViewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Keep local AlarmManager reminders synchronized with live Firestore data
    val routinesList = (routinesState as? UiState.Success)?.data.orEmpty()
    val assignmentsList = (assignmentsState as? UiState.Success)?.data.orEmpty()
    val sessionsList = (sessionsState as? UiState.Success)?.data.orEmpty()

    LaunchedEffect(routinesList, assignmentsList, sessionsList) {
        ReminderScheduler.syncReminders(
            context = context,
            routines = routinesList,
            assignments = assignmentsList,
            sessions = sessionsList
        )
    }

    val scheduleSnapshot = remember(routinesList, assignmentsList, sessionsList) {
        DateTimeFormatters.buildScheduleContextForAi(
            routines = routinesList,
            assignments = assignmentsList,
            sessions = sessionsList
        )
    }

    // BackHandler returns to Today tab when user is on secondary tabs
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(windowInsets = WindowInsets.navigationBars) {
                        StudyPulseTabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (selectedTab != 3) {
                    Box {
                        FloatingActionButton(
                            onClick = { showAddMenu = true },
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.testTag("quick_add_fab")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = "Add item")
                        }
                        DropdownMenu(
                            expanded = showAddMenu,
                            onDismissRequest = { showAddMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add Class Routine") },
                                leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    routineToEdit = null
                                    defaultRoutineDay = DateTimeFormatters.currentIsoDayOfWeek()
                                    showRoutineSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add Assignment") },
                                leadingIcon = { Icon(Icons.Rounded.Assignment, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    assignmentToEdit = null
                                    showAssignmentSheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Schedule Study Session") },
                                leadingIcon = { Icon(Icons.Rounded.MenuBook, contentDescription = null) },
                                onClick = {
                                    showAddMenu = false
                                    sessionToEdit = null
                                    showSessionSheet = true
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        Spacer(modifier = Modifier.weight(0.2f))
                        StudyPulseTabs.forEachIndexed { index, tab ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selectedTab == index) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.titleRes)
                                    )
                                },
                                label = { Text(stringResource(tab.titleRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> TodayScreen(
                            userName = userDisplayName,
                            hasNotificationPermission = hasNotificationPermission,
                            routinesState = routinesState,
                            assignmentsState = assignmentsState,
                            sessionsState = sessionsState,
                            onRequestNotificationPermission = { requestNotificationsIfNeeded() },
                            onOpenReminderCenter = { showReminderCenter = true },
                            onSignOut = onSignOut,
                            onAddRoutine = {
                                routineToEdit = null
                                defaultRoutineDay = DateTimeFormatters.currentIsoDayOfWeek()
                                showRoutineSheet = true
                            },
                            onEditRoutine = {
                                routineToEdit = it
                                showRoutineSheet = true
                            },
                            onToggleRoutineReminder = { mainViewModel.toggleRoutineReminder(it) },
                            onDeleteRoutine = { mainViewModel.deleteRoutine(it) },
                            onAddAssignment = {
                                assignmentToEdit = null
                                showAssignmentSheet = true
                            },
                            onEditAssignment = {
                                assignmentToEdit = it
                                showAssignmentSheet = true
                            },
                            onToggleAssignmentCompleted = { mainViewModel.toggleAssignmentCompleted(it) },
                            onDeleteAssignment = { mainViewModel.deleteAssignment(it) },
                            onAddSession = {
                                sessionToEdit = null
                                showSessionSheet = true
                            },
                            onEditSession = {
                                sessionToEdit = it
                                showSessionSheet = true
                            },
                            onStartFocusTimer = { mainViewModel.startFocusTimer(it) },
                            onToggleSessionCompleted = { mainViewModel.toggleStudySessionCompleted(it) },
                            onDeleteSession = { mainViewModel.deleteStudySession(it) },
                            onAskAiQuestion = { prompt ->
                                selectedTab = 3
                                assistantViewModel.sendQuestion(prompt, scheduleSnapshot)
                            },
                            onNavigateToTab = { selectedTab = it }
                        )

                        1 -> ScheduleScreen(
                            routinesState = routinesState,
                            sessionsState = sessionsState,
                            onAddRoutine = { day ->
                                routineToEdit = null
                                defaultRoutineDay = day
                                showRoutineSheet = true
                            },
                            onEditRoutine = {
                                routineToEdit = it
                                showRoutineSheet = true
                            },
                            onToggleRoutineReminder = { mainViewModel.toggleRoutineReminder(it) },
                            onDeleteRoutine = { mainViewModel.deleteRoutine(it) },
                            onAddSession = {
                                sessionToEdit = null
                                showSessionSheet = true
                            },
                            onEditSession = {
                                sessionToEdit = it
                                showSessionSheet = true
                            },
                            onStartFocusTimer = { mainViewModel.startFocusTimer(it) },
                            onToggleSessionCompleted = { mainViewModel.toggleStudySessionCompleted(it) },
                            onDeleteSession = { mainViewModel.deleteStudySession(it) }
                        )

                        2 -> TasksScreen(
                            assignmentsState = assignmentsState,
                            onAddAssignment = {
                                assignmentToEdit = null
                                showAssignmentSheet = true
                            },
                            onEditAssignment = {
                                assignmentToEdit = it
                                showAssignmentSheet = true
                            },
                            onToggleCompleted = { mainViewModel.toggleAssignmentCompleted(it) },
                            onDeleteAssignment = { mainViewModel.deleteAssignment(it) },
                            onAskAiHelp = { assignment ->
                                selectedTab = 3
                                val subj = if (assignment.subject.isNotBlank()) " (${assignment.subject})" else ""
                                assistantViewModel.sendQuestion(
                                    "Help me prepare and study for \"${assignment.title}\"$subj. ${assignment.details}",
                                    scheduleSnapshot
                                )
                            }
                        )

                        3 -> AssistantScreen(
                            messages = aiMessages,
                            streamState = aiStreamState,
                            scheduleSnapshot = scheduleSnapshot,
                            onSendQuestion = { q, snapshot ->
                                assistantViewModel.sendQuestion(q, snapshot)
                            },
                            onClearChat = { assistantViewModel.clearConversation() }
                        )
                    }
                }
            }
        }
    }

    if (showRoutineSheet) {
        RoutineEditorSheet(
            initialItem = routineToEdit,
            defaultDayOfWeek = defaultRoutineDay,
            onDismiss = { showRoutineSheet = false },
            onSave = { item ->
                mainViewModel.saveRoutine(item, isEdit = routineToEdit != null)
                showRoutineSheet = false
            }
        )
    }

    if (showAssignmentSheet) {
        AssignmentEditorSheet(
            initialItem = assignmentToEdit,
            onDismiss = { showAssignmentSheet = false },
            onSave = { item ->
                mainViewModel.saveAssignment(item, isEdit = assignmentToEdit != null)
                showAssignmentSheet = false
            }
        )
    }

    if (showSessionSheet) {
        StudySessionEditorSheet(
            initialItem = sessionToEdit,
            onDismiss = { showSessionSheet = false },
            onSave = { item ->
                mainViewModel.saveStudySession(item, isEdit = sessionToEdit != null)
                showSessionSheet = false
            }
        )
    }

    if (showReminderCenter) {
        RemindersStatusSheet(
            hasNotificationPermission = hasNotificationPermission,
            activeRoutinesCount = routinesList.count { it.reminderEnabled },
            activeDeadlinesCount = assignmentsList.count { !it.completed && it.reminderEnabled },
            activeSessionsCount = sessionsList.count { !it.completed && it.reminderEnabled },
            onRequestPermission = { requestNotificationsIfNeeded() },
            onSendTestAlert = {
                val sent = ReminderNotificationHelper.sendTestAlert(context)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        if (sent) "Test reminder notification sent!"
                        else "Please allow notifications first so alerts can appear."
                    )
                }
                if (!sent) {
                    requestNotificationsIfNeeded()
                }
            },
            onDismiss = { showReminderCenter = false }
        )
    }

    if (focusTimer.session != null) {
        FocusTimerDialog(
            state = focusTimer,
            onTogglePause = { mainViewModel.togglePauseFocusTimer() },
            onComplete = { mainViewModel.completeActiveFocusSession() },
            onStop = { mainViewModel.stopFocusTimer() }
        )
    }
}
