package com.example.workoutapp.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Durable, DataStore-backed signal for whether the user has ever intentionally saved/customized
 * their training profile (goal phase or category weights) via Settings.
 *
 * This is deliberately independent of whether a `UserGoal` row exists in Room: the database seeds
 * a neutral default `UserGoal()` row for every install on first launch (see
 * `WorkoutDatabase`'s creation callback), so row presence alone is never proof that a user made a
 * choice. Only an explicit write from the real Settings save flow (`UserGoalRepository.setTrainingPhase`
 * / `setCategoryWeights`) marks this true, so first-run users are honestly told their generator
 * defaults are neutral, not personalized.
 *
 * `dataStore` is injected (see [com.example.workoutapp.di.PreferencesModule]) rather than derived
 * from a Context directly, so this class can be exercised in plain JVM tests against a real,
 * temp-file-backed DataStore instead of requiring Robolectric or a device/emulator.
 *
 * Not covered by an explicit `<include>`/`<exclude>` in backup_rules.xml / data_extraction_rules.xml,
 * so it follows the same default full-backup behavior as the rest of app-private storage - consistent
 * with how the Room database itself is (not) singled out there.
 */
@Singleton
class UserPreferencesDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val HAS_CUSTOMIZED_PROFILE = booleanPreferencesKey("has_customized_training_profile")
    }

    val hasCustomizedProfile: Flow<Boolean> =
        dataStore.data.map { prefs -> prefs[Keys.HAS_CUSTOMIZED_PROFILE] ?: false }

    suspend fun markProfileCustomized() {
        dataStore.edit { prefs -> prefs[Keys.HAS_CUSTOMIZED_PROFILE] = true }
    }
}
