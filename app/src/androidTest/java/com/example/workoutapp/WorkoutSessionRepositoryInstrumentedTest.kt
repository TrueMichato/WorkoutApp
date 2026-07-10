package com.example.workoutapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.PlanExerciseSection
import com.example.workoutapp.data.model.RichPrescriptionData
import com.example.workoutapp.data.model.TimeSlot
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutSession
import com.example.workoutapp.data.model.toJson
import com.example.workoutapp.data.model.toRichPrescriptionDataOrNull
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.data.repository.SessionExerciseConfig
import com.example.workoutapp.data.repository.WorkoutSessionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutSessionRepositoryInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var database: WorkoutDatabase

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    @Inject
    lateinit var workoutSessionRepository: WorkoutSessionRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
        }
    }

    @Test
    fun instantiateSessionFromTemplate_preservesRichPrescriptionFields() = runBlocking {
        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Template Test Row",
                difficulty = Difficulty.BEGINNER,
                defaultSets = 2,
                defaultReps = "10",
                defaultRestSeconds = 45
            ),
            categories = listOf(WorkoutCategory.MOBILITY),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList()
        )
        val richPrescription = RichPrescriptionData(
            rounds = 4,
            durationSeconds = 50,
            tempo = "20X1",
            effortTarget = "Smooth nasal breathing"
        )
        val templateId = workoutSessionRepository.createPlanTemplate(
            template = WorkoutPlanTemplate(
                name = "Recovery Circuit",
                description = "Move and breathe",
                targetDurationMinutes = 25,
                scheduledTimeSlot = TimeSlot.MORNING,
                sourcePhase = TrainingPhase.RECOVERY
            ),
            exercises = listOf(
                WorkoutPlanTemplateExercise(
                    templateId = 0,
                    exerciseId = exerciseId,
                    orderIndex = 0,
                    section = PlanExerciseSection.COOLDOWN,
                    plannedSets = 2,
                    plannedReps = "50s",
                    plannedRestSeconds = 20,
                    prescriptionJson = richPrescription.toJson(),
                    coachingNotes = "Stay relaxed"
                )
            )
        )

        val sessionId = workoutSessionRepository.instantiateSessionFromTemplate(templateId)
        val sessionExercises = workoutSessionRepository.getExercisesForSession(sessionId).first()
        val sessionExercise = sessionExercises.single()

        assertEquals(PlanExerciseSection.COOLDOWN, sessionExercise.section)
        assertEquals("50s", sessionExercise.plannedReps)
        assertEquals(20, sessionExercise.plannedRestSeconds)
        assertEquals("Stay relaxed", sessionExercise.notes)
        assertNotNull(sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull())
        assertEquals(richPrescription, sessionExercise.prescriptionJson.toRichPrescriptionDataOrNull())
    }

    @Test
    fun createSessionWithExercises_rollsBackSessionWhenExerciseInsertFails() = runBlocking {
        val result = runCatching {
            workoutSessionRepository.createSessionWithExercises(
                session = WorkoutSession(name = "Invalid session"),
                exerciseConfigs = listOf(SessionExerciseConfig(exerciseId = Long.MAX_VALUE))
            )
        }

        assertTrue(result.isFailure)
        assertTrue(workoutSessionRepository.getAllSessions().first().isEmpty())
    }

    @Test
    fun createExerciseWithRelations_persistsAllRelations() = runBlocking {
        val equipmentId = database.equipmentDao().insert(Equipment(name = "Test bands"))

        val exerciseId = exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(name = "Banded row"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = listOf(equipmentId),
            primaryMuscles = listOf(MuscleGroup.UPPER_BACK),
            secondaryMuscles = listOf(MuscleGroup.BICEPS)
        )

        assertNotNull(exerciseRepository.getExerciseById(exerciseId))
        assertEquals(listOf(WorkoutCategory.STRENGTH), exerciseRepository.getExerciseCategories(exerciseId))
        assertEquals(listOf(equipmentId), exerciseRepository.getRequiredEquipmentIds(exerciseId))
        assertEquals(listOf(MuscleGroup.UPPER_BACK), exerciseRepository.getPrimaryMuscles(exerciseId))
        assertEquals(listOf(MuscleGroup.BICEPS), exerciseRepository.getSecondaryMuscles(exerciseId))
    }
}
