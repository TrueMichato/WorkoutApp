package com.example.workoutapp.data.local

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.workoutapp.data.model.CategoryStats
import com.example.workoutapp.data.model.DefaultEquipment
import com.example.workoutapp.data.model.DefaultLocations
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.UserGoal
import com.example.workoutapp.data.model.WorkoutCategory

object WorkoutDatabaseSeedCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        WorkoutDatabaseSeeder.seedDefaults(db)
    }
}

object WorkoutDatabaseSeeder {
    private const val HOME_GYM_ID = 1L
    private const val COMMERCIAL_GYM_ID = 2L
    private const val TRAVEL_ID = 3L
    private const val OUTDOOR_ID = 4L

    private val homeEquipmentNames = setOf(
        "Dumbbells",
        "Resistance Bands",
        "Pull-up Bar",
        "Yoga Mat",
        "Foam Roller",
        "Kettlebell",
        "Adjustable Bench",
        "No Equipment"
    )

    private val outdoorEquipmentNames = setOf(
        "Pull-up Bar",
        "Resistance Bands",
        "Jump Rope",
        "Yoga Mat",
        "No Equipment"
    )

    fun seedDefaults(
        db: SupportSQLiteDatabase,
        now: Long = System.currentTimeMillis()
    ) {
        db.beginTransaction()
        try {
            val equipmentIds = seedEquipment(db)
            seedLocations(db, now)
            seedLocationEquipment(db, equipmentIds)
            seedUserGoal(db, now)
            seedCategoryStats(db, now)
            val exerciseIds = seedSampleExercises(db, equipmentIds, now)
            seedExerciseVariations(db, exerciseIds)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun seedEquipment(db: SupportSQLiteDatabase): Map<String, Long> {
        DefaultEquipment.all.forEachIndexed { index, equipment ->
            db.insertIgnore("equipment", equipment.toValues(id = index + 1L))
        }
        return DefaultEquipment.all.mapIndexed { index, equipment ->
            equipment.name to index + 1L
        }.toMap()
    }

    private fun seedLocations(db: SupportSQLiteDatabase, now: Long) {
        DefaultLocations.all.forEachIndexed { index, location ->
            db.insertIgnore(
                "equipment_locations",
                location.toValues(
                    id = index + 1L,
                    isDefault = location.name == DefaultLocations.COMMERCIAL_GYM.name,
                    now = now
                )
            )
        }
    }

    private fun seedLocationEquipment(
        db: SupportSQLiteDatabase,
        equipmentIds: Map<String, Long>
    ) {
        DefaultEquipment.all.forEach { equipment ->
            db.insertLocationEquipment(COMMERCIAL_GYM_ID, equipmentIds.idFor(equipment.name))
        }
        homeEquipmentNames.forEach { equipmentName ->
            db.insertLocationEquipment(HOME_GYM_ID, equipmentIds.idFor(equipmentName))
        }
        DefaultEquipment.all.filter { it.isPortable }.forEach { equipment ->
            db.insertLocationEquipment(TRAVEL_ID, equipmentIds.idFor(equipment.name))
        }
        outdoorEquipmentNames.forEach { equipmentName ->
            db.insertLocationEquipment(OUTDOOR_ID, equipmentIds.idFor(equipmentName))
        }
    }

    private fun seedUserGoal(db: SupportSQLiteDatabase, now: Long) {
        val goal = UserGoal(
            phaseStartDate = now,
            updatedAt = now
        )
        db.insertIgnore(
            "user_goals",
            valuesOf {
                put("id", goal.id)
                put("currentPhase", goal.currentPhase.name)
                put("phaseStartDate", goal.phaseStartDate)
                putNull("phaseEndDate")
                put("categoryWeights", goal.categoryWeights)
                put("minDaysBetweenSameCategory", goal.minDaysBetweenSameCategory)
                put("maxDaysWithoutCategory", goal.maxDaysWithoutCategory)
                putBoolean("ensureWeeklyVariety", goal.ensureWeeklyVariety)
                put("preferredSessionDurationMinutes", goal.preferredSessionDurationMinutes)
                put("maxSessionsPerDay", goal.maxSessionsPerDay)
                put("preferredRestDays", goal.preferredRestDays)
                putBoolean("autoProgressionEnabled", goal.autoProgressionEnabled)
                put("progressionThreshold", goal.progressionThreshold)
                put("updatedAt", goal.updatedAt)
            }
        )
    }

    private fun seedCategoryStats(db: SupportSQLiteDatabase, now: Long) {
        WorkoutCategory.rotationCategories().forEach { category ->
            db.insertIgnore("category_stats", CategoryStats(category = category, updatedAt = now).toValues())
        }
    }

    private fun seedSampleExercises(
        db: SupportSQLiteDatabase,
        equipmentIds: Map<String, Long>,
        now: Long
    ): Map<String, Long> {
        SampleExercises.all.forEachIndexed { index, sample ->
            val exerciseId = index + 1L
            val exercise = Exercise(
                id = exerciseId,
                name = sample.name,
                description = sample.description,
                instructions = sample.instructions,
                tips = sample.tips,
                difficulty = sample.difficulty,
                isUnilateral = sample.isUnilateral,
                isCompound = sample.isCompound,
                defaultSets = sample.defaultSets,
                defaultReps = sample.defaultReps,
                defaultRestSeconds = sample.defaultRestSeconds,
                createdAt = now,
                updatedAt = now
            )
            db.insertIgnore("exercises", exercise.toValues())
            sample.categories.forEach { category ->
                db.insertExerciseCategory(exerciseId, category)
            }
            sample.equipmentNames.forEach { equipmentName ->
                db.insertExerciseEquipment(exerciseId, equipmentIds.idFor(equipmentName))
            }
            sample.primaryMuscles.forEach { muscleGroup ->
                db.insertExerciseMuscle(exerciseId, muscleGroup.name, isPrimary = true)
            }
            sample.secondaryMuscles.forEach { muscleGroup ->
                db.insertExerciseMuscle(exerciseId, muscleGroup.name, isPrimary = false)
            }
        }
        return SampleExercises.all.mapIndexed { index, sample -> sample.name to index + 1L }.toMap()
    }

    /**
     * Links the seeded Push-up variations (Tiger/Pike/Plyometric/Slow) to the seeded Push-up main
     * exercise, only on brand-new installs (this runs inside the same onCreate seeding
     * transaction as [seedSampleExercises], never on an upgrade/migration). Uses
     * [SupportSQLiteDatabase.insert] with CONFLICT_IGNORE against the `exercise_variations`
     * primary key (`variationExerciseId`), so re-running this against an already-seeded database
     * is a no-op rather than a duplicate/crash.
     */
    private fun seedExerciseVariations(db: SupportSQLiteDatabase, exerciseIds: Map<String, Long>) {
        val mainId = exerciseIds[SampleExercises.PUSH_UP_MAIN_NAME] ?: return
        SampleExercises.PUSH_UP_VARIATIONS.forEach { (variationName, focus) ->
            val variationId = exerciseIds[variationName] ?: return@forEach
            db.insertIgnore(
                "exercise_variations",
                valuesOf {
                    put("variationExerciseId", variationId)
                    put("parentExerciseId", mainId)
                    put("focus", focus)
                }
            )
        }
    }

    private fun Equipment.toValues(id: Long): ContentValues = valuesOf {
        put("id", id)
        put("name", name)
        put("description", description)
        put("iconName", iconName)
        putBoolean("isCustom", isCustom)
        putBoolean("isPortable", isPortable)
    }

    private fun EquipmentLocation.toValues(
        id: Long,
        isDefault: Boolean,
        now: Long
    ): ContentValues = valuesOf {
        put("id", id)
        put("name", name)
        put("description", description)
        put("iconName", iconName)
        putBoolean("isDefault", isDefault)
        put("createdAt", now)
    }

    private fun CategoryStats.toValues(): ContentValues = valuesOf {
        put("category", category.name)
        put("totalSessions", totalSessions)
        put("totalExercises", totalExercises)
        put("totalMinutes", totalMinutes)
        putNull("lastTrainedAt")
        put("daysSinceLastTrained", daysSinceLastTrained)
        put("avgSessionsPerWeek", avgSessionsPerWeek)
        put("avgMinutesPerSession", avgMinutesPerSession)
        put("currentStreak", currentStreak)
        put("longestStreak", longestStreak)
        put("updatedAt", updatedAt)
    }

    private fun Exercise.toValues(): ContentValues = valuesOf {
        put("id", id)
        put("name", name)
        put("description", description)
        put("instructions", instructions)
        put("tips", tips)
        put("difficulty", difficulty.name)
        putBoolean("isUnilateral", isUnilateral)
        putBoolean("isCompound", isCompound)
        put("defaultSets", defaultSets)
        put("defaultReps", defaultReps)
        put("defaultRestSeconds", defaultRestSeconds)
        put("estimatedDurationSeconds", estimatedDurationSeconds)
        put("trainingPhasePresets", trainingPhasePresets)
        put("localMediaUris", localMediaUris)
        put("externalMediaUrls", externalMediaUrls)
        put("timesPerformed", timesPerformed)
        putNull("lastPerformedAt")
        putBoolean("isFavorite", isFavorite)
        putBoolean("isArchived", isArchived)
        put("personalNotes", personalNotes)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun SupportSQLiteDatabase.insertLocationEquipment(
        locationId: Long,
        equipmentId: Long
    ) {
        insertIgnore(
            "location_equipment",
            valuesOf {
                put("locationId", locationId)
                put("equipmentId", equipmentId)
            }
        )
    }

    private fun SupportSQLiteDatabase.insertExerciseCategory(
        exerciseId: Long,
        category: WorkoutCategory
    ) {
        insertIgnore(
            "exercise_categories",
            valuesOf {
                put("exerciseId", exerciseId)
                put("category", category.name)
            }
        )
    }

    private fun SupportSQLiteDatabase.insertExerciseEquipment(
        exerciseId: Long,
        equipmentId: Long
    ) {
        insertIgnore(
            "exercise_equipment",
            valuesOf {
                put("exerciseId", exerciseId)
                put("equipmentId", equipmentId)
                putBoolean("isRequired", true)
                putBoolean("isAlternative", false)
            }
        )
    }

    private fun SupportSQLiteDatabase.insertExerciseMuscle(
        exerciseId: Long,
        muscleGroup: String,
        isPrimary: Boolean
    ) {
        insertIgnore(
            "exercise_muscles",
            valuesOf {
                put("exerciseId", exerciseId)
                put("muscleGroup", muscleGroup)
                putBoolean("isPrimary", isPrimary)
            }
        )
    }

    private fun SupportSQLiteDatabase.insertIgnore(
        table: String,
        values: ContentValues
    ) {
        insert(table, SQLiteDatabase.CONFLICT_IGNORE, values)
    }

    private fun Map<String, Long>.idFor(equipmentName: String): Long =
        this[equipmentName] ?: error("Default equipment '$equipmentName' was not seeded.")

    private fun valuesOf(block: ContentValues.() -> Unit): ContentValues =
        ContentValues().apply(block)

    private fun ContentValues.putBoolean(key: String, value: Boolean) {
        put(key, if (value) 1 else 0)
    }
}
