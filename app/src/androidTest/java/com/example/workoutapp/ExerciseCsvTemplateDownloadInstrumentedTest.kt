package com.example.workoutapp

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasType
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workoutapp.data.csv.ExerciseCsvTemplate
import com.example.workoutapp.data.local.WorkoutDatabase
import com.example.workoutapp.ui.test.TestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the discoverable "Save CSV template" action without driving the real system file
 * picker: Espresso-Intents stubs the [Intent.ACTION_CREATE_DOCUMENT] result so the test stays
 * hermetic while still proving the button launches the expected, permission-free SAF intent.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ExerciseCsvTemplateDownloadInstrumentedTest {

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
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun saveTemplateButton_launchesCreateDocumentIntentWithoutBroadStoragePermissions() {
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT))
            .respondWith(Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null))

        composeRule.onNodeWithTag(TestTags.BottomNav.Exercises).performClick()
        composeRule.onNodeWithTag(TestTags.Exercises.Screen)
        composeRule.onNodeWithTag(TestTags.Exercises.SaveTemplateButton).performClick()

        Intents.intended(
            allOf(
                hasAction(Intent.ACTION_CREATE_DOCUMENT),
                hasType("text/csv"),
                hasExtra(Intent.EXTRA_TITLE, ExerciseCsvTemplate.suggestedFileName)
            )
        )
    }
}
