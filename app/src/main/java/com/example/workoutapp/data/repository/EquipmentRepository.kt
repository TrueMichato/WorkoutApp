package com.example.workoutapp.data.repository

import com.example.workoutapp.data.local.dao.EquipmentDao
import com.example.workoutapp.data.local.dao.EquipmentDeleteOutcome
import com.example.workoutapp.data.local.dao.EquipmentInsertOutcome
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EquipmentRepository @Inject constructor(
    private val equipmentDao: EquipmentDao
) {
    companion object {
        const val MAX_NAME_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 200
    }

    // Equipment
    fun getAllEquipment(): Flow<List<Equipment>> = equipmentDao.getAll()

    fun getPortableEquipment(): Flow<List<Equipment>> = equipmentDao.getPortable()

    fun getCustomEquipment(): Flow<List<Equipment>> = equipmentDao.getCustom()

    fun searchEquipment(query: String): Flow<List<Equipment>> = equipmentDao.search(query)

    suspend fun getEquipmentById(id: Long): Equipment? = equipmentDao.getById(id)

    suspend fun getEquipmentByNameIgnoreCase(name: String): Equipment? =
        equipmentDao.findByNameIgnoreCase(normalizeName(name))

    /**
     * Collapses leading/trailing AND repeated internal whitespace to a single space, so
     * "  Foam   Roller " and "Foam Roller" are treated (and stored) identically - true
     * whitespace-insensitive matching, not just an edge trim.
     */
    private fun normalizeName(raw: String): String = raw.trim().replace(Regex("\\s+"), " ")

    /**
     * Validates and creates a custom equipment row. Rejects blank/too-long names and
     * case/whitespace-insensitive duplicates of any existing equipment (built-in or custom) so
     * users can't shadow a built-in item or create duplicate custom rows. The duplicate check and
     * insert happen atomically in a single DB transaction ([EquipmentDao.insertIfNameAvailable])
     * so two concurrent callers can never both pass the check and insert duplicates. Any
     * unexpected persistence exception is caught and surfaced as a typed failure rather than
     * escaping as an unhandled coroutine exception; [CancellationException] is always rethrown so
     * cancellation semantics are preserved.
     */
    suspend fun createEquipment(
        name: String,
        description: String = "",
        isPortable: Boolean = false
    ): EquipmentSaveResult {
        val normalizedName = normalizeName(name)
        if (normalizedName.isBlank()) {
            return EquipmentSaveResult.Failure(EquipmentValidationError.BlankName)
        }
        if (normalizedName.length > MAX_NAME_LENGTH) {
            return EquipmentSaveResult.Failure(EquipmentValidationError.NameTooLong(MAX_NAME_LENGTH))
        }
        val normalizedDescription = description.trim().take(MAX_DESCRIPTION_LENGTH)
        return try {
            when (
                val outcome = equipmentDao.insertIfNameAvailable(
                    Equipment(
                        name = normalizedName,
                        description = normalizedDescription,
                        isPortable = isPortable,
                        isCustom = true
                    )
                )
            ) {
                is EquipmentInsertOutcome.Inserted -> EquipmentSaveResult.Success(outcome.equipment)
                is EquipmentInsertOutcome.DuplicateName ->
                    EquipmentSaveResult.Failure(EquipmentValidationError.DuplicateName(outcome.existingName))
                is EquipmentInsertOutcome.PersistFailed ->
                    EquipmentSaveResult.Failure(EquipmentValidationError.PersistFailed)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EquipmentSaveResult.Failure(EquipmentValidationError.PersistFailed)
        }
    }

    suspend fun updateEquipment(equipment: Equipment) = equipmentDao.update(equipment)

    /**
     * Deletes custom equipment only after atomically re-confirming (in a single DB transaction,
     * [EquipmentDao.deleteCustomEquipmentIfUnreferenced]) that it still exists, is still custom,
     * and is not referenced by any exercise or location, so removal never silently strips
     * equipment out of a user's saved data and a reference can't sneak in between the check and
     * the delete. Any unexpected persistence exception is caught and surfaced as a typed failure;
     * [CancellationException] is always rethrown.
     */
    suspend fun deleteEquipment(equipment: Equipment): EquipmentDeletionResult {
        return try {
            when (val outcome = equipmentDao.deleteCustomEquipmentIfUnreferenced(equipment.id)) {
                EquipmentDeleteOutcome.Deleted -> EquipmentDeletionResult.Success
                EquipmentDeleteOutcome.NotFound ->
                    EquipmentDeletionResult.Failure(EquipmentDeletionError.NotFound)
                EquipmentDeleteOutcome.NotCustom ->
                    EquipmentDeletionResult.Failure(EquipmentDeletionError.NotCustom)
                is EquipmentDeleteOutcome.InUse -> EquipmentDeletionResult.Failure(
                    EquipmentDeletionError.InUse(outcome.exerciseCount, outcome.locationCount)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EquipmentDeletionResult.Failure(EquipmentDeletionError.UnexpectedError)
        }
    }

    // Locations
    fun getAllLocations(): Flow<List<EquipmentLocation>> = equipmentDao.getAllLocations()

    suspend fun getLocationById(id: Long): EquipmentLocation? = equipmentDao.getLocationById(id)

    suspend fun getDefaultLocation(): EquipmentLocation? = equipmentDao.getDefaultLocation()

    fun getDefaultLocationFlow(): Flow<EquipmentLocation?> = equipmentDao.getDefaultLocationFlow()

    suspend fun createLocation(name: String, description: String = "", iconName: String = "location_on"): Long {
        return equipmentDao.insertLocation(
            EquipmentLocation(
                name = name,
                description = description,
                iconName = iconName
            )
        )
    }

    suspend fun updateLocation(location: EquipmentLocation) = equipmentDao.updateLocation(location)

    suspend fun deleteLocation(location: EquipmentLocation) = equipmentDao.deleteLocation(location)

    suspend fun setDefaultLocation(locationId: Long) = equipmentDao.setAsDefaultLocation(locationId)

    // Location-Equipment mapping
    fun getEquipmentForLocation(locationId: Long): Flow<List<Equipment>> =
        equipmentDao.getEquipmentForLocation(locationId)

    suspend fun getEquipmentIdsForLocation(locationId: Long): List<Long> =
        equipmentDao.getEquipmentIdsForLocation(locationId)

    suspend fun addEquipmentToLocation(locationId: Long, equipmentId: Long) {
        equipmentDao.insertLocationEquipment(LocationEquipmentCrossRef(locationId, equipmentId))
    }

    suspend fun removeEquipmentFromLocation(locationId: Long, equipmentId: Long) {
        equipmentDao.removeEquipmentFromLocation(locationId, equipmentId)
    }

    suspend fun setLocationEquipment(locationId: Long, equipmentIds: List<Long>) {
        equipmentDao.clearEquipmentForLocation(locationId)
        val refs = equipmentIds.map { LocationEquipmentCrossRef(locationId, it) }
        equipmentDao.insertLocationEquipmentAll(refs)
    }

    suspend fun isEquipmentAtLocation(locationId: Long, equipmentId: Long): Boolean =
        equipmentDao.isEquipmentAtLocation(locationId, equipmentId)

    suspend fun getEquipmentCountForLocation(locationId: Long): Int =
        equipmentDao.getEquipmentCountForLocation(locationId)
}

