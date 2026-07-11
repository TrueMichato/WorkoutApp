package com.example.workoutapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * The first public release (v1.0.0) shipped database schema version 5.
 *
 * Git history, the v1.0.0 release tag, and checked-in schema assets contain no shipped
 * pre-v5 Room schema. Keep the supported upgrade baseline explicit instead of fabricating
 * migrations for unknown historical databases.
 */
object WorkoutDatabaseMigrations {
    const val CURRENT_VERSION = 6
    const val FIRST_SHIPPED_VERSION = 5

    /**
     * Adds the `exercise_variations` table (exercise family/variation links). Purely additive:
     * no existing table is touched, so every v5 exercise, relation, and history row is preserved
     * untouched and remains a standalone exercise (no rows are inserted into the new table).
     */
    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `exercise_variations` (
                    `variationExerciseId` INTEGER NOT NULL,
                    `parentExerciseId` INTEGER NOT NULL,
                    `focus` TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(`variationExerciseId`),
                    FOREIGN KEY(`variationExerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`parentExerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_exercise_variations_parentExerciseId` ON `exercise_variations` (`parentExerciseId`)"
            )
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_5_6)
}
