package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class DashboardFirstRunFlowTest {

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
    fun firstRunDashboard_showsNeutralBaselineGuidance() {
        composeRule.onNodeWithTag(TestTags.Dashboard.Screen).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Dashboard.BalanceBaselineCard).assertIsDisplayed()
        composeRule.onNodeWithText("Learning your baseline").assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.Dashboard.GenerateWorkoutButton).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Very Unbalanced").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithContentDescription(
            label = "Training balance baseline is still learning",
            substring = true
        ).assertIsDisplayed()
    }
}
