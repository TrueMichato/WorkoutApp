package com.example.workoutapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workoutapp.data.csv.ExerciseCsvImporter
import com.example.workoutapp.data.local.MediaStorageManager
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PTRoutineExerciseCrossRef
import com.example.workoutapp.data.model.PhysicalTherapyRoutine
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.SetLog
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.repository.EquipmentRepository
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.SessionExerciseConfig
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import com.example.workoutapp.ui.exercises.AddEditExerciseViewModel
import com.example.workoutapp.ui.exercises.ExerciseLibraryFilter
import com.example.workoutapp.ui.exercises.ExercisesViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExerciseProtectionInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: WorkoutDatabase

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    @Inject
    lateinit var equipmentRepository: EquipmentRepository

    @Inject
    lateinit var workoutSessionRepository: WorkoutSessionRepository

    @Inject
    lateinit var exerciseCsvImporter: ExerciseCsvImporter

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
        }
    }

    @Test
    fun editExercise_preservesTrackingAndLifecycleMetadata() = runBlocking {
        val createdAt = 1_000L
        val updatedAt = 2_000L
        val lastPerformedAt = 3_000L
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Original metadata row",
                description = "Before",
                difficulty = Difficulty.ADVANCED,
                estimatedDurationSeconds = 720,
                timesPerformed = 12,
                lastPerformedAt = lastPerformedAt,
                isFavorite = true,
                isArchived = true,
                createdAt = createdAt,
                updatedAt = updatedAt
            ),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = listOf(MuscleGroup.CHEST)
        )

        val mediaStorageManager = MediaStorageManager(InstrumentationRegistry.getInstrumentation().targetContext)
        val viewModel = AddEditExerciseViewModel(exerciseRepository, equipmentRepository, mediaStorageManager)
        viewModel.loadExercise(exerciseId)
        waitUntil { !viewModel.uiState.value.isLoading && viewModel.uiState.value.name == "Original metadata row" }

        viewModel.updateName("Edited metadata row")
        viewModel.updateDescription("After")
        viewModel.updateDefaultRest(45)
        viewModel.saveExercise()
        waitUntil { viewModel.uiState.value.saveSuccess || viewModel.uiState.value.saveError != null }

        assertNull(viewModel.uiState.value.saveError)
        val saved = exerciseRepository.getExerciseById(exerciseId)
        assertNotNull(saved)
        requireNotNull(saved)
        assertEquals("Edited metadata row", saved.name)
        assertEquals("After", saved.description)
        assertEquals(45, saved.defaultRestSeconds)
        assertEquals(720, saved.estimatedDurationSeconds)
        assertEquals(12, saved.timesPerformed)
        assertEquals(lastPerformedAt, saved.lastPerformedAt)
        assertTrue(saved.isFavorite)
        assertTrue(saved.isArchived)
        assertEquals(createdAt, saved.createdAt)
    }

    @Test
    fun archiveAndRestore_filtersActiveLibraryButKeepsArchivedLookup() = runBlocking {
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Archive visibility row"),
            categories = listOf(WorkoutCategory.MOBILITY),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )

        assertTrue(exerciseRepository.getAllExercises().first().any { it.id == exerciseId })

        exerciseRepository.archiveExercise(exerciseId)

        assertFalse(exerciseRepository.getAllExercises().first().any { it.id == exerciseId })
        val archived = exerciseRepository.getAllExercisesIncludingArchived().first().single { it.id == exerciseId }
        assertTrue(archived.isArchived)

        exerciseRepository.unarchiveExercise(exerciseId)

        val restored = exerciseRepository.getAllExercises().first().single { it.id == exerciseId }
        assertFalse(restored.isArchived)
    }

    @Test
    fun exerciseLibraryFilter_showsArchivedExercisesOnlyWhenSelected() = runBlocking {
        val activeId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Active library row"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )
        val archivedId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Archived library row"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )
        exerciseRepository.archiveExercise(archivedId)
        val viewModel = ExercisesViewModel(exerciseRepository, exerciseCsvImporter)

        val activeState = viewModel.uiState.first { !it.isLoading && it.exercises.any { exercise -> exercise.id == activeId } }

        assertEquals(ExerciseLibraryFilter.ACTIVE, activeState.libraryFilter)
        assertTrue(activeState.exercises.any { it.id == activeId })
        assertFalse(activeState.exercises.any { it.id == archivedId })

        viewModel.onLibraryFilterSelected(ExerciseLibraryFilter.ARCHIVED)

        val archivedState = viewModel.uiState.first { it.libraryFilter == ExerciseLibraryFilter.ARCHIVED && !it.isLoading }
        assertFalse(archivedState.exercises.any { it.id == activeId })
        assertTrue(archivedState.exercises.single { it.id == archivedId }.isArchived)
    }

    @Test
    fun archivePreservesHistoryPlanAndPtReferences_andHardDeleteIsGuarded() = runBlocking {
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Referenced exercise row"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = listOf(MuscleGroup.QUADS)
        )
        val sessionId = workoutSessionRepository.createSessionWithExercises(
            session = WorkoutSession(name = "Referenced session"),
            exerciseConfigs = listOf(SessionExerciseConfig(exerciseId = exerciseId))
        )
        val sessionExercise = workoutSessionRepository.getExercisesForSession(sessionId).first().single()
        workoutSessionRepository.logSet(
            SetLog(
                sessionExerciseId = sessionExercise.id,
                setNumber = 1,
                reps = 8
            )
        )
        val templateId = workoutSessionRepository.createPlanTemplate(
            template = WorkoutPlanTemplate(name = "Referenced plan"),
            exercises = listOf(
                WorkoutPlanTemplateExercise(
                    templateId = 0,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    section = PlanExerciseSection.MAIN
                )
            )
        )
        val routineId = database.physicalTherapyDao().insertRoutine(
            PhysicalTherapyRoutine(name = "Referenced PT routine")
        )
        database.physicalTherapyDao().insertRoutineExercise(
            PTRoutineExerciseCrossRef(routineId = routineId, exerciseId = exerciseId)
        )

        exerciseRepository.archiveExercise(exerciseId)

        assertFalse(exerciseRepository.getAllExercises().first().any { it.id == exerciseId })
        assertTrue(exerciseRepository.getAllExercisesIncludingArchived().first().single { it.id == exerciseId }.isArchived)
        assertEquals(exerciseId, workoutSessionRepository.getExercisesForSession(sessionId).first().single().exerciseId)
        assertEquals(1, workoutSessionRepository.getSetLogsForExercise(sessionExercise.id).first().single().setNumber)
        assertEquals(exerciseId, workoutSessionRepository.getExercisesForPlanTemplate(templateId).single().exerciseId)
        assertEquals(exerciseId, database.physicalTherapyDao().getExercisesForRoutine(routineId).single().exerciseId)

        val deleteResult = runCatching {
            exerciseRepository.hardDeleteExerciseIfUnreferenced(exerciseId)
        }

        assertTrue(deleteResult.isFailure)
        assertNotNull(exerciseRepository.getExerciseById(exerciseId))
        assertEquals(exerciseId, workoutSessionRepository.getExercisesForSession(sessionId).first().single().exerciseId)
        assertEquals(1, workoutSessionRepository.getSetLogsForExercise(sessionExercise.id).first().single().setNumber)
    }

    @Test
    fun hardDeleteExerciseIfUnreferenced_removesOnlyUnusedExercises() = runBlocking {
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Unused hard delete row"),
            categories = listOf(WorkoutCategory.CUSTOM),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )

        exerciseRepository.hardDeleteExerciseIfUnreferenced(exerciseId)

        assertNull(exerciseRepository.getExerciseById(exerciseId))
        assertFalse(exerciseRepository.getAllExercisesIncludingArchived().first().any { it.id == exerciseId })
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(5_000) {
            while (!predicate()) {
                delay(50)
            }
        }
    }
}
