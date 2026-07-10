package com.example.workoutapp.data.local.dao

import androidx.room.*
import com.example.workoutapp.data.model.FeedbackAction
import com.example.workoutapp.data.model.MLFeedbackEvent
import com.example.workoutapp.data.model.MLPreferenceScore
import com.example.workoutapp.data.model.WorkoutCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface MLFeedbackDao {

    // ── Feedback Events ──────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: MLFeedbackEvent): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<MLFeedbackEvent>)

    @Update
    suspend fun updateEvent(event: MLFeedbackEvent)

    @Query("SELECT * FROM ml_feedback_events WHERE id = :id")
    suspend fun getEventById(id: Long): MLFeedbackEvent?

    @Query("SELECT * FROM ml_feedback_events ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<MLFeedbackEvent>>

    @Query("SELECT * FROM ml_feedback_events WHERE exerciseId = :exerciseId ORDER BY createdAt DESC")
    fun getEventsForExercise(exerciseId: Long): Flow<List<MLFeedbackEvent>>

    @Query("SELECT * FROM ml_feedback_events WHERE suggestedCategory = :category ORDER BY createdAt DESC")
    fun getEventsForCategory(category: WorkoutCategory): Flow<List<MLFeedbackEvent>>

    @Query("SELECT * FROM ml_feedback_events WHERE suggestedInSessionId = :sessionId")
    suspend fun getEventsForSession(sessionId: Long): List<MLFeedbackEvent>

    @Query("""
        UPDATE ml_feedback_events 
        SET `action` = :action, perceivedDifficulty = :difficulty, satisfactionRating = :rating 
        WHERE suggestedInSessionId = :sessionId AND exerciseId = :exerciseId
    """)
    suspend fun updateEventOutcome(
        sessionId: Long,
        exerciseId: Long,
        action: FeedbackAction,
        difficulty: Int?,
        rating: Int?
    )

    // ── Aggregation Queries ──────────────────────────────────────────

    @Query("""
        SELECT COUNT(*) FROM ml_feedback_events 
        WHERE exerciseId = :exerciseId AND `action` IN ('ACCEPTED', 'COMPLETED')
    """)
    suspend fun getPositiveCountForExercise(exerciseId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM ml_feedback_events 
        WHERE exerciseId = :exerciseId AND `action` IN ('REJECTED', 'SKIPPED', 'SWAPPED')
    """)
    suspend fun getNegativeCountForExercise(exerciseId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM ml_feedback_events 
        WHERE suggestedCategory = :category AND `action` IN ('ACCEPTED', 'COMPLETED')
    """)
    suspend fun getPositiveCountForCategory(category: WorkoutCategory): Int

    @Query("""
        SELECT COUNT(*) FROM ml_feedback_events 
        WHERE suggestedCategory = :category AND `action` IN ('REJECTED', 'SKIPPED', 'SWAPPED')
    """)
    suspend fun getNegativeCountForCategory(category: WorkoutCategory): Int

    @Query("SELECT COUNT(*) FROM ml_feedback_events")
    suspend fun getTotalEventCount(): Int

    @Query("SELECT * FROM ml_feedback_events ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentEventsSync(limit: Int): List<MLFeedbackEvent>

    // ── Time-based queries ───────────────────────────────────────────

    @Query("""
        SELECT AVG(CASE WHEN `action` IN ('ACCEPTED', 'COMPLETED') THEN 1.0 ELSE 0.0 END)
        FROM ml_feedback_events
        WHERE exerciseId = :exerciseId AND hourOfDay BETWEEN :startHour AND :endHour
    """)
    suspend fun getAcceptanceRateByTimeOfDay(exerciseId: Long, startHour: Int, endHour: Int): Float?

    @Query("""
        SELECT AVG(CASE WHEN `action` IN ('ACCEPTED', 'COMPLETED') THEN 1.0 ELSE 0.0 END)
        FROM ml_feedback_events
        WHERE exerciseId = :exerciseId AND dayOfWeek = :dayOfWeek
    """)
    suspend fun getAcceptanceRateByDayOfWeek(exerciseId: Long, dayOfWeek: Int): Float?

    // ── Preference Scores Cache ──────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferenceScore(score: MLPreferenceScore)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreferenceScores(scores: List<MLPreferenceScore>)

    @Query("SELECT * FROM ml_preference_scores WHERE `key` = :key")
    suspend fun getPreferenceScore(key: String): MLPreferenceScore?

    @Query("SELECT * FROM ml_preference_scores WHERE `key` LIKE 'exercise:%'")
    suspend fun getAllExerciseScores(): List<MLPreferenceScore>

    @Query("SELECT * FROM ml_preference_scores WHERE `key` LIKE 'category:%'")
    suspend fun getAllCategoryScores(): List<MLPreferenceScore>

    @Query("DELETE FROM ml_preference_scores")
    suspend fun clearPreferenceScores()

    // ── Cleanup ──────────────────────────────────────────────────────

    @Query("DELETE FROM ml_feedback_events WHERE createdAt < :timestamp")
    suspend fun deleteEventsOlderThan(timestamp: Long)

    @Query("DELETE FROM ml_feedback_events")
    suspend fun clearAllEvents()
}



