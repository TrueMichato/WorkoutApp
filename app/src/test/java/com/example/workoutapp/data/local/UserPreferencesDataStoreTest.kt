package com.example.workoutapp.data.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Exercises [UserPreferencesDataStore] against a real, temp-file-backed Preferences DataStore
 * (via [PreferenceDataStoreFactory]) rather than a fake/mock. This works as a plain JVM unit test
 * because DataStore's file I/O doesn't require an Android Context - only [UserPreferencesDataStore]
 * takes an injected `DataStore<Preferences>` (see [com.example.workoutapp.di.PreferencesModule]),
 * which lets this real-I/O coverage run reliably without Robolectric or a device/emulator.
 */
class UserPreferencesDataStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun newStore(): UserPreferencesDataStore = UserPreferencesDataStore(
        PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("prefs_${System.nanoTime()}.preferences_pb") }
        )
    )

    @Test
    fun hasCustomizedProfile_defaultsFalse_onFreshDataStore() = runBlocking {
        assertFalse(newStore().hasCustomizedProfile.first())
    }

    @Test
    fun markProfileCustomized_flipsSignalToTrue() = runBlocking {
        val store = newStore()
        store.markProfileCustomized()
        assertTrue(store.hasCustomizedProfile.first())
    }

    @Test
    fun markProfileCustomized_isIdempotent() = runBlocking {
        val store = newStore()
        store.markProfileCustomized()
        store.markProfileCustomized()
        assertTrue(store.hasCustomizedProfile.first())
    }
}
