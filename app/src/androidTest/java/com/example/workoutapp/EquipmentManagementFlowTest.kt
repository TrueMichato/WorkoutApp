package com.example.workoutapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
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

/**
 * Connected regression test for the equipment-management crash: adding a custom equipment item
 * used to render it twice in the same LazyColumn (once under "customEquipment", once again under
 * "allEquipment") both keyed by `it.id`, which crashed Compose the first time a custom row was
 * added. This test drives the real Equipment Management UI end-to-end and asserts the app stays
 * usable and the new item appears exactly once.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EquipmentManagementFlowTest {

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
    fun addCustomEquipmentFlow_showsSingleRow_andAppRemainsUsable() {
        val equipmentName = "UI Test Sandbag ${System.currentTimeMillis()}"

        composeRule.onNodeWithTag(TestTags.BottomNav.Settings).performClick()
        composeRule.onNodeWithText("Equipment & Locations").performClick()
        composeRule.onNodeWithTag(TestTags.Equipment.Screen).assertIsDisplayed()

        // Equipment is the second tab; Locations is first and selected by default.
        composeRule.onNodeWithText("Equipment").performClick()

        composeRule.onNodeWithTag(TestTags.Equipment.AddButton).performClick()
        composeRule.onNodeWithTag(TestTags.Equipment.NameField).performTextInput(equipmentName)
        composeRule.onNodeWithTag(TestTags.Equipment.CreateButton).performClick()

        // Dialog closes only after confirmed persistence - this also proves no crash occurred,
        // since a crash would tear down the compose hierarchy entirely.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(TestTags.Equipment.NameField).fetchSemanticsNodes().isEmpty()
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(equipmentName).fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithText(equipmentName).assertIsDisplayed()

        // The app must remain fully usable. First prove the current screen still responds to
        // input by switching tabs in place - this alone would have failed had the earlier crash
        // still been present, since the whole Compose hierarchy would have torn down. Note the
        // bottom navigation bar is intentionally NOT rendered while nested inside Equipment
        // Management (see WorkoutAppMain.showBottomBar, which only shows it for top-level
        // bottomNavItems destinations), so asserting/clicking a bottom_nav_* tag from this screen
        // is not a valid usability probe here.
        composeRule.onNodeWithText("Locations").performClick()
        composeRule.onNodeWithTag(TestTags.Equipment.Screen).assertIsDisplayed()
        composeRule.onNodeWithText("Equipment").performClick()
        composeRule.onNodeWithText(equipmentName).assertIsDisplayed()

        // Now navigate away entirely (system back leaves Equipment Management, restoring the
        // bottom bar) and back in, proving the single row persists as one row (not duplicated)
        // on re-entry.
        pressBack()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TestTags.BottomNav.Settings).assertIsDisplayed()
        composeRule.onNodeWithTag(TestTags.BottomNav.Dashboard).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(TestTags.BottomNav.Settings).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Equipment & Locations").performClick()
        composeRule.onNodeWithText("Equipment").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(equipmentName).fetchSemanticsNodes().size == 1
        }
    }
}
