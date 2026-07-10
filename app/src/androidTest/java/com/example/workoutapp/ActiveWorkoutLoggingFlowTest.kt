package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/**
 * Core active-workout logging flow: the focused exercise's Log-set form only shows metrics
 * applicable to its prescription, rejects nonsensical input with an inline error, and accepts
 * a valid set once corrected. Exercises PR #5's completion semantics indirectly by asserting
 * Done/Skip remain distinct from saving a set.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ActiveWorkoutLoggingFlowTest {

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
    fun repBasedExercise_showsRepsAndWeightAndValidatesBeforeSaving() {
        val exerciseName = "Logging Flow Squat ${System.currentTimeMillis()}"
        val planName = "Logging Flow Day ${System.currentTimeMillis()}"

        val exerciseId = runBlocking {
            exerciseRepository.createExerciseWithRelations(
                exercise = Exercise(
                    name = exerciseName,
                    description = "Rep-based strength move",
                    difficulty = Difficulty.INTERMEDIATE,
                    defaultSets = 3,
                    defaultReps = "8-12",
                    defaultRestSeconds = 90
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

        // The single planned exercise becomes the dominant "current exercise" card.
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.CurrentExerciseCard).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.RepsField).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.WeightField).assertIsDisplayed()
        // Rep-based prescriptions have no meaningful duration metric.
        assertTrue(composeRule.onAllNodesWithTag(TestTags.ActiveWorkout.DurationField).fetchSemanticsNodes().isEmpty())

        // Negative reps must be rejected with an actionable inline error, not silently dropped.
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.RepsField).performTextReplacement("-5")
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.SaveSetButton).performClick()
        composeRule.onNodeWithText("Reps can't be negative").assertIsDisplayed()

        // Correcting the input and saving succeeds and shows the logged set.
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.RepsField).performTextReplacement("10")
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.WeightField).performTextReplacement("60")
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.SaveSetButton).performClick()
        composeRule.onNodeWithText("Logged sets").assertIsDisplayed()

        // Done/Skip remain explicit, separate actions from logging a set.
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.DoneButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.ActiveWorkout.SkipButton).assertIsDisplayed()
    }
}
