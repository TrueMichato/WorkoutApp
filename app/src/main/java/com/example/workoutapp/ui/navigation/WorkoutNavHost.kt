package com.example.workoutapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.workoutapp.ui.dashboard.DashboardScreen
import com.example.workoutapp.ui.exercises.ExercisesScreen
import com.example.workoutapp.ui.exercises.AddEditExerciseScreen
import com.example.workoutapp.ui.exercises.ExerciseDetailScreen
import com.example.workoutapp.ui.workout.WorkoutScreen
import com.example.workoutapp.ui.workout.WorkoutGeneratorScreen
import com.example.workoutapp.ui.workout.ActiveWorkoutScreen
import com.example.workoutapp.ui.workout.WorkoutHistoryScreen
import com.example.workoutapp.ui.workout.WorkoutPlanEditorScreen
import com.example.workoutapp.ui.physicaltherapy.PhysicalTherapyScreen
import com.example.workoutapp.ui.physicaltherapy.PTRoutineDetailScreen
import com.example.workoutapp.ui.physicaltherapy.AddPTRoutineScreen
import com.example.workoutapp.ui.settings.SettingsScreen
import com.example.workoutapp.ui.settings.GoalSettingsScreen
import com.example.workoutapp.ui.settings.StorageSettingsScreen
import com.example.workoutapp.ui.equipment.EquipmentManagementScreen
import com.example.workoutapp.ui.equipment.LocationDetailScreen

@Composable
fun WorkoutNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        // Bottom nav destinations
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToExercises = { navController.navigate(Screen.Exercises.route) },
                onNavigateToWorkout = { navController.navigate(Screen.Workout.route) },
                onNavigateToGenerateWorkout = { navController.navigate(Screen.WorkoutGenerator.route) },
                onNavigateToPT = { navController.navigate(Screen.PhysicalTherapy.route) },
                onNavigateToActiveWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                }
            )
        }

        composable(Screen.Exercises.route) {
            ExercisesScreen(
                onNavigateToExerciseDetail = { exerciseId ->
                    navController.navigate(Screen.ExerciseDetail.createRoute(exerciseId))
                },
                onNavigateToAddExercise = {
                    navController.navigate(Screen.AddExercise.route)
                }
            )
        }

        composable(Screen.Workout.route) {
            WorkoutScreen(
                onNavigateToGenerateWorkout = {
                    navController.navigate(Screen.WorkoutGenerator.route)
                },
                onNavigateToActiveWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.WorkoutHistory.route)
                },
                onNavigateToAddPlan = {
                    navController.navigate(Screen.AddWorkoutPlan.route)
                },
                onNavigateToEditPlan = { templateId ->
                    navController.navigate(Screen.EditWorkoutPlan.createRoute(templateId))
                }
            )
        }

        composable(Screen.PhysicalTherapy.route) {
            PhysicalTherapyScreen(
                onNavigateToRoutineDetail = { routineId ->
                    navController.navigate(Screen.PTRoutineDetail.createRoute(routineId))
                },
                onNavigateToAddRoutine = {
                    navController.navigate(Screen.AddPTRoutine.route)
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToGoals = {
                    navController.navigate(Screen.GoalSettings.route)
                },
                onNavigateToEquipment = {
                    navController.navigate(Screen.EquipmentManagement.route)
                },
                onNavigateToStorage = {
                    navController.navigate(Screen.StorageSettings.route)
                }
            )
        }

        // Exercise screens
        composable(
            route = Screen.ExerciseDetail.route,
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: return@composable
            ExerciseDetailScreen(
                exerciseId = exerciseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = {
                    navController.navigate(Screen.EditExercise.createRoute(exerciseId))
                },
                onNavigateToExercise = { targetExerciseId ->
                    navController.navigate(Screen.ExerciseDetail.createRoute(targetExerciseId))
                },
                onNavigateToCreateVariation = { parentExerciseId ->
                    navController.navigate(Screen.AddVariation.createRoute(parentExerciseId))
                }
            )
        }

        composable(Screen.AddExercise.route) {
            AddEditExerciseScreen(
                exerciseId = null,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.AddVariation.route,
            arguments = listOf(navArgument("parentExerciseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val parentExerciseId = backStackEntry.arguments?.getLong("parentExerciseId") ?: return@composable
            AddEditExerciseScreen(
                exerciseId = null,
                newVariationOfParentId = parentExerciseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditExercise.route,
            arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
        ) { backStackEntry ->
            val exerciseId = backStackEntry.arguments?.getLong("exerciseId") ?: return@composable
            AddEditExerciseScreen(
                exerciseId = exerciseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Workout screens
        composable(Screen.WorkoutGenerator.route) {
            WorkoutGeneratorScreen(
                onNavigateBack = { navController.popBackStack() },
                onWorkoutGenerated = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId)) {
                        popUpTo(Screen.Workout.route)
                    }
                }
            )
        }

        composable(
            route = Screen.ActiveWorkout.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            ActiveWorkoutScreen(
                sessionId = sessionId,
                onNavigateBack = { navController.popBackStack() },
                onWorkoutCompleted = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.WorkoutHistory.route) {
            WorkoutHistoryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.AddWorkoutPlan.route) {
            WorkoutPlanEditorScreen(
                templateId = null,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToActiveWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId)) {
                        popUpTo(Screen.Workout.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EditWorkoutPlan.route,
            arguments = listOf(navArgument("templateId") { type = NavType.LongType })
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getLong("templateId") ?: return@composable
            WorkoutPlanEditorScreen(
                templateId = templateId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToActiveWorkout = { sessionId ->
                    navController.navigate(Screen.ActiveWorkout.createRoute(sessionId)) {
                        popUpTo(Screen.Workout.route)
                    }
                }
            )
        }

        // Physical Therapy screens
        composable(
            route = Screen.PTRoutineDetail.route,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType })
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getLong("routineId") ?: return@composable
            PTRoutineDetailScreen(
                routineId = routineId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditPTRoutine.createRoute(id))
                }
            )
        }

        composable(Screen.AddPTRoutine.route) {
            AddPTRoutineScreen(
                editRoutineId = null,
                onNavigateBack = { navController.popBackStack() },
                onRoutineSaved = { routineId ->
                    navController.navigate(Screen.PTRoutineDetail.createRoute(routineId)) {
                        popUpTo(Screen.PhysicalTherapy.route)
                    }
                }
            )
        }

        composable(
            route = Screen.EditPTRoutine.route,
            arguments = listOf(navArgument("routineId") { type = NavType.LongType })
        ) { backStackEntry ->
            val routineId = backStackEntry.arguments?.getLong("routineId") ?: return@composable
            AddPTRoutineScreen(
                editRoutineId = routineId,
                onNavigateBack = { navController.popBackStack() },
                onRoutineSaved = { navController.popBackStack() }
            )
        }

        // Settings screens
        composable(Screen.GoalSettings.route) {
            GoalSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.StorageSettings.route) {
            StorageSettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.EquipmentManagement.route) {
            EquipmentManagementScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLocation = { locationId ->
                    navController.navigate(Screen.LocationDetail.createRoute(locationId))
                }
            )
        }

        composable(
            route = Screen.LocationDetail.route,
            arguments = listOf(navArgument("locationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val locationId = backStackEntry.arguments?.getLong("locationId") ?: return@composable
            LocationDetailScreen(
                locationId = locationId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}



