package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.model.Equipment
import com.example.workoutapp.data.model.EquipmentLocation
import com.example.workoutapp.data.model.LocationEquipmentCrossRef
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * In-memory fake mirroring the subset of [EquipmentDao] behavior needed here (see
 * [com.example.workoutapp.data.repository.UserGoalRepositoryTest] for the rationale: Room DAOs are
 * plain interfaces, so faking them directly keeps this a deterministic JVM unit test of the real
 * [EquipmentRepository] wiring without standing up SQLite/Robolectric).
 */
private class FakeEquipmentDao : EquipmentDao {
    private var nextId = 1L
    val equipmentFlow = MutableStateFlow<List<Equipment>>(emptyList())
    val locationsFlow = MutableStateFlow<List<EquipmentLocation>>(emptyList())

    /** Rows tests can seed directly to simulate an equipment item being referenced elsewhere. */
    val exerciseEquipmentRefs = mutableListOf<Pair<Long, Long>>() // exerciseId to equipmentId
    val locationEquipmentRefs = mutableListOf<LocationEquipmentCrossRef>()

    fun seed(vararg equipment: Equipment): List<Equipment> {
        val seeded = equipment.mapIndexed { index, e -> e.copy(id = (index + 1).toLong()) }
        nextId = seeded.size + 1L
        equipmentFlow.value = seeded
        return seeded
    }

    /** Test hooks so create/delete failure paths can be exercised without a real DB. */
    var insertException: Throwable? = null
    var deleteException: Throwable? = null

    override suspend fun insert(equipment: Equipment): Long {
        insertException?.let { throw it }
        val id = nextId++
        equipmentFlow.value = equipmentFlow.value + equipment.copy(id = id)
        return id
    }

    override suspend fun insertAll(equipment: List<Equipment>): List<Long> =
        equipment.map { insert(it) }

    override suspend fun update(equipment: Equipment) {
        equipmentFlow.value = equipmentFlow.value.map { if (it.id == equipment.id) equipment else it }
    }

    override suspend fun delete(equipment: Equipment) {
        deleteException?.let { throw it }
        equipmentFlow.value = equipmentFlow.value.filterNot { it.id == equipment.id }
    }

    override suspend fun getById(id: Long): Equipment? = equipmentFlow.value.firstOrNull { it.id == id }

    override fun getAll(): Flow<List<Equipment>> = equipmentFlow

    override fun getPortable(): Flow<List<Equipment>> =
        MutableStateFlow(equipmentFlow.value.filter { it.isPortable })

    override fun getCustom(): Flow<List<Equipment>> =
        MutableStateFlow(equipmentFlow.value.filter { it.isCustom })

    override fun search(query: String): Flow<List<Equipment>> =
        MutableStateFlow(equipmentFlow.value.filter { it.name.contains(query, ignoreCase = true) })

    override suspend fun getCount(): Int = equipmentFlow.value.size

    override suspend fun findByNameIgnoreCase(name: String): Equipment? =
        equipmentFlow.value.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    override suspend fun countExerciseReferences(equipmentId: Long): Int =
        exerciseEquipmentRefs.count { it.second == equipmentId }

    override suspend fun countLocationReferences(equipmentId: Long): Int =
        locationEquipmentRefs.count { it.equipmentId == equipmentId }

    override suspend fun insertLocation(location: EquipmentLocation): Long {
        val id = nextId++
        locationsFlow.value = locationsFlow.value + location.copy(id = id)
        return id
    }

    override suspend fun insertLocations(locations: List<EquipmentLocation>): List<Long> =
        locations.map { insertLocation(it) }

    override suspend fun updateLocation(location: EquipmentLocation) {
        locationsFlow.value = locationsFlow.value.map { if (it.id == location.id) location else it }
    }

    override suspend fun deleteLocation(location: EquipmentLocation) {
        locationsFlow.value = locationsFlow.value.filterNot { it.id == location.id }
    }

    override suspend fun getLocationById(id: Long): EquipmentLocation? =
        locationsFlow.value.firstOrNull { it.id == id }

    override fun getAllLocations(): Flow<List<EquipmentLocation>> = locationsFlow

    override suspend fun getDefaultLocation(): EquipmentLocation? =
        locationsFlow.value.firstOrNull { it.isDefault }

    override fun getDefaultLocationFlow(): Flow<EquipmentLocation?> =
        MutableStateFlow(locationsFlow.value.firstOrNull { it.isDefault })

    override suspend fun clearDefaultLocation() {
        locationsFlow.value = locationsFlow.value.map { it.copy(isDefault = false) }
    }

    override suspend fun setDefaultLocation(locationId: Long) {
        locationsFlow.value = locationsFlow.value.map { it.copy(isDefault = it.id == locationId) }
    }

    override suspend fun insertLocationEquipment(ref: LocationEquipmentCrossRef) {
        locationEquipmentRefs += ref
    }

    override suspend fun insertLocationEquipmentAll(refs: List<LocationEquipmentCrossRef>) {
        locationEquipmentRefs += refs
    }

    override suspend fun deleteLocationEquipment(ref: LocationEquipmentCrossRef) {
        locationEquipmentRefs.remove(ref)
    }

    override suspend fun clearEquipmentForLocation(locationId: Long) {
        locationEquipmentRefs.removeAll { it.locationId == locationId }
    }

    override suspend fun removeEquipmentFromLocation(locationId: Long, equipmentId: Long) {
        locationEquipmentRefs.removeAll { it.locationId == locationId && it.equipmentId == equipmentId }
    }

    override fun getEquipmentForLocation(locationId: Long): Flow<List<Equipment>> {
        val ids = locationEquipmentRefs.filter { it.locationId == locationId }.map { it.equipmentId }
        return MutableStateFlow(equipmentFlow.value.filter { it.id in ids })
    }

    override suspend fun getEquipmentIdsForLocation(locationId: Long): List<Long> =
        locationEquipmentRefs.filter { it.locationId == locationId }.map { it.equipmentId }

    override suspend fun getEquipmentCountForLocation(locationId: Long): Int =
        locationEquipmentRefs.count { it.locationId == locationId }

    override suspend fun isEquipmentAtLocation(locationId: Long, equipmentId: Long): Boolean =
        locationEquipmentRefs.any { it.locationId == locationId && it.equipmentId == equipmentId }
}

/**
 * Coverage for [EquipmentRepository]'s add-equipment robustness: this is the shared validation
 * layer used by the Equipment Management dialog, the inline "Add Equipment" flow in
 * Add/Edit Exercise, and CSV import equipment auto-creation, so bugs here affect every add-equipment
 * surface reported as crashing/not robust.
 */
class EquipmentRepositoryTest {

    private fun newRepository(vararg seed: Equipment): Pair<EquipmentRepository, FakeEquipmentDao> {
        val dao = FakeEquipmentDao()
        dao.seed(*seed)
        return EquipmentRepository(dao) to dao
    }

    @Test
    fun createEquipment_blankName_isRejectedWithoutTouchingDatabase() = runBlocking {
        val (repository, dao) = newRepository()

        val result = repository.createEquipment("   ", "desc", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        assertEquals(
            EquipmentValidationError.BlankName.message,
            (result as EquipmentSaveResult.Failure).error.message
        )
        assertTrue(dao.equipmentFlow.value.isEmpty())
    }

    @Test
    fun createEquipment_nameOverMaxLength_isRejected() = runBlocking {
        val (repository, _) = newRepository()
        val tooLong = "x".repeat(EquipmentRepository.MAX_NAME_LENGTH + 1)

        val result = repository.createEquipment(tooLong, "", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        assertTrue((result as EquipmentSaveResult.Failure).error is EquipmentValidationError.NameTooLong)
    }

    @Test
    fun createEquipment_duplicateOfBuiltIn_caseAndWhitespaceInsensitive_isRejected() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Barbell", isCustom = false))

        val result = repository.createEquipment("  barbell  ", "", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        val error = (result as EquipmentSaveResult.Failure).error
        assertTrue(error is EquipmentValidationError.DuplicateName)
        assertEquals("Barbell", (error as EquipmentValidationError.DuplicateName).existingName)
        // Duplicate rejection must never overwrite/shadow the existing built-in row.
        assertEquals(1, dao.equipmentFlow.value.size)
        assertFalse(dao.equipmentFlow.value.single().isCustom)
    }

    @Test
    fun createEquipment_duplicateOfExistingCustomEquipment_isRejected() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "My Weird Bar", isCustom = true))

        val result = repository.createEquipment("My Weird Bar", "", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun createEquipment_validName_trimsFieldsAndPersistsAsCustom() = runBlocking {
        val (repository, dao) = newRepository()

        val result = repository.createEquipment("  Slam Ball  ", "  Heavy ball  ", true)

        assertTrue(result is EquipmentSaveResult.Success)
        val created = (result as EquipmentSaveResult.Success).equipment
        assertEquals("Slam Ball", created.name)
        assertEquals("Heavy ball", created.description)
        assertTrue(created.isCustom)
        assertTrue(created.isPortable)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun createEquipment_internalWhitespaceIsCollapsedOnStorage() = runBlocking {
        val (repository, _) = newRepository()

        val result = repository.createEquipment("Foam   Roller", "", false)

        assertTrue(result is EquipmentSaveResult.Success)
        assertEquals("Foam Roller", (result as EquipmentSaveResult.Success).equipment.name)
    }

    @Test
    fun createEquipment_duplicateDifferingOnlyByInternalWhitespace_isRejected() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Foam Roller", isCustom = true))

        val result = repository.createEquipment("Foam   Roller", "", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        assertTrue((result as EquipmentSaveResult.Failure).error is EquipmentValidationError.DuplicateName)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun createEquipment_daoInsertThrows_returnsPersistFailedInsteadOfCrashing() = runBlocking {
        val (repository, dao) = newRepository()
        dao.insertException = RuntimeException("simulated DB failure")

        val result = repository.createEquipment("New Thing", "", false)

        assertTrue(result is EquipmentSaveResult.Failure)
        assertTrue((result as EquipmentSaveResult.Failure).error is EquipmentValidationError.PersistFailed)
        assertTrue(dao.equipmentFlow.value.isEmpty())
    }

    @Test(expected = CancellationException::class)
    fun createEquipment_daoThrowsCancellationException_isRethrownNotSwallowed() = runBlocking {
        val (repository, dao) = newRepository()
        dao.insertException = CancellationException("cancelled")

        repository.createEquipment("New Thing", "", false)
        Unit
    }

    @Test
    fun deleteEquipment_builtIn_isRefused() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Barbell", isCustom = false))
        val builtIn = dao.equipmentFlow.value.single()

        val result = repository.deleteEquipment(builtIn)

        assertTrue(result is EquipmentDeletionResult.Failure)
        assertTrue((result as EquipmentDeletionResult.Failure).error is EquipmentDeletionError.NotCustom)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun deleteEquipment_alreadyDeletedRecord_reportsNotFoundInsteadOfSilentNoop() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Custom Bar", isCustom = true))
        val custom = dao.equipmentFlow.value.single()
        dao.equipmentFlow.value = emptyList() // simulate concurrent deletion/disappearance

        val result = repository.deleteEquipment(custom)

        assertTrue(result is EquipmentDeletionResult.Failure)
        assertTrue((result as EquipmentDeletionResult.Failure).error is EquipmentDeletionError.NotFound)
    }

    @Test
    fun deleteEquipment_referencedByExercise_isRefusedAndNotRemoved() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Kettlebell", isCustom = true))
        val custom = dao.equipmentFlow.value.single()
        dao.exerciseEquipmentRefs += (99L to custom.id)

        val result = repository.deleteEquipment(custom)

        assertTrue(result is EquipmentDeletionResult.Failure)
        val error = (result as EquipmentDeletionResult.Failure).error
        assertTrue(error is EquipmentDeletionError.InUse)
        assertEquals(1, (error as EquipmentDeletionError.InUse).exerciseCount)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun deleteEquipment_referencedByLocation_isRefusedAndNotRemoved() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Yoga Mat", isCustom = true))
        val custom = dao.equipmentFlow.value.single()
        dao.locationEquipmentRefs += LocationEquipmentCrossRef(locationId = 7L, equipmentId = custom.id)

        val result = repository.deleteEquipment(custom)

        assertTrue(result is EquipmentDeletionResult.Failure)
        val error = (result as EquipmentDeletionResult.Failure).error
        assertTrue(error is EquipmentDeletionError.InUse)
        assertEquals(1, (error as EquipmentDeletionError.InUse).locationCount)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test
    fun deleteEquipment_unreferencedCustomEquipment_succeeds() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Kettlebell", isCustom = true))
        val custom = dao.equipmentFlow.value.single()

        val result = repository.deleteEquipment(custom)

        assertEquals(EquipmentDeletionResult.Success, result)
        assertTrue(dao.equipmentFlow.value.isEmpty())
    }

    @Test
    fun deleteEquipment_daoDeleteThrows_returnsUnexpectedErrorInsteadOfCrashing() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Kettlebell", isCustom = true))
        val custom = dao.equipmentFlow.value.single()
        dao.deleteException = RuntimeException("simulated DB failure")

        val result = repository.deleteEquipment(custom)

        assertTrue(result is EquipmentDeletionResult.Failure)
        assertTrue((result as EquipmentDeletionResult.Failure).error is EquipmentDeletionError.UnexpectedError)
        assertEquals(1, dao.equipmentFlow.value.size)
    }

    @Test(expected = CancellationException::class)
    fun deleteEquipment_daoThrowsCancellationException_isRethrownNotSwallowed() = runBlocking {
        val (repository, dao) = newRepository(Equipment(name = "Kettlebell", isCustom = true))
        val custom = dao.equipmentFlow.value.single()
        dao.deleteException = CancellationException("cancelled")

        repository.deleteEquipment(custom)
        Unit
    }

    @Test
    fun getEquipmentByNameIgnoreCase_findsRegardlessOfCasing() = runBlocking {
        val (repository, _) = newRepository(Equipment(name = "Resistance Bands", isCustom = false))

        val found = repository.getEquipmentByNameIgnoreCase("resistance bands")

        assertEquals("Resistance Bands", found?.name)
    }

    @Test
    fun getEquipmentByNameIgnoreCase_missing_returnsNull() = runBlocking {
        val (repository, _) = newRepository()

        assertNull(repository.getEquipmentByNameIgnoreCase("Nothing Here"))
    }
}
