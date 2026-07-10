package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.workoutapp.data.local.WorkoutDatabase
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
}

