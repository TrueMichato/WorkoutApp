package com.example.workoutapp.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workoutapp.data.model.DefaultEquipment
import com.example.workoutapp.data.model.DefaultLocations
import com.example.workoutapp.data.model.WorkoutCategory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseSeederTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: WorkoutDatabase? = null

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DB)
    }

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(TEST_DB)
    }

    @Test
    fun firstRunSeedCompletesBeforeDatabaseOpenReturns() {
        val db = openSeededDatabase().openHelper.writableDatabase

        assertEquals(DefaultEquipment.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM equipment"))
        assertEquals(DefaultLocations.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM equipment_locations"))
        assertEquals(SampleExercises.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM exercises"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM user_goals WHERE id = 1"))
        assertEquals(
            WorkoutCategory.rotationCategories().size.toLong(),
            db.longForQuery("SELECT COUNT(*) FROM category_stats")
        )
    }

    @Test
    fun seedDefaultsIsIdempotent() {
        val db = openSeededDatabase().openHelper.writableDatabase

        WorkoutDatabaseSeeder.seedDefaults(db, now = 1234L)
        WorkoutDatabaseSeeder.seedDefaults(db, now = 5678L)

        assertEquals(DefaultEquipment.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM equipment"))
        assertEquals(DefaultLocations.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM equipment_locations"))
        assertEquals(SampleExercises.all.size.toLong(), db.longForQuery("SELECT COUNT(*) FROM exercises"))
        assertEquals(1L, db.longForQuery("SELECT COUNT(*) FROM user_goals WHERE id = 1"))
        assertEquals(
            WorkoutCategory.rotationCategories().size.toLong(),
            db.longForQuery("SELECT COUNT(*) FROM category_stats")
        )
    }

    @Test
    fun seedDefaultsCreatesImportantDefaultRelationships() {
        val db = openSeededDatabase().openHelper.writableDatabase

        assertEquals(
            "Commercial Gym",
            db.stringForQuery("SELECT name FROM equipment_locations WHERE isDefault = 1")
        )
        assertEquals(
            DefaultEquipment.all.size.toLong(),
            db.longForQuery(
                """
                SELECT COUNT(*) FROM location_equipment le
                INNER JOIN equipment_locations l ON l.id = le.locationId
                WHERE l.name = 'Commercial Gym'
                """.trimIndent()
            )
        )
        assertEquals(
            1L,
            db.longForQuery(
                """
                SELECT COUNT(*) FROM exercise_equipment ee
                INNER JOIN exercises ex ON ex.id = ee.exerciseId
                INNER JOIN equipment eq ON eq.id = ee.equipmentId
                WHERE ex.name = 'Bench Press' AND eq.name = 'Barbell'
                """.trimIndent()
            )
        )
        assertTrue(
            db.longForQuery(
                """
                SELECT COUNT(*) FROM exercise_muscles em
                INNER JOIN exercises ex ON ex.id = em.exerciseId
                WHERE ex.name = 'Bench Press' AND em.muscleGroup = 'CHEST' AND em.isPrimary = 1
                """.trimIndent()
            ) > 0
        )
    }

    private fun openSeededDatabase(): WorkoutDatabase {
        val opened = Room.databaseBuilder(context, WorkoutDatabase::class.java, TEST_DB)
            .addMigrations(*WorkoutDatabaseMigrations.ALL)
            .addCallback(WorkoutDatabaseSeedCallback)
            .allowMainThreadQueries()
            .build()
        database = opened
        return opened
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
        const val TEST_DB = "seed-defaults.db"
    }
}
