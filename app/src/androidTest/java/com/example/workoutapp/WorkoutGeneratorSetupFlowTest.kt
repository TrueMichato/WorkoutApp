package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import org.junit.runner.RunWith

/**
 * Covers the default-first generator setup/preview UX: the primary Generate action must be
 * reachable in the initial viewport, the Advanced/Customize section must preserve selections
 * across collapse/reopen, and the preview card's Edit-setup affordance must reliably reopen
 * Advanced without losing the generated preview. Preview generation itself is exercised through
 * the real (untouched) WorkoutPlanner, mirroring WorkoutPlanFlowTest's seeding approach.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class WorkoutGeneratorSetupFlowTest {

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
            seedExercises()
        }
    }

    private suspend fun seedExercises() {
        val suffix = System.currentTimeMillis()
        exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Generator Test Push-up $suffix",
                description = "Bodyweight push exercise",
                difficulty = Difficulty.BEGINNER,
                defaultSets = 3,
                defaultReps = "10-12",
                defaultRestSeconds = 60
            ),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = listOf(MuscleGroup.CHEST)
        )
        exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Generator Test Row $suffix",
                description = "Bodyweight pull exercise",
                difficulty = Difficulty.BEGINNER,
                defaultSets = 3,
                defaultReps = "10-12",
                defaultRestSeconds = 60
            ),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = listOf(MuscleGroup.LATS)
        )
        exerciseRepository.createExerciseWithRelations(
            exercise = Exercise(
                name = "Generator Test Squat $suffix",
                description = "Bodyweight leg exercise",
                difficulty = Difficulty.BEGINNER,
                defaultSets = 3,
                defaultReps = "12-15",
                defaultRestSeconds = 60
            ),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = listOf(MuscleGroup.QUADS)
        )
    }

    private fun openGenerator() {
        composeRule.onNodeWithTag(TestTags.BottomNav.Workout).performClick()
        composeRule.onNodeWithTag(TestTags.Workout.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Workout.NewWorkoutFab).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.Screen).assertIsDisplayed()
    }

    @Test
    fun initialViewport_showsDefaultsSummaryAndPrimaryActionWithoutExpandingAdvanced() {
        openGenerator()

        // Both the honest defaults summary and the single primary action must be visible
        // immediately - no scrolling, no expanding Advanced first.
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.SummaryCard).assertIsDisplayed()
        // The primary action's own label is checked scoped to its tag - the top app bar title
        // ("Generate Workout") shares the same text, so a bare onNodeWithText would be ambiguous.
        composeRule.onNode(hasTestTag(TestTags.WorkoutGenerator.PrimaryActionButton) and hasText("Generate Workout"))
            .assertIsDisplayed()

        // Advanced controls are not visible until the user opts in.
        composeRule.onAllNodesWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton)
            .fetchSemanticsNodes()
            .let { nodes -> assert(nodes.isEmpty()) { "Advanced section should start collapsed" } }
    }

    @Test
    fun advancedSection_preservesCategorySelectionAcrossCollapseAndReopen() {
        openGenerator()

        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.AdvancedToggle).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.ResetDefaultsButton))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton).assertIsDisplayed()

        composeRule.onNodeWithText("Strength").performClick()

        // Collapse, then reopen - the selection must still be reflected.
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.AdvancedToggle))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.AdvancedToggle).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton).assertIsNotDisplayed()

        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.AdvancedToggle).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.ResetDefaultsButton))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton).assertIsDisplayed()
        composeRule.onNodeWithText("Clear").assertIsDisplayed()
    }

    @Test
    fun previewCard_editSetupReopensAdvancedWithoutLosingPreview() {
        openGenerator()

        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.AdvancedToggle).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.PreviewButton))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.PreviewButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.WorkoutGenerator.PreviewCard).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.PreviewCard))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.PreviewCard).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.EditSetupButton).assertIsDisplayed()

        // Collapse Advanced - the preview must remain visible on its own.
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.AdvancedToggle))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.AdvancedToggle).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton).assertIsNotDisplayed()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.PreviewCard))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.PreviewCard).assertIsDisplayed()

        // Edit setup reopens Advanced from the preview card, without navigating away or losing it.
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.EditSetupButton).performClick()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.ResetDefaultsButton))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ResetDefaultsButton).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.ContentList)
            .performScrollToNode(hasTestTag(TestTags.WorkoutGenerator.PreviewCard))
        composeRule.onNodeWithTag(TestTags.WorkoutGenerator.PreviewCard).assertIsDisplayed()
    }
}
