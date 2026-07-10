package com.example.workoutapp.data.local

import androidx.room.migration.Migration

/**
 * The first public release (v1.0.0) shipped database schema version 5.
 *
 * Git history, the v1.0.0 release tag, and checked-in schema assets contain no shipped
 * pre-v5 Room schema. Keep the supported upgrade baseline explicit instead of fabricating
 * migrations for unknown historical databases.
 */
object WorkoutDatabaseMigrations {
    const val CURRENT_VERSION = 5
    const val FIRST_SHIPPED_VERSION = 5

    val ALL: Array<Migration> = emptyArray()
}
