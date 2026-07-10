package com.example.workoutapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.*
import com.example.workoutapp.data.repository.*
import com.example.workoutapp.domain.DashboardAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: WorkoutSessionRepository,
    private val ptRepository: PhysicalTherapyRepository,
    private val userGoalRepository: UserGoalRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeDashboardData()
    }

    private fun observeDashboardData() {
        // Exercise count
        viewModelScope.launch {
            exerciseRepository.getActiveExerciseCount().collect { exerciseCount ->
                _uiState.update { state -> state.copy(exerciseCount = exerciseCount).withRecommendation() }
            }
        }

        // Completed workouts + total minutes
        viewModelScope.launch {
            combine(
                sessionRepository.getCompletedSessionCount(),
                sessionRepository.getTotalTrainingMinutes()
            ) { completedCount, totalMinutes ->
                completedCount to totalMinutes
            }.collect { (completedCount, totalMinutes) ->
                _uiState.update {
                    it.copy(
                        completedWorkouts = completedCount,
                        totalTrainingMinutes = totalMinutes
                    ).withRecommendation()
                }
            }
        }

        // Active session
        viewModelScope.launch {
            sessionRepository.getInProgressSessionFlow().collect { session ->
                _uiState.update { it.copy(activeSession = session).withRecommendation() }
            }
        }

        // Recent workouts
        viewModelScope.launch {
            sessionRepository.getRecentCompletedSessions(5).collect { sessions ->
                _uiState.update { it.copy(recentWorkouts = sessions).withRecommendation() }
            }
        }

        // PT routines
        viewModelScope.launch {
            ptRepository.getMustDoRoutines().collect { routines ->
                val pending = routines.filter { routine ->
                    !ptRepository.isRoutineCompletedToday(routine.id)
                }
                _uiState.update { it.copy(pendingPTRoutines = pending).withRecommendation() }
            }
        }

        // Category stats + balance score + category balances
        viewModelScope.launch {
            userGoalRepository.recalculateDaysSinceLastTrained()
            userGoalRepository.getAllCategoryStats().collect { stats ->
                val statsMap = stats.associate { it.category to it.daysSinceLastTrained }
                val weightsResult = userGoalRepository.getCategoryWeightsResult()
                val weights = weightsResult.value
                val score = DashboardAnalytics.balanceScore(stats, weights)
                val balances = DashboardAnalytics.categoryBalances(stats, weights)
                _uiState.update {
                    it.copy(
                        categoryStats = statsMap,
                        categoryStatsList = stats,
                        balanceScore = score,
                        categoryBalances = balances,
                        error = weightsResult.issues.toUserMessage().ifBlank { null }
                    ).withRecommendation()
                }
            }
        }

        // Neglected exercises
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                val snapshots = exercises.map { e ->
                    DashboardAnalytics.ExerciseSnapshot(e.id, e.name, e.timesPerformed, e.lastPerformedAt)
                }
                val alerts = DashboardAnalytics.neglectedExercises(snapshots)
                _uiState.update { it.copy(neglectedExercises = alerts).withRecommendation() }
            }
        }

        // Weekly trend (last 8 weeks)
        viewModelScope.launch {
            sessionRepository.getAllSessions().collect { sessions ->
                val completed = sessions.filter { it.status == SessionStatus.COMPLETED || it.status == SessionStatus.PARTIAL }
                val trend = DashboardAnalytics.weeklyTrend(completed)
                _uiState.update { it.copy(weeklyTrend = trend).withRecommendation() }
            }
        }

        // This-week summary
        viewModelScope.launch {
            combine(
                userGoalRepository.getAllCategoryStats(),
                sessionRepository.getAllSessions()
            ) { stats, sessions ->
                val (weekStart, weekEnd) = currentWeekBounds(System.currentTimeMillis())
                val trainedThisWeek = stats
                    .filter { (it.lastTrainedAt ?: Long.MIN_VALUE) in weekStart until weekEnd }
                    .map { it.category }
                    .toSet()
                val thisWeekSessions = sessions.filter {
                    (it.completedAt ?: Long.MIN_VALUE) in weekStart until weekEnd
                }
                DashboardAnalytics.thisWeekSummary(thisWeekSessions, trainedThisWeek)
            }.collect { summary ->
                _uiState.update { it.copy(thisWeekSummary = summary).withRecommendation() }
            }
        }
    }
}

internal fun currentWeekBounds(
    now: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): Pair<Long, Long> {
    val calendar = Calendar.getInstance(timeZone).apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
    }
    val weekStart = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_MONTH, 7)
    return weekStart to calendar.timeInMillis
}

private fun DashboardUiState.withRecommendation(): DashboardUiState = copy(
    recommendation = DashboardAnalytics.nextRecommendation(
        pendingPTRoutineCount = pendingPTRoutines.size,
        balanceScore = balanceScore.coerceAtLeast(0),
        categoryBalances = categoryBalances,
        neglectedExercises = neglectedExercises
    )
)

data class DashboardUiState(
    val exerciseCount: Int = 0,
    val completedWorkouts: Int = 0,
    val totalTrainingMinutes: Int = 0,
    val activeSession: WorkoutSession? = null,
    val recentWorkouts: List<WorkoutSession> = emptyList(),
    val pendingPTRoutines: List<PhysicalTherapyRoutine> = emptyList(),
    val categoryStats: Map<WorkoutCategory, Int> = emptyMap(),
    val categoryStatsList: List<CategoryStats> = emptyList(),
    val balanceScore: Int = -1,  // -1 = not yet computed
    val categoryBalances: List<DashboardAnalytics.CategoryBalance> = emptyList(),
    val neglectedExercises: List<DashboardAnalytics.NeglectedExerciseAlert> = emptyList(),
    val weeklyTrend: List<DashboardAnalytics.WeeklyTrend> = emptyList(),
    val thisWeekSummary: DashboardAnalytics.ThisWeekSummary? = null,
    val recommendation: DashboardAnalytics.DashboardRecommendation? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)
