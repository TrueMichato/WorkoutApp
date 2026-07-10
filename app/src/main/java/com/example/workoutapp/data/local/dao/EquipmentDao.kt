package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.*
import kotlinx.coroutines.flow.Flow

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

