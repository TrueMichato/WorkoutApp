package com.example.workoutapp.ui.exercises

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workoutapp.data.csv.ExerciseCsvImporter
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
    private val exerciseCsvImporter: ExerciseCsvImporter
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow<WorkoutCategory?>(null)
    private val _isImporting = MutableStateFlow(false)
    private val _importSummary = MutableStateFlow<String?>(null)
    private val _importErrors = MutableStateFlow<List<String>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ExercisesUiState> = combine(
        _searchQuery,
        _selectedCategory,
        _isImporting,
        _importSummary,
        _importErrors
    ) { query, category, isImporting, importSummary, importErrors ->
        ExercisesUiArgs(query, category, isImporting, importSummary, importErrors)
    }.flatMapLatest { args ->
        when {
            args.query.isNotEmpty() -> exerciseRepository.searchExercises(args.query)
            args.category != null -> exerciseRepository.getExercisesByCategory(args.category)
            else -> exerciseRepository.getAllExercises()
        }.map { exercises ->
            ExercisesUiState(
                exercises = exercises,
                searchQuery = args.query,
                selectedCategory = args.category,
                isLoading = false,
                isImporting = args.isImporting,
                importSummary = args.importSummary,
                importErrors = args.importErrors
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ExercisesUiState(isLoading = true)
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: WorkoutCategory?) {
        _selectedCategory.value = category
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
}

private data class ExercisesUiArgs(
    val query: String,
    val category: WorkoutCategory?,
    val isImporting: Boolean,
    val importSummary: String?,
    val importErrors: List<String>
)

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val selectedCategory: WorkoutCategory? = null,
    val isLoading: Boolean = false,
    val isImporting: Boolean = false,
    val importSummary: String? = null,
    val importErrors: List<String> = emptyList(),
    val error: String? = null
)

