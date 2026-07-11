package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.MuscleGroup
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.ui.exercises.ExerciseLibraryFilter
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExerciseLibraryFlowTest {

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
    fun addExerciseFlow_savesExerciseAndShowsItInLibrary() {
        val exerciseName = "UI Test Push-up ${System.currentTimeMillis()}"

        composeRule.onNodeWithTag(TestTags.BottomNav.Exercises).performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Exercises.AddFab).performClick()

        composeRule.onNodeWithTag(TestTags.AddEditExercise.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.AddEditExercise.NameField).performTextInput(exerciseName)
        composeRule.onNodeWithTag(TestTags.AddEditExercise.SaveButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(exerciseName).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText(exerciseName).assertIsDisplayed()
    }

    @Test
    fun archivedExercisePath_opensDetailAndRestoresToActiveLibrary() {
        val exerciseName = "Archived UI Row ${System.currentTimeMillis()}"
        val exerciseId = runBlocking {
            val id = exerciseRepository.createExerciseWithRelations(
                exercise = Exercise(name = exerciseName, description = "Restore coverage"),
                categories = emptyList(),
                equipmentIds = emptyList(),
                primaryMuscles = emptyList()
            )
            exerciseRepository.archiveExercise(id)
            id
        }

        composeRule.onNodeWithTag(TestTags.BottomNav.Exercises).performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Exercises.libraryFilter(ExerciseLibraryFilter.ARCHIVED)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(exerciseName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.Exercises.exerciseCard(exerciseId)).performClick()

        composeRule.onNodeWithText("Archived").assertIsDisplayed()
        composeRule.onNodeWithText(exerciseName).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Restore").assertIsDisplayed().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { exerciseRepository.getExerciseById(exerciseId)?.isArchived == false }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Archived").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.libraryFilter(ExerciseLibraryFilter.ACTIVE)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(exerciseName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(exerciseName).assertIsDisplayed()
    }

    @Test
    fun createVariationFlow_linksToMainExerciseAndNavigatesBothWays() {
        val mainName = "UI Test Main Push-up ${System.currentTimeMillis()}"
        val variationName = "UI Test Tiger Push-up ${System.currentTimeMillis()}"
        val mainExerciseId = runBlocking {
            exerciseRepository.createExerciseWithRelations(
                exercise = Exercise(name = mainName),
                categories = emptyList(),
                equipmentIds = emptyList(),
                primaryMuscles = emptyList()
            )
        }

        composeRule.onNodeWithTag(TestTags.BottomNav.Exercises).performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Exercises.AddFab).performClick()

        composeRule.onNodeWithTag(TestTags.AddEditExercise.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.AddEditExercise.NameField).performTextInput(variationName)
        composeRule.onNodeWithTag(TestTags.AddEditExercise.FamilyParentPickerButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.AddEditExercise.familyParentOption(mainExerciseId))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.AddEditExercise.familyParentOption(mainExerciseId)).performClick()
        composeRule.onNodeWithTag(TestTags.AddEditExercise.FamilyFocusField).performTextInput("Triceps emphasis")
        composeRule.onNodeWithTag(TestTags.AddEditExercise.SaveButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(variationName).fetchSemanticsNodes().isNotEmpty()
        }

        // Library shows the "Variation of" badge on the new exercise's card.
        composeRule.onNodeWithText("Variation of $mainName").assertIsDisplayed()

        // Detail screen for the variation links back to the main exercise.
        composeRule.onNodeWithText(variationName).performClick()
        composeRule.onNodeWithText("Variation of \"$mainName\"").assertIsDisplayed()
        composeRule.onNodeWithText("Triceps emphasis").assertIsDisplayed()
        composeRule.onNodeWithText("Variation of \"$mainName\"").performClick()

        // Now on the main exercise's detail screen, showing its variation.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Variations").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(variationName).assertIsDisplayed()
    }

    @Test
    fun createVariationAction_fromMainExerciseDetail_prefillsSharedFieldsAndParent() {
        val mainName = "UI Test Direct Main Exercise ${System.currentTimeMillis()}"
        val newVariationName = "UI Test Direct Variation ${System.currentTimeMillis()}"
        val mainExerciseId = runBlocking {
            exerciseRepository.createExerciseWithRelations(
                exercise = Exercise(name = mainName, difficulty = Difficulty.ADVANCED, isCompound = true),
                categories = listOf(WorkoutCategory.STRENGTH),
                equipmentIds = emptyList(),
                primaryMuscles = listOf(MuscleGroup.CHEST)
            )
        }

        composeRule.onNodeWithTag(TestTags.BottomNav.Exercises).performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.Screen).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(mainName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(TestTags.Exercises.exerciseCard(mainExerciseId)).performClick()

        composeRule.onNodeWithContentDescription("More").performClick()
        composeRule.onNodeWithText("Create variation").assertIsDisplayed().performClick()

        // Landed on the Add Exercise form with the main exercise pre-selected as parent and
        // its shared fields (categories) copied in, but the name field left blank for the user.
        composeRule.onNodeWithTag(TestTags.AddEditExercise.Screen).assertIsDisplayed()
        composeRule.onNodeWithText("Variation of \"$mainName\"", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(TestTags.AddEditExercise.NameField).performTextInput(newVariationName)
        composeRule.onNodeWithTag(TestTags.AddEditExercise.FamilyFocusField).performTextInput("New emphasis")
        composeRule.onNodeWithTag(TestTags.AddEditExercise.SaveButton).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(newVariationName).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Variation of $mainName").assertIsDisplayed()

        runBlocking {
            val savedId = exerciseRepository.getAllExercises().first().single { it.name == newVariationName }.id
            // Categories were copied from the main exercise.
            assertTrue(WorkoutCategory.STRENGTH in exerciseRepository.getExerciseCategories(savedId))
            assertEquals(mainExerciseId, exerciseRepository.getParentExerciseId(savedId))
        }
    }
}
