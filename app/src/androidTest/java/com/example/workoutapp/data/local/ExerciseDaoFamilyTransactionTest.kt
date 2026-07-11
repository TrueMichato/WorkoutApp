package com.example.workoutapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workoutapp.data.local.dao.ExerciseDao
import com.example.workoutapp.data.local.dao.ExerciseFamilyMutation
import com.example.workoutapp.data.local.dao.ExerciseSaveOutcome
import com.example.workoutapp.data.model.Difficulty
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.WorkoutCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected tests for [ExerciseDao]'s atomic exercise+relations+family `@Transaction` helpers
 * against a real in-memory Room/SQLite database, proving that a rejected family mutation never
 * leaves a partially-written exercise row, relation set, or family link behind - and that
 * reparenting never silently detaches an exercise if the new link fails to validate.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoFamilyTransactionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        dao = database.exerciseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun createStandalone(name: String): Long = dao.insertWithRelations(
        exercise = Exercise(name = name, difficulty = Difficulty.INTERMEDIATE),
        categories = listOf(WorkoutCategory.STRENGTH),
        equipmentIds = emptyList(),
        primaryMuscles = emptyList(),
        secondaryMuscles = emptyList()
    )

    @Test
    fun insertWithRelationsAndFamily_invalidParent_persistsNothingAtAll() = runBlocking {
        val outcome = dao.insertWithRelationsAndFamily(
            exercise = Exercise(name = "New Variation", difficulty = Difficulty.ADVANCED),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            familyMutation = ExerciseFamilyMutation.LinkTo(parentExerciseId = 999_999L, focus = "Focus")
        )

        assertTrue(outcome is ExerciseSaveOutcome.ParentNotFound)
        // Nothing at all was persisted - not the exercise row, not its categories.
        assertEquals(0L, dao.getAllSync().size.toLong())
        assertTrue(dao.getExerciseIdsForCategory(WorkoutCategory.STRENGTH).isEmpty())
    }

    @Test
    fun updateWithRelationsAndFamily_reparentToInvalidParent_leavesOriginalExerciseAndLinkUntouched() = runBlocking {
        val mainId = createStandalone("Push-up")
        val variationId = createStandalone("Tiger Push-up")
        val otherMainId = createStandalone("Pull-up")
        val nestedVariationId = createStandalone("Wide Pull-up")

        // otherMainId is made a variation of nestedVariationId, so it becomes an invalid
        // reparent target (a variation can't also become a main exercise for something else).
        assertEquals(
            ExerciseSaveOutcome.Success(variationId),
            dao.linkVariation(mainId, variationId, "Triceps emphasis")
        )
        assertEquals(
            ExerciseSaveOutcome.Success(otherMainId),
            dao.linkVariation(nestedVariationId, otherMainId, "")
        )

        val originalVariation = dao.getById(variationId)!!
        val originalCategories = dao.getCategoriesForExercise(variationId)

        // Attempt to reparent the Tiger Push-up variation onto "otherMainId" - which is itself
        // already a variation of nestedVariationId, so this must be rejected.
        val outcome = dao.updateWithRelationsAndFamily(
            exercise = originalVariation.copy(name = "Renamed During Failed Reparent", description = "should not stick"),
            categories = listOf(WorkoutCategory.MOBILITY),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            familyMutation = ExerciseFamilyMutation.LinkTo(parentExerciseId = otherMainId, focus = "new focus")
        )

        assertTrue(outcome is ExerciseSaveOutcome.ParentIsAlreadyVariation)

        // The exercise row itself was NOT renamed/edited - the whole transaction rolled back.
        val reloaded = dao.getById(variationId)!!
        assertEquals("Tiger Push-up", reloaded.name)
        assertEquals(originalVariation.description, reloaded.description)
        // Its relations were NOT replaced either.
        assertEquals(originalCategories, dao.getCategoriesForExercise(variationId))
        // Its original family link to mainId is still intact - reparenting never silently
        // detached it before validating the new parent.
        assertEquals(mainId, dao.getVariationLink(variationId)?.parentExerciseId)
    }

    @Test
    fun updateWithRelationsAndFamily_selfLink_rejectsAndLeavesEverythingUnchanged() = runBlocking {
        val exerciseId = createStandalone("Solo Exercise")
        val original = dao.getById(exerciseId)!!

        val outcome = dao.updateWithRelationsAndFamily(
            exercise = original.copy(name = "Should Not Persist"),
            categories = listOf(WorkoutCategory.ENDURANCE),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            familyMutation = ExerciseFamilyMutation.LinkTo(parentExerciseId = exerciseId, focus = "")
        )

        assertEquals(ExerciseSaveOutcome.SelfLink, outcome)
        assertEquals("Solo Exercise", dao.getById(exerciseId)!!.name)
        assertNull(dao.getVariationLink(exerciseId))
    }

    @Test
    fun updateWithRelationsAndFamily_exerciseAlreadyHasVariations_rejectsBecomingAVariation() = runBlocking {
        val mainId = createStandalone("Push-up")
        val variationId = createStandalone("Tiger Push-up")
        val otherStandaloneId = createStandalone("Bench Press")
        dao.linkVariation(mainId, variationId, "Triceps emphasis")

        val original = dao.getById(mainId)!!
        val outcome = dao.updateWithRelationsAndFamily(
            exercise = original.copy(description = "attempted edit"),
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            familyMutation = ExerciseFamilyMutation.LinkTo(parentExerciseId = otherStandaloneId, focus = "")
        )

        assertTrue(outcome is ExerciseSaveOutcome.ExerciseHasOwnVariations)
        assertEquals("", dao.getById(mainId)!!.description)
        // Its variation link (as a parent) is unaffected.
        assertEquals(mainId, dao.getVariationLink(variationId)?.parentExerciseId)
    }

    @Test
    fun updateWithRelationsAndFamily_validReparent_atomicallyDetachesOldAndLinksNew() = runBlocking {
        val firstParentId = createStandalone("Push-up")
        val secondParentId = createStandalone("Pike Push-up Family")
        val variationId = createStandalone("Diamond Push-up")
        dao.linkVariation(firstParentId, variationId, "Original focus")

        val original = dao.getById(variationId)!!
        val outcome = dao.updateWithRelationsAndFamily(
            exercise = original,
            categories = listOf(WorkoutCategory.STRENGTH),
            equipmentIds = emptyList(),
            primaryMuscles = emptyList(),
            secondaryMuscles = emptyList(),
            familyMutation = ExerciseFamilyMutation.LinkTo(parentExerciseId = secondParentId, focus = "New focus")
        )

        assertTrue(outcome is ExerciseSaveOutcome.Success)
        assertEquals(secondParentId, dao.getVariationLink(variationId)?.parentExerciseId)
        assertEquals("New focus", dao.getVariationLink(variationId)?.focus)
        assertEquals(0, dao.countVariationsForParent(firstParentId))
    }

    @Test
    fun linkVariation_concurrentReparentAttempts_onlyOneSucceedsAtomically() = runBlocking {
        val parentA = createStandalone("Parent A")
        val parentB = createStandalone("Parent B")
        val variationId = createStandalone("Contested Variation")

        val results = listOf(parentA, parentB).map { parentId ->
            async(Dispatchers.IO) {
                dao.linkVariation(parentId, variationId, "")
            }
        }.awaitAll()

        val successes = results.filterIsInstance<ExerciseSaveOutcome.Success>()
        val alreadyLinked = results.filterIsInstance<ExerciseSaveOutcome.AlreadyLinkedElsewhere>()
        // Only one of the two concurrent link attempts can win the race for this variation's
        // single exercise_variations row; the other must see it as already linked instead of
        // throwing a raw constraint-violation exception.
        assertEquals(1, successes.size)
        assertEquals(1, alreadyLinked.size)
        assertNotNull(dao.getVariationLink(variationId))
    }
}
