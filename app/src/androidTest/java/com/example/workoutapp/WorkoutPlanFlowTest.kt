package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.ExerciseProgrammingPreset
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.TrainingPhase
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutPlanFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var database: WorkoutDatabase

    @Inject
    lateinit var exerciseRepository: ExerciseRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            database.clearAllTables()
        }
    }

    @Test
    fun newPlanSaveAndPlay_startsActiveWorkoutWithRichPrescriptionSummary() {
        val exerciseName = "Planner Test Squat ${System.currentTimeMillis()}"
        val planName = "Leg Day ${System.currentTimeMillis()}"

        val exerciseId = runBlocking {
            exerciseRepository.createExerciseWithRelations(
                exercise = Exercise(
                    name = exerciseName,
                    description = "Strength builder",
                    difficulty = Difficulty.INTERMEDIATE,
                    defaultSets = 4,
                    defaultReps = "6-8",
                    defaultRestSeconds = 120,
                    trainingPhasePresets = Json.encodeToString(
                        mapOf(
                            TrainingPhase.BALANCED.name to ExerciseProgrammingPreset(
                                setsText = "4",
                                repsText = "6-8",
                                restSeconds = 120,
                                rounds = 3,
                                durationSeconds = 40,
                                tempo = "31X1",
                                effortTarget = "RPE 8"
                            )
                        )
                    )
                ),
                categories = listOf(WorkoutCategory.STRENGTH),
                equipmentIds = emptyList(),
                primaryMuscles = listOf(MuscleGroup.QUADS)
            )
        }

        composeRule.onNodeWithTag(TestTags.BottomNav.Workout).performClick()
        composeRule.onNodeWithTag(TestTags.Workout.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Workout.NewPlanButton).performClick()

        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.NameField).performTextInput(planName)
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutPlanEditor.AddExerciseButton))
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.AddExerciseButton).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.ExerciseSearchField).performTextInput(exerciseName)
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.exerciseOption(exerciseId)).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutPlanEditor.SaveAndPlayButton))
        composeRule.onNodeWithTag(TestTags.WorkoutPlanEditor.SaveAndPlayButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(exerciseName).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue(composeRule.onAllNodesWithText(exerciseName).fetchSemanticsNodes().isNotEmpty())

        // The rich prescription summary (rounds/tempo/effort) can render below the fold in the
        // active workout's scrollable content list, so scroll each target into view before
        // asserting it rather than assuming it's already on screen.
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.ContentList)
            .performScrollToNode(hasText("3 rounds", substring = true))
        composeRule.onNodeWithText("3 rounds", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.ActiveWorkout.ContentList)
            .performScrollToNode(hasText("Tempo 31X1", substring = true))
        composeRule.onNodeWithText("Tempo 31X1", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.ActiveWorkout.ContentList)
            .performScrollToNode(hasText("RPE 8", substring = true))
        composeRule.onNodeWithText("RPE 8", substring = true).assertIsDisplayed()
    }
}
