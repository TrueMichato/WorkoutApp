package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.repository.ExerciseRepository
import com.example.workoutapp.ui.exercises.ExerciseLibraryFilter
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
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

        composeRule.onNode(hasText("Exercises") and hasClickAction()).performClick()
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

        composeRule.onNode(hasText("Exercises") and hasClickAction()).performClick()
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
}
