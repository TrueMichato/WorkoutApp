package com.example.workoutapp.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.domain.DashboardAnalytics
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToExercises: () -> Unit,
    onNavigateToWorkout: () -> Unit,
    onNavigateToGenerateWorkout: () -> Unit,
    onNavigateToPT: () -> Unit,
    onNavigateToActiveWorkout: (Long) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Brain", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToGenerateWorkout,
                icon = { Icon(Icons.Default.PlayArrow, "Start Workout") },
                text = { Text("Start Workout") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.error?.let { error ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            error,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            // Quick stats
            item { QuickStatsCard(uiState.exerciseCount, uiState.completedWorkouts, uiState.totalTrainingMinutes) }

            uiState.recommendation?.let { recommendation ->
                item {
                    DashboardRecommendationCard(
                        recommendation = recommendation,
                        onAction = {
                            when (recommendation.action) {
                                DashboardAnalytics.RecommendationAction.OPEN_PT -> onNavigateToPT()
                                DashboardAnalytics.RecommendationAction.GENERATE_WORKOUT -> onNavigateToGenerateWorkout()
                                DashboardAnalytics.RecommendationAction.OPEN_EXERCISES -> onNavigateToExercises()
                                DashboardAnalytics.RecommendationAction.OPEN_WORKOUTS -> onNavigateToWorkout()
                            }
                        }
                    )
                }
            }

            // Active workout
            uiState.activeSession?.let { session ->
                item { ActiveWorkoutCard(session.name.ifEmpty { "Current Workout" }) { onNavigateToActiveWorkout(session.id) } }
            }

            // PT Reminders
            if (uiState.pendingPTRoutines.isNotEmpty()) {
                item { PTRemindersSection(uiState.pendingPTRoutines.size, onNavigateToPT) }
            }

            // ── Balance Score ────────────────────────────────────────────
            if (uiState.balanceScore >= 0) {
                item { BalanceScoreCard(uiState.balanceScore) }
            }

            // ── Category Balance Bars ────────────────────────────────────
            if (uiState.categoryBalances.isNotEmpty()) {
                item { CategoryBalanceBarsCard(uiState.categoryBalances, onNavigateToWorkout) }
            }

            // ── This-Week Summary ────────────────────────────────────────
            uiState.thisWeekSummary?.let { summary ->
                item { ThisWeekSummaryCard(summary) }
            }

            // ── Weekly Trend Mini-Chart ──────────────────────────────────
            if (uiState.weeklyTrend.isNotEmpty()) {
                item { WeeklyTrendCard(uiState.weeklyTrend) }
            }

            // ── Neglected Exercise Alerts ────────────────────────────────
            if (uiState.neglectedExercises.isNotEmpty()) {
                item { NeglectedExercisesCard(uiState.neglectedExercises.take(5), onNavigateToExercises) }
            }

            // Quick actions
            item { QuickActionsRow(onNavigateToGenerateWorkout, onNavigateToExercises, onNavigateToPT) }

            // Recent workouts
            item { Text("Recent Workouts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }

            if (uiState.recentWorkouts.isEmpty()) {
                item { EmptyRecentWorkoutsCard() }
            } else {
                items(uiState.recentWorkouts, key = { it.id }) { session ->
                    RecentWorkoutCard(session)
                }
            }

            // Bottom spacer for FAB
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════
// Composable cards
// ═══════════════════════════════════════════════════════════════════════

@Composable
private fun QuickStatsCard(exerciseCount: Int, completedWorkouts: Int, totalMinutes: Int) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem(exerciseCount.toString(), "Exercises", Icons.Default.FitnessCenter)
            StatItem(completedWorkouts.toString(), "Workouts", Icons.Default.CheckCircle)
            StatItem("${totalMinutes / 60}h", "Total Time", Icons.Default.Timer)
        }
    }
}

@Composable
private fun StatItem(value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ActiveWorkoutCard(sessionName: String, onContinue: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), onClick = onContinue) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Workout in Progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(sessionName, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.Default.ChevronRight, "Continue")
        }
    }
}

@Composable
private fun PTRemindersSection(routineCount: Int, onViewAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), onClick = onViewAll) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Healing, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Physical Therapy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$routineCount routine(s) pending today", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

// ── Balance Score ────────────────────────────────────────────────────

@Composable
private fun BalanceScoreCard(score: Int) {
    val scoreColor = when {
        score >= 80 -> MaterialTheme.colorScheme.primary
        score >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val label = when {
        score >= 80 -> "Well Balanced"
        score >= 50 -> "Somewhat Balanced"
        score >= 25 -> "Needs Attention"
        else -> "Very Unbalanced"
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Circular score indicator
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { score / 100f },
                    modifier = Modifier.size(64.dp),
                    color = scoreColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 6.dp
                )
                Text("$score", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scoreColor)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Training Balance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = scoreColor)
                Text("Score based on category rotation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Category Balance Bars ────────────────────────────────────────────

@Composable
private fun CategoryBalanceBarsCard(balances: List<DashboardAnalytics.CategoryBalance>, onViewDetails: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Category Distribution", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onViewDetails) { Text("Details") }
            }
            balances.take(8).forEach { balance ->
                CategoryBalanceBar(balance)
            }
        }
    }
}

@Composable
private fun CategoryBalanceBar(balance: DashboardAnalytics.CategoryBalance) {
    val pct = balance.balancePct.coerceIn(0f, 1.5f)
    val barColor = when {
        pct >= 0.8f -> MaterialTheme.colorScheme.primary
        pct >= 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    val dayLabel = if (balance.daysSinceLast >= 999) "Never" else "${balance.daysSinceLast}d ago"

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(balance.category.displayName, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(
            Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier.fillMaxHeight().fillMaxWidth(fraction = (pct / 1.5f).coerceIn(0f, 1f)).clip(RoundedCornerShape(6.dp)).background(barColor)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(dayLabel, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(48.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── This-Week Summary ────────────────────────────────────────────────

@Composable
private fun ThisWeekSummaryCard(summary: DashboardAnalytics.ThisWeekSummary) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("This Week", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.sessionsCompleted}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Sessions", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.totalMinutes}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Minutes", style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${summary.categoriesTrained.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Categories", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (summary.categoriesMissed.isNotEmpty()) {
                Text("Not yet trained: ${summary.categoriesMissed.joinToString { it.displayName }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ── Weekly Trend Mini-Chart ──────────────────────────────────────────

@Composable
private fun WeeklyTrendCard(trend: List<DashboardAnalytics.WeeklyTrend>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weekly Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val maxSessions = trend.maxOf { it.sessionCount }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                trend.forEach { week ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("${week.sessionCount}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        val barHeight = if (maxSessions > 0) (week.sessionCount.toFloat() / maxSessions * 48f).coerceAtLeast(2f) else 2f
                        Box(
                            Modifier.width(16.dp).height(barHeight.dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(
                                if (week.sessionCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(week.weekLabel, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── Neglected Exercises ──────────────────────────────────────────────

@Composable
private fun NeglectedExercisesCard(alerts: List<DashboardAnalytics.NeglectedExerciseAlert>, onViewAll: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Neglected Exercises", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onViewAll) { Text("Browse") }
            }
            alerts.forEach { alert ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(alert.exerciseName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${alert.daysSincePerformed}d ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

// ── Quick Actions & Recent Workouts ──────────────────────────────────

@Composable
private fun QuickActionsRow(onGenerateWorkout: () -> Unit, onBrowseExercises: () -> Unit, onViewPT: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickActionButton(Modifier.weight(1f), Icons.Default.AutoAwesome, "Generate", onGenerateWorkout)
        QuickActionButton(Modifier.weight(1f), Icons.Default.Search, "Exercises", onBrowseExercises)
        QuickActionButton(Modifier.weight(1f), Icons.Default.Healing, "PT", onViewPT)
    }
}

@Composable
private fun QuickActionButton(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    OutlinedCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyRecentWorkoutsCard() {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FitnessCenter, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No workouts yet", style = MaterialTheme.typography.bodyLarge)
                Text("Start your first workout!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecentWorkoutCard(session: WorkoutSession) {
    val dateFmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.FitnessCenter, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(session.name.ifEmpty { "Workout" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dateStr = session.completedAt?.let { dateFmt.format(Date(it)) } ?: session.startedAt?.let { dateFmt.format(Date(it)) } ?: ""
                Text(dateStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            session.actualDurationMinutes?.let { mins ->
                Text("${mins}m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DashboardRecommendationCard(
    recommendation: DashboardAnalytics.DashboardRecommendation,
    onAction: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (recommendation.action) {
                        DashboardAnalytics.RecommendationAction.OPEN_PT -> Icons.Default.Healing
                        DashboardAnalytics.RecommendationAction.GENERATE_WORKOUT -> Icons.Default.AutoAwesome
                        DashboardAnalytics.RecommendationAction.OPEN_EXERCISES -> Icons.Default.Search
                        DashboardAnalytics.RecommendationAction.OPEN_WORKOUTS -> Icons.Default.PlayCircle
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(recommendation.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        recommendation.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(recommendation.actionLabel)
            }
        }
    }
}
