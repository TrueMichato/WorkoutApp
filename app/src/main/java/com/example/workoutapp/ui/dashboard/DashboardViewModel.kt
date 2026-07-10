package com.example.workoutapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.model.*
import com.example.workoutapp.data.repository.*
import com.example.workoutapp.domain.DashboardAnalytics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
        loadDashboardData()
    }

    private fun loadDashboardData() {
        // Exercise count
        viewModelScope.launch {
            val exerciseCount = exerciseRepository.getActiveExerciseCount()
            _uiState.update { state -> state.copy(exerciseCount = exerciseCount).withRecommendation() }
        }

        // Completed workouts + total minutes
        viewModelScope.launch {
            val completedCount = sessionRepository.getCompletedSessionCount()
            val totalMinutes = sessionRepository.getTotalTrainingMinutes()
            _uiState.update {
                it.copy(completedWorkouts = completedCount, totalTrainingMinutes = totalMinutes).withRecommendation()
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
                val weights = try { userGoalRepository.getCategoryWeights() } catch (_: Exception) { emptyMap() }
                val score = DashboardAnalytics.balanceScore(stats, weights)
                val balances = DashboardAnalytics.categoryBalances(stats, weights)
                _uiState.update {
                    it.copy(
                        categoryStats = statsMap,
                        categoryStatsList = stats,
                        balanceScore = score,
                        categoryBalances = balances
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
            sessionRepository.getSessionsForToday().collect { _ ->
                val stats = _uiState.value.categoryStatsList
                val trainedThisWeek = stats.filter {
                    it.daysSinceLastTrained <= 7
                }.map { it.category }.toSet()

                sessionRepository.getRecentCompletedSessions(50).collect { sessions ->
                    val now = System.currentTimeMillis()
                    val weekAgo = now - 7 * 24 * 3600_000L
                    val thisWeekSessions = sessions.filter { (it.completedAt ?: 0L) >= weekAgo }
                    val summary = DashboardAnalytics.thisWeekSummary(thisWeekSessions, trainedThisWeek)
                    _uiState.update { it.copy(thisWeekSummary = summary).withRecommendation() }
                }
            }
        }
    }

    fun refresh() {
        loadDashboardData()
    }
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
