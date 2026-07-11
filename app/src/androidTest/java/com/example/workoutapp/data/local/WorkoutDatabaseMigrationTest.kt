package com.example.workoutapp.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WorkoutDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(UNSUPPORTED_DB)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DB)
        context.deleteDatabase(UNSUPPORTED_DB)
    }

    @Test
    fun shippedV5BaselineValidatesAndPreservesRepresentativeUserData() {
        helper.createDatabase(TEST_DB, WorkoutDatabaseMigrations.FIRST_SHIPPED_VERSION).apply {
            insertRepresentativeUserData()
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            WorkoutDatabaseMigrations.CURRENT_VERSION,
            true,
            *WorkoutDatabaseMigrations.ALL
        )

        assertEquals("User Exercise", db.stringForQuery("SELECT name FROM exercises WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM exercise_categories WHERE exerciseId = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM exercise_equipment WHERE exerciseId = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM exercise_muscles WHERE exerciseId = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM workout_sessions WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM session_exercises WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM set_logs WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM workout_plan_templates WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM workout_plan_template_exercises WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM pt_routines WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM pt_routine_exercises WHERE routineId = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM pt_session_logs WHERE id = 100"))
        assertEquals("MOBILITY_REHAB", db.stringForQuery("SELECT currentPhase FROM user_goals WHERE id = 1"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM category_stats WHERE category = 'STRENGTH'"))
        assertEquals("User Dumbbell", db.stringForQuery("SELECT name FROM equipment WHERE id = 100"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM location_equipment WHERE locationId = 100"))
        assertEquals("COMPLETED", db.stringForQuery("SELECT action FROM ml_feedback_events WHERE id = 100"))
        assertEquals(4L, db.longForQuery("SELECT sampleCount FROM ml_preference_scores WHERE `key` = 'exercise:100'"))

        // v6 adds the exercise_variations table; a v5 baseline has no family links, so every
        // migrated exercise (including the representative "User Exercise" above) stays standalone.
        assertEquals(0L, db.longForQuery("SELECT COUNT(*) FROM exercise_variations"))
        assertEquals(0L, db.longForQuery("SELECT COUNT(*) FROM exercise_variations WHERE variationExerciseId = 100 OR parentExerciseId = 100"))

        db.close()
    }

    @Test
    fun migrationV5ToV6CreatesExerciseVariationsTableAndSupportsFamilyLinks() {
        helper.createDatabase(TEST_DB, WorkoutDatabaseMigrations.FIRST_SHIPPED_VERSION).apply {
            insertRepresentativeUserData()
            // A second standalone exercise to link as a variation of the first one.
            execSQL(
                """
                INSERT INTO exercises (
                    id, name, description, instructions, tips, difficulty, isUnilateral, isCompound,
                    defaultSets, defaultReps, defaultRestSeconds, estimatedDurationSeconds,
                    trainingPhasePresets, localMediaUris, externalMediaUrls, timesPerformed,
                    lastPerformedAt, isFavorite, isArchived, personalNotes, createdAt, updatedAt
                ) VALUES (
                    101, 'User Exercise Variation', 'desc', 'inst', 'tips', 'ADVANCED', 1, 0,
                    4, '6-8', 120, 240, '{}', '[]', '[]', 0,
                    NULL, 0, 0, '', 1000, 1001
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            WorkoutDatabaseMigrations.CURRENT_VERSION,
            true,
            *WorkoutDatabaseMigrations.ALL
        )

        assertEquals(0L, db.longForQuery("SELECT COUNT(*) FROM exercise_variations"))

        // The new table accepts family links after migration, and both linked exercises' own
        // rows/relations are untouched by the link itself.
        db.execSQL(
            "INSERT INTO exercise_variations (variationExerciseId, parentExerciseId, focus) VALUES (101, 100, 'Triceps emphasis')"
        )
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM exercise_variations WHERE parentExerciseId = 100"))
        assertEquals("Triceps emphasis", db.stringForQuery("SELECT focus FROM exercise_variations WHERE variationExerciseId = 101"))
        assertEquals("User Exercise", db.stringForQuery("SELECT name FROM exercises WHERE id = 100"))
        assertEquals("User Exercise Variation", db.stringForQuery("SELECT name FROM exercises WHERE id = 101"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM exercise_categories WHERE exerciseId = 100"))

        db.close()
    }

    @Test
    fun unsupportedPreV5DatabaseFailsInsteadOfDestructivelyMigrating() {
        createUnsupportedVersion4Database()

        val database = Room.databaseBuilder(context, WorkoutDatabase::class.java, UNSUPPORTED_DB)
            .addMigrations(*WorkoutDatabaseMigrations.ALL)
            .build()

        try {
            database.openHelper.writableDatabase
            fail("Opening an unsupported pre-v5 database should require an explicit migration.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("A migration from 4 to 5 was required"))
        } finally {
            database.close()
        }
    }

    private fun SupportSQLiteDatabase.insertRepresentativeUserData() {
        execSQL(
            """
            INSERT INTO equipment (id, name, description, iconName, isCustom, isPortable)
            VALUES (100, 'User Dumbbell', 'owned pair', 'fitness_center', 1, 1)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO equipment_locations (id, name, description, iconName, isDefault, createdAt)
            VALUES (100, 'Garage', 'user gym', 'home', 1, 1000)
            """.trimIndent()
        )
        execSQL("INSERT INTO location_equipment (locationId, equipmentId) VALUES (100, 100)")
        execSQL(
            """
            INSERT INTO exercises (
                id, name, description, instructions, tips, difficulty, isUnilateral, isCompound,
                defaultSets, defaultReps, defaultRestSeconds, estimatedDurationSeconds,
                trainingPhasePresets, localMediaUris, externalMediaUrls, timesPerformed,
                lastPerformedAt, isFavorite, isArchived, personalNotes, createdAt, updatedAt
            ) VALUES (
                100, 'User Exercise', 'desc', 'inst', 'tips', 'ADVANCED', 1, 0,
                4, '6-8', 120, 240, '{}', '[]', '[]', 7,
                123456, 1, 0, 'note', 1000, 1001
            )
            """.trimIndent()
        )
        execSQL("INSERT INTO exercise_categories (exerciseId, category) VALUES (100, 'STRENGTH')")
        execSQL(
            """
            INSERT INTO exercise_equipment (exerciseId, equipmentId, isRequired, isAlternative)
            VALUES (100, 100, 1, 0)
            """.trimIndent()
        )
        execSQL("INSERT INTO exercise_muscles (exerciseId, muscleGroup, isPrimary) VALUES (100, 'CHEST', 1)")
        execSQL(
            """
            INSERT INTO workout_sessions (
                id, name, notes, locationId, targetDurationMinutes, targetCategories, status,
                plannedDate, scheduledTimeSlot, startedAt, completedAt, actualDurationMinutes,
                perceivedDifficulty, energyLevel, satisfactionRating, postSessionNotes, createdAt, updatedAt
            ) VALUES (
                100, 'User Session', 'notes', 100, 45, '["STRENGTH"]', 'COMPLETED',
                2000, 'MORNING', 2001, 3000, 44, 7, 8, 5, 'done', 1000, 3000
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO session_exercises (
                id, sessionId, exerciseId, orderIndex, section, plannedSets, plannedReps,
                plannedRestSeconds, prescriptionJson, isCompleted, isSkipped, notes
            ) VALUES (100, 100, 100, 0, 'MAIN', 4, '6-8', 120, '{"rounds":1}', 1, 0, 'good')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO set_logs (
                id, sessionExerciseId, setNumber, reps, weight, weightUnit, durationSeconds,
                distance, distanceUnit, rpe, restTakenSeconds, notes, completedAt
            ) VALUES (100, 100, 1, 6, 42.5, 'KG', NULL, NULL, 'METERS', 8, 120, 'solid', 2500)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO workout_plan_templates (
                id, name, description, notes, locationId, targetDurationMinutes, targetCategories,
                scheduledTimeSlot, sourcePhase, createdAt, updatedAt
            ) VALUES (100, 'User Plan', 'desc', 'notes', 100, 45, '["STRENGTH"]', 'MORNING', 'BALANCED', 1000, 1001)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO workout_plan_template_exercises (
                id, templateId, exerciseId, orderIndex, section, plannedSets, plannedReps,
                plannedRestSeconds, prescriptionJson, coachingNotes
            ) VALUES (100, 100, 100, 0, 'MAIN', 4, '6-8', 120, '{"rounds":1}', 'brace')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO pt_routines (
                id, name, description, therapistName, condition, frequency, timesPerDay,
                preferredTimeSlots, startDate, endDate, isMustDo, priority, isActive,
                isArchived, notes, precautions, createdAt, updatedAt
            ) VALUES (
                100, 'User PT', 'desc', 'PT', 'knee', 'THREE_TIMES_WEEK', 1,
                '["EVENING"]', 1000, NULL, 1, 1, 1, 0, 'notes', 'none', 1000, 1001
            )
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO pt_routine_exercises (
                routineId, exerciseId, orderIndex, prescribedSets, prescribedReps,
                prescribedHoldSeconds, prescribedRestSeconds, specialInstructions, progressionNotes
            ) VALUES (100, 100, 0, 2, '10', 30, 30, 'slow', 'add reps')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO pt_session_logs (
                id, routineId, startedAt, completedAt, timeSlot, exercisesCompleted,
                exercisesTotal, painLevelBefore, painLevelAfter, notes, symptomChanges
            ) VALUES (100, 100, 4000, 4500, 'EVENING', 1, 1, 4, 2, 'better', 'better')
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO user_goals (
                id, currentPhase, phaseStartDate, phaseEndDate, categoryWeights,
                minDaysBetweenSameCategory, maxDaysWithoutCategory, ensureWeeklyVariety,
                preferredSessionDurationMinutes, maxSessionsPerDay, preferredRestDays,
                autoProgressionEnabled, progressionThreshold, updatedAt
            ) VALUES (1, 'MOBILITY_REHAB', 1000, NULL, '{}', 2, 10, 0, 45, 1, '[0]', 1, 2, 1001)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO category_stats (
                category, totalSessions, totalExercises, totalMinutes, lastTrainedAt,
                daysSinceLastTrained, avgSessionsPerWeek, avgMinutesPerSession,
                currentStreak, longestStreak, updatedAt
            ) VALUES ('STRENGTH', 2, 3, 90, 3000, 1, 1.5, 45.0, 1, 2, 3000)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO ml_feedback_events (
                id, exerciseId, suggestedCategory, suggestedInSessionId, action, dayOfWeek,
                hourOfDay, timeSlot, daysSinceExercisePerformed, daysSinceCategoryTrained,
                exerciseTimesPerformed, difficultyLevel, sessionDurationMinutes, currentBalanceScore,
                perceivedDifficulty, satisfactionRating, createdAt
            ) VALUES (100, 100, 'STRENGTH', 100, 'COMPLETED', 1, 9, 'MORNING', 2, 3, 7, 4, 45, 80, 7, 5, 3000)
            """.trimIndent()
        )
        execSQL(
            """
            INSERT INTO ml_preference_scores (`key`, score, confidence, sampleCount, updatedAt)
            VALUES ('exercise:100', 0.75, 0.9, 4, 3000)
            """.trimIndent()
        )
    }

    private fun createUnsupportedVersion4Database() {
        val file = context.getDatabasePath(UNSUPPORTED_DB)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE legacy_data (id INTEGER PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO legacy_data (id, value) VALUES (1, 'keep me')")
            db.version = WorkoutDatabaseMigrations.FIRST_SHIPPED_VERSION - 1
        }
    }

    private fun SupportSQLiteDatabase.longForQuery(sql: String): Long =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun SupportSQLiteDatabase.stringForQuery(sql: String): String =
        query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private companion object {
        const val TEST_DB = "migration-v5-baseline.db"
        const val UNSUPPORTED_DB = "unsupported-v4.db"
    }
}
