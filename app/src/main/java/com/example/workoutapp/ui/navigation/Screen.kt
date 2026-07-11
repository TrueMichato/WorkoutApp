package com.example.workoutapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation routes for the app
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    // Bottom navigation destinations
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        selectedIcon = Icons.Filled.Dashboard,
        unselectedIcon = Icons.Outlined.Dashboard
    )

    data object Exercises : Screen(
        route = "exercises",
        title = "Exercises",
        selectedIcon = Icons.Filled.FitnessCenter,
        unselectedIcon = Icons.Outlined.FitnessCenter
    )

    data object Workout : Screen(
        route = "workout",
        title = "Workout",
        selectedIcon = Icons.Filled.PlayCircle,
        unselectedIcon = Icons.Outlined.PlayCircle
    )

    data object PhysicalTherapy : Screen(
        route = "physical_therapy",
        title = "PT",
        selectedIcon = Icons.Filled.Healing,
        unselectedIcon = Icons.Outlined.Healing
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    // Detail/nested screens
    data object ExerciseDetail : Screen(
        route = "exercise/{exerciseId}",
        title = "Exercise Details"
    ) {
        fun createRoute(exerciseId: Long) = "exercise/$exerciseId"
    }

    data object AddExercise : Screen(
        route = "exercise/add",
        title = "Add Exercise"
    )

    /** Pre-fills a brand-new exercise as a variation of [parentExerciseId] (see AddEditExerciseViewModel.loadNewVariation). */
    data object AddVariation : Screen(
        route = "exercise/add/variation/{parentExerciseId}",
        title = "Add Variation"
    ) {
        fun createRoute(parentExerciseId: Long) = "exercise/add/variation/$parentExerciseId"
    }

    data object EditExercise : Screen(
        route = "exercise/edit/{exerciseId}",
        title = "Edit Exercise"
    ) {
        fun createRoute(exerciseId: Long) = "exercise/edit/$exerciseId"
    }

    data object EquipmentManagement : Screen(
        route = "equipment",
        title = "Equipment & Locations"
    )

    data object LocationDetail : Screen(
        route = "location/{locationId}",
        title = "Location"
    ) {
        fun createRoute(locationId: Long) = "location/$locationId"
    }

    data object ActiveWorkout : Screen(
        route = "workout/active/{sessionId}",
        title = "Workout"
    ) {
        fun createRoute(sessionId: Long) = "workout/active/$sessionId"
    }

    data object WorkoutHistory : Screen(
        route = "workout/history",
        title = "History"
    )

    data object WorkoutGenerator : Screen(
        route = "workout/generate",
        title = "Generate Workout"
    )

    data object AddWorkoutPlan : Screen(
        route = "workout/plan/add",
        title = "New Workout Plan"
    )

    data object EditWorkoutPlan : Screen(
        route = "workout/plan/{templateId}",
        title = "Edit Workout Plan"
    ) {
        fun createRoute(templateId: Long) = "workout/plan/$templateId"
    }

    data object PTRoutineDetail : Screen(
        route = "pt/routine/{routineId}",
        title = "PT Routine"
    ) {
        fun createRoute(routineId: Long) = "pt/routine/$routineId"
    }

    data object AddPTRoutine : Screen(
        route = "pt/add",
        title = "Add PT Routine"
    )

    data object EditPTRoutine : Screen(
        route = "pt/edit/{routineId}",
        title = "Edit PT Routine"
    ) {
        fun createRoute(routineId: Long) = "pt/edit/$routineId"
    }

    data object GoalSettings : Screen(
        route = "settings/goals",
        title = "Training Goals"
    )

    data object StorageSettings : Screen(
        route = "settings/storage",
        title = "Storage Management"
    )

    companion object {
        val bottomNavItems = listOf(Dashboard, Exercises, Workout, PhysicalTherapy, Settings)
    }
}

