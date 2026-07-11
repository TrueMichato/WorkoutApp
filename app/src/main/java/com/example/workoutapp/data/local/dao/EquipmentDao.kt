package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Result of attempting to atomically insert a new custom [Equipment] row after checking for a
 * name collision, all within a single DB transaction so two concurrent callers (e.g. the CSV
 * importer and the UI, or two overlapping import rows) can never both pass the duplicate check
 * and insert duplicate rows.
 */
sealed class EquipmentInsertOutcome {
    data class Inserted(val equipment: Equipment) : EquipmentInsertOutcome()
    data class DuplicateName(val existingName: String) : EquipmentInsertOutcome()
    data object PersistFailed : EquipmentInsertOutcome()
}

/**
 * Result of attempting to atomically delete a custom [Equipment] row after re-checking it still
 * exists, is still custom, and is unreferenced - all within a single DB transaction so a
 * reference can't be created between the check and the delete.
 */
sealed class EquipmentDeleteOutcome {
    data object Deleted : EquipmentDeleteOutcome()
    data object NotFound : EquipmentDeleteOutcome()
    data object NotCustom : EquipmentDeleteOutcome()
    data class InUse(val exerciseCount: Int, val locationCount: Int) : EquipmentDeleteOutcome()
}

@Dao
interface EquipmentDao {

    // Equipment CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(equipment: Equipment): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(equipment: List<Equipment>): List<Long>

    @Update
    suspend fun update(equipment: Equipment)

    @Delete
    suspend fun delete(equipment: Equipment)

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun getById(id: Long): Equipment?

    @Query("SELECT * FROM equipment ORDER BY isCustom ASC, name ASC")
    fun getAll(): Flow<List<Equipment>>

    @Query("SELECT * FROM equipment WHERE isPortable = 1 ORDER BY name ASC")
    fun getPortable(): Flow<List<Equipment>>

    @Query("SELECT * FROM equipment WHERE isCustom = 1 ORDER BY name ASC")
    fun getCustom(): Flow<List<Equipment>>

    @Query("SELECT * FROM equipment WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun search(query: String): Flow<List<Equipment>>

    @Query("SELECT COUNT(*) FROM equipment")
    suspend fun getCount(): Int

    @Query("SELECT * FROM equipment WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun findByNameIgnoreCase(name: String): Equipment?

    @Query("SELECT COUNT(*) FROM exercise_equipment WHERE equipmentId = :equipmentId")
    suspend fun countExerciseReferences(equipmentId: Long): Int

    @Query("SELECT COUNT(*) FROM location_equipment WHERE equipmentId = :equipmentId")
    suspend fun countLocationReferences(equipmentId: Long): Int

    /**
     * Atomically checks for a case/whitespace-insensitive duplicate name and inserts the row if
     * none exists, all in one DB transaction, so concurrent creators can't both pass the check
     * and insert duplicate custom equipment.
     */
    @Transaction
    suspend fun insertIfNameAvailable(equipment: Equipment): EquipmentInsertOutcome {
        val duplicate = findByNameIgnoreCase(equipment.name)
        if (duplicate != null) {
            return EquipmentInsertOutcome.DuplicateName(duplicate.name)
        }
        val id = insert(equipment)
        val created = getById(id) ?: return EquipmentInsertOutcome.PersistFailed
        return EquipmentInsertOutcome.Inserted(created)
    }

    /**
     * Atomically re-checks that [equipmentId] still exists, is still custom, and is still
     * unreferenced before deleting it, all in one DB transaction, so a reference can't be added
     * between the check and the delete.
     */
    @Transaction
    suspend fun deleteCustomEquipmentIfUnreferenced(equipmentId: Long): EquipmentDeleteOutcome {
        val current = getById(equipmentId) ?: return EquipmentDeleteOutcome.NotFound
        if (!current.isCustom) {
            return EquipmentDeleteOutcome.NotCustom
        }
        val exerciseRefs = countExerciseReferences(current.id)
        val locationRefs = countLocationReferences(current.id)
        if (exerciseRefs > 0 || locationRefs > 0) {
            return EquipmentDeleteOutcome.InUse(exerciseRefs, locationRefs)
        }
        delete(current)
        return EquipmentDeleteOutcome.Deleted
    }

    // Location CRUD
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocation(location: EquipmentLocation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(locations: List<EquipmentLocation>): List<Long>

    @Update
    suspend fun updateLocation(location: EquipmentLocation)

    @Delete
    suspend fun deleteLocation(location: EquipmentLocation)

    @Query("SELECT * FROM equipment_locations WHERE id = :id")
    suspend fun getLocationById(id: Long): EquipmentLocation?

    @Query("SELECT * FROM equipment_locations ORDER BY isDefault DESC, name ASC")
    fun getAllLocations(): Flow<List<EquipmentLocation>>

    @Query("SELECT * FROM equipment_locations WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultLocation(): EquipmentLocation?

    @Query("SELECT * FROM equipment_locations WHERE isDefault = 1 LIMIT 1")
    fun getDefaultLocationFlow(): Flow<EquipmentLocation?>

    @Query("UPDATE equipment_locations SET isDefault = 0")
    suspend fun clearDefaultLocation()

    @Query("UPDATE equipment_locations SET isDefault = 1 WHERE id = :locationId")
    suspend fun setDefaultLocation(locationId: Long)

    @Transaction
    suspend fun setAsDefaultLocation(locationId: Long) {
        clearDefaultLocation()
        setDefaultLocation(locationId)
    }

    // Location-Equipment cross-references
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationEquipment(ref: LocationEquipmentCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocationEquipmentAll(refs: List<LocationEquipmentCrossRef>)

    @Delete
    suspend fun deleteLocationEquipment(ref: LocationEquipmentCrossRef)

    @Query("DELETE FROM location_equipment WHERE locationId = :locationId")
    suspend fun clearEquipmentForLocation(locationId: Long)

    @Query("DELETE FROM location_equipment WHERE locationId = :locationId AND equipmentId = :equipmentId")
    suspend fun removeEquipmentFromLocation(locationId: Long, equipmentId: Long)

    @Query("""
        SELECT e.* FROM equipment e
        INNER JOIN location_equipment le ON e.id = le.equipmentId
        WHERE le.locationId = :locationId
        ORDER BY e.name ASC
    """)
    fun getEquipmentForLocation(locationId: Long): Flow<List<Equipment>>

    @Query("""
        SELECT e.id FROM equipment e
        INNER JOIN location_equipment le ON e.id = le.equipmentId
        WHERE le.locationId = :locationId
    """)
    suspend fun getEquipmentIdsForLocation(locationId: Long): List<Long>

    @Query("""
        SELECT COUNT(*) FROM location_equipment WHERE locationId = :locationId
    """)
    suspend fun getEquipmentCountForLocation(locationId: Long): Int

    // Check if equipment is available at a location
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM location_equipment 
            WHERE locationId = :locationId AND equipmentId = :equipmentId
        )
    """)
    suspend fun isEquipmentAtLocation(locationId: Long, equipmentId: Long): Boolean
}

