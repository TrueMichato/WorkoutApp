package com.example.workoutapp.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.local.dao.EquipmentDeleteOutcome
import com.example.workoutapp.data.local.dao.EquipmentInsertOutcome
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.LocationEquipmentCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Connected tests for [EquipmentDao]'s atomic `@Transaction` helpers against a real in-memory
 * Room/SQLite database. A hand-written JVM fake DAO (as used in [EquipmentRepositoryTest]) can't
 * exercise real transaction/locking semantics, so these tests use an actual database and real
 * threads to confirm the duplicate-check-then-insert race the PR review flagged is truly closed
 * by [EquipmentDao.insertIfNameAvailable]'s `@Transaction`.
 */
@RunWith(AndroidJUnit4::class)
class EquipmentDaoTransactionTest {

    private lateinit var database: WorkoutDatabase
    private lateinit var dao: EquipmentDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).build()
        dao = database.equipmentDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertIfNameAvailable_concurrentCallsWithSameName_onlyOneInsertSucceeds() = runBlocking {
        val name = "Concurrent Kettlebell"

        val results = (1..8).map {
            async(Dispatchers.IO) {
                dao.insertIfNameAvailable(Equipment(name = name, isCustom = true))
            }
        }.awaitAll()

        val inserted = results.filterIsInstance<EquipmentInsertOutcome.Inserted>()
        val duplicates = results.filterIsInstance<EquipmentInsertOutcome.DuplicateName>()

        // If the check-then-insert weren't atomic, multiple concurrent callers could each see "no
        // duplicate yet" and all insert, leaving several rows with the same name.
        assertEquals(1, inserted.size)
        assertEquals(7, duplicates.size)
        assertEquals(1, dao.getCount())
    }

    @Test
    fun insertIfNameAvailable_concurrentCallsWithDistinctNames_allSucceed() = runBlocking {
        val results = (1..8).map { index ->
            async(Dispatchers.IO) {
                dao.insertIfNameAvailable(Equipment(name = "Item $index", isCustom = true))
            }
        }.awaitAll()

        assertTrue(results.all { it is EquipmentInsertOutcome.Inserted })
        assertEquals(8, dao.getCount())
    }

    @Test
    fun deleteCustomEquipmentIfUnreferenced_unreferencedRow_deletesAtomically() = runBlocking {
        val id = dao.insert(Equipment(name = "Yoga Mat", isCustom = true))

        val outcome = dao.deleteCustomEquipmentIfUnreferenced(id)

        assertEquals(EquipmentDeleteOutcome.Deleted, outcome)
        assertNull(dao.getById(id))
    }

    @Test
    fun deleteCustomEquipmentIfUnreferenced_referencedRow_refusesAndKeepsRow() = runBlocking {
        val id = dao.insert(Equipment(name = "Foam Roller", isCustom = true))
        dao.insertLocationEquipment(LocationEquipmentCrossRef(locationId = 1L, equipmentId = id))

        val outcome = dao.deleteCustomEquipmentIfUnreferenced(id)

        assertTrue(outcome is EquipmentDeleteOutcome.InUse)
        assertNotNull(dao.getById(id))
    }
}
