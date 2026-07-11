package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.csv.ExerciseCsvImporter
import com.example.workoutapp.data.csv.ExerciseCsvTemplateExporter
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val exerciseCsvImporter: ExerciseCsvImporter,
    private val exerciseCsvTemplateExporter: ExerciseCsvTemplateExporter
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<WorkoutCategory?>(null)
    private val _libraryFilter = MutableStateFlow(ExerciseLibraryFilter.ACTIVE)
    private val _isImporting = MutableStateFlow(false)
    private val _importSummary = MutableStateFlow<String?>(null)
    private val _importErrors = MutableStateFlow<List<String>>(emptyList())
    private val _templateSaveMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val librarySelectionArgs: Flow<ExerciseLibrarySelectionArgs> = combine(
        _searchQuery,
        _selectedCategory,
        _libraryFilter
    ) { query, category, libraryFilter ->
        ExerciseLibrarySelectionArgs(query, category, libraryFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ExercisesUiState> = combine(
        librarySelectionArgs,
        _isImporting,
        _importSummary,
        _importErrors,
        _templateSaveMessage
    ) { selectionArgs, isImporting, importSummary, importErrors, templateSaveMessage ->
        ExercisesUiArgs(
            query = selectionArgs.query,
            category = selectionArgs.category,
            libraryFilter = selectionArgs.libraryFilter,
            isImporting = isImporting,
            importSummary = importSummary,
            importErrors = importErrors,
            templateSaveMessage = templateSaveMessage
        )
    }.flatMapLatest { args ->
        val exercisesFlow = when {
            args.libraryFilter == ExerciseLibraryFilter.ARCHIVED -> exerciseRepository.getAllExercisesIncludingArchived()
            args.query.isNotEmpty() -> exerciseRepository.searchExercises(args.query)
            args.category != null -> exerciseRepository.getExercisesByCategory(args.category)
            else -> exerciseRepository.getAllExercises()
        }
        exercisesFlow.map { exercises ->
            val visibleExercises = when (args.libraryFilter) {
                ExerciseLibraryFilter.ACTIVE -> exercises
                ExerciseLibraryFilter.ARCHIVED -> {
                    var archived = exercises.filter { exercise ->
                        exercise.isArchived &&
                            (args.query.isBlank() || exercise.matchesQuery(args.query))
                    }
                    args.category?.let { category ->
                        archived = archived.filter { exercise ->
                            category in exerciseRepository.getExerciseCategories(exercise.id)
                        }
                    }
                    archived
                }
            }
            // Family grouping is a presentation-only concern: attach each visible exercise's
            // "N variations" / "Variation of X" badge without changing the underlying search or
            // filter query results, so variations remain independently findable by name.
            val familyBadges = buildFamilyBadges(visibleExercises)
            ExercisesUiState(
                exercises = visibleExercises,
                familyBadges = familyBadges,
                searchQuery = args.query,
                selectedCategory = args.category,
                libraryFilter = args.libraryFilter,
                isLoading = false,
                isImporting = args.isImporting,
                importSummary = args.importSummary,
                importErrors = args.importErrors,
                templateSaveMessage = args.templateSaveMessage
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExercisesUiState(isLoading = true)
    )

    private suspend fun buildFamilyBadges(visibleExercises: List<Exercise>): Map<Long, ExerciseFamilyBadge> {
        if (visibleExercises.isEmpty()) return emptyMap()
        val variationParentIds = exerciseRepository.getFamilyRootIdsForAll()
        val exerciseNamesById = visibleExercises.associateBy { it.id }
        val badges = mutableMapOf<Long, ExerciseFamilyBadge>()
        val variationCountByParent = variationParentIds.values.groupingBy { it }.eachCount()
        visibleExercises.forEach { exercise ->
            val parentId = variationParentIds[exercise.id]
            if (parentId != null) {
                val parentName = exerciseNamesById[parentId]?.name
                badges[exercise.id] = ExerciseFamilyBadge.Variation(parentName = parentName)
            } else {
                val count = variationCountByParent[exercise.id] ?: 0
                if (count > 0) {
                    badges[exercise.id] = ExerciseFamilyBadge.MainExercise(variationCount = count)
                }
            }
        }
        return badges
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: WorkoutCategory?) {
        _selectedCategory.value = category
    }

    fun onLibraryFilterSelected(filter: ExerciseLibraryFilter) {
        _libraryFilter.value = filter
    }

    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            exerciseRepository.setFavorite(exercise.id, !exercise.isFavorite)
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            _importSummary.value = null
            _importErrors.value = emptyList()
            try {
                val result = exerciseCsvImporter.importFromUri(uri)
                _importSummary.value = result.summary
                _importErrors.value = result.errors
            } catch (e: Exception) {
                _importSummary.value = "Import failed"
                _importErrors.value = listOf(e.message ?: "Unexpected CSV import error.")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun clearImportFeedback() {
        _importSummary.value = null
        _importErrors.value = emptyList()
    }

    fun saveTemplate(uri: Uri) {
        viewModelScope.launch {
            val result = exerciseCsvTemplateExporter.saveTemplate(uri)
            _templateSaveMessage.value = if (result.isSuccess) {
                "Saved example CSV template."
            } else {
                "Couldn't save the template: ${result.exceptionOrNull()?.message ?: "unknown error"}."
            }
        }
    }

    fun clearTemplateSaveMessage() {
        _templateSaveMessage.value = null
    }
}

private data class ExerciseLibrarySelectionArgs(
    val query: String,
    val category: WorkoutCategory?,
    val libraryFilter: ExerciseLibraryFilter
)

private data class ExercisesUiArgs(
    val query: String,
    val category: WorkoutCategory?,
    val libraryFilter: ExerciseLibraryFilter,
    val isImporting: Boolean,
    val importSummary: String?,
    val importErrors: List<String>,
    val templateSaveMessage: String?
)

enum class ExerciseLibraryFilter(val displayName: String) {
    ACTIVE("Active"),
    ARCHIVED("Archived")
}

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val familyBadges: Map<Long, ExerciseFamilyBadge> = emptyMap(),
    val searchQuery: String = "",
    val selectedCategory: WorkoutCategory? = null,
    val libraryFilter: ExerciseLibraryFilter = ExerciseLibraryFilter.ACTIVE,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importSummary: String? = null,
    val importErrors: List<String> = emptyList(),
    val templateSaveMessage: String? = null,
    val error: String? = null
)

/** Presentation-only exercise family badge for a library card; never affects search/filter results. */
sealed class ExerciseFamilyBadge {
    data class MainExercise(val variationCount: Int) : ExerciseFamilyBadge()
    data class Variation(val parentName: String?) : ExerciseFamilyBadge()
}

private fun Exercise.matchesQuery(query: String): Boolean {
    val normalizedQuery = query.trim()
    return name.contains(normalizedQuery, ignoreCase = true) ||
        description.contains(normalizedQuery, ignoreCase = true)
}
