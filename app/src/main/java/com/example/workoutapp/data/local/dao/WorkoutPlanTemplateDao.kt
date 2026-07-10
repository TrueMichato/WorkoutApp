package com.example.workoutapp.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.workoutapp.data.model.WorkoutPlanTemplate
import com.example.workoutapp.data.model.WorkoutPlanTemplateExercise
import com.example.workoutapp.data.model.WorkoutPlanTemplateSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutPlanTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: WorkoutPlanTemplate): Long

    @Update
    suspend fun updateTemplate(template: WorkoutPlanTemplate)

    @Delete
    suspend fun deleteTemplate(template: WorkoutPlanTemplate)

    @Query("DELETE FROM workout_plan_templates WHERE id = :templateId")
    suspend fun deleteTemplateById(templateId: Long)

    @Query("SELECT * FROM workout_plan_templates WHERE id = :templateId")
    suspend fun getTemplateById(templateId: Long): WorkoutPlanTemplate?

    @Query(
        """
        SELECT 
            t.id,
            t.name,
            t.description,
            t.targetDurationMinutes,
            t.targetCategories,
            t.scheduledTimeSlot,
            t.sourcePhase,
            t.updatedAt,
            (SELECT COUNT(*) FROM workout_plan_template_exercises e WHERE e.templateId = t.id) AS exerciseCount
        FROM workout_plan_templates t
        ORDER BY t.updatedAt DESC, t.name ASC
        """
    )
    fun getTemplateSummaries(): Flow<List<WorkoutPlanTemplateSummary>>

    @Query(
        "SELECT * FROM workout_plan_template_exercises WHERE templateId = :templateId ORDER BY orderIndex ASC"
    )
    suspend fun getExercisesForTemplate(templateId: Long): List<WorkoutPlanTemplateExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplateExercises(exercises: List<WorkoutPlanTemplateExercise>): List<Long>

    @Query("DELETE FROM workout_plan_template_exercises WHERE templateId = :templateId")
    suspend fun clearExercisesForTemplate(templateId: Long)

    @Transaction
    suspend fun replaceTemplateExercises(
        templateId: Long,
        exercises: List<WorkoutPlanTemplateExercise>
    ) {
        clearExercisesForTemplate(templateId)
        if (exercises.isNotEmpty()) {
            insertTemplateExercises(exercises)
        }
    }

    @Transaction
    suspend fun updateTemplateWithExercises(
        template: WorkoutPlanTemplate,
        exercises: List<WorkoutPlanTemplateExercise>
    ) {
        updateTemplate(template)
        replaceTemplateExercises(template.id, exercises)
    }
}

