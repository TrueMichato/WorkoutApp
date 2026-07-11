package com.example.workoutapp.ui.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.workoutapp.data.csv.ExerciseCsvSchema
import com.example.workoutapp.data.csv.ExerciseCsvTemplate
import com.example.workoutapp.data.model.Exercise
import com.example.workoutapp.data.model.WorkoutCategory
import com.example.workoutapp.ui.test.TestTags
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(
    onNavigateToExerciseDetail: (Long) -> Unit,
    onNavigateToAddExercise: () -> Unit,
    viewModel: ExercisesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importCsv)
    }
    val templateSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let(viewModel::saveTemplate)
    }

    LaunchedEffect(uiState.templateSaveMessage) {
        uiState.templateSaveMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearTemplateSaveMessage()
            }
        }
    }

    Scaffold(
        modifier = Modifier.testTag(TestTags.Exercises.Screen),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Exercise Library") },
                actions = {
                    IconButton(
                        onClick = { csvPickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "text/plain", "*/*")) },
                        enabled = !uiState.isImporting,
                        modifier = Modifier.testTag(TestTags.Exercises.ImportButton)
                    ) {
                        if (uiState.isImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.UploadFile, "Import CSV")
                        }
                    }
                    IconButton(
                        onClick = { templateSaveLauncher.launch(ExerciseCsvTemplate.suggestedFileName) },
                        modifier = Modifier.testTag(TestTags.Exercises.SaveTemplateButton)
                    ) {
                        Icon(Icons.Default.Download, "Save CSV template")
                    }
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier.testTag(TestTags.Exercises.FilterButton)
                    ) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }
                }
            )
        },
        floatingActionButton = {
            if (uiState.libraryFilter == ExerciseLibraryFilter.ACTIVE) {
                FloatingActionButton(
                    onClick = onNavigateToAddExercise,
                    modifier = Modifier.testTag(TestTags.Exercises.AddFab)
                ) {
                    Icon(Icons.Default.Add, "Add Exercise")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(TestTags.Exercises.SearchField),
                placeholder = { Text("Search exercises...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExerciseLibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = uiState.libraryFilter == filter,
                        onClick = { viewModel.onLibraryFilterSelected(filter) },
                        label = { Text(filter.displayName) },
                        leadingIcon = {
                            Icon(
                                if (filter == ExerciseLibraryFilter.ACTIVE) Icons.Default.FitnessCenter else Icons.Default.Archive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag(TestTags.Exercises.libraryFilter(filter))
                    )
                }
            }

            // Category chips
            if (uiState.selectedCategory != null) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text(uiState.selectedCategory!!.displayName) },
                        trailingIcon = { Icon(Icons.Default.Close, "Clear filter") }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            uiState.importSummary?.let { summary ->
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(summary, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = viewModel::clearImportFeedback) {
                                Text("Dismiss")
                            }
                        }
                        Text(
                            ExerciseCsvSchema.helpSummary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (uiState.importErrors.isNotEmpty()) {
                            Text(
                                uiState.importErrors.take(4).joinToString(separator = "\n"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            if (uiState.importErrors.size > 4) {
                                Text(
                                    "…and ${uiState.importErrors.size - 4} more row issue(s).",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            // Exercise list
            if (uiState.exercises.isEmpty() && !uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.FitnessCenter,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            if (uiState.libraryFilter == ExerciseLibraryFilter.ARCHIVED) "No archived exercises" else "No exercises found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            if (uiState.libraryFilter == ExerciseLibraryFilter.ARCHIVED) {
                                "Archived exercises will appear here for restore."
                            } else {
                                "Add your first exercise!"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.libraryFilter == ExerciseLibraryFilter.ACTIVE) {
                            Button(onClick = onNavigateToAddExercise) {
                                Icon(Icons.Default.Add, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Exercise")
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseCard(
                            exercise = exercise,
                            familyBadge = uiState.familyBadges[exercise.id],
                            onClick = { onNavigateToExerciseDetail(exercise.id) },
                            onFavoriteClick = { viewModel.toggleFavorite(exercise) },
                            modifier = Modifier.testTag(TestTags.Exercises.exerciseCard(exercise.id))
                        )
                    }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false }
        ) {
            FilterSheetContent(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = {
                    viewModel.onCategorySelected(it)
                    showFilterSheet = false
                }
            )
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: Exercise,
    familyBadge: ExerciseFamilyBadge? = null,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (exercise.description.isNotEmpty()) {
                    Text(
                        exercise.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                when (familyBadge) {
                    is ExerciseFamilyBadge.MainExercise -> Text(
                        "${familyBadge.variationCount} variation${if (familyBadge.variationCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is ExerciseFamilyBadge.Variation -> Text(
                        familyBadge.parentName?.let { "Variation of $it" } ?: "Variation",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    null -> Unit
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            exercise.difficulty.displayName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (exercise.isArchived) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                "Archived",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    if (exercise.timesPerformed > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${exercise.timesPerformed}x performed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!exercise.isArchived) {
                IconButton(onClick = onFavoriteClick) {
                    Icon(
                        if (exercise.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (exercise.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSheetContent(
    selectedCategory: WorkoutCategory?,
    onCategorySelected: (WorkoutCategory?) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Filter by Category",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // All option
        FilterChip(
            selected = selectedCategory == null,
            onClick = { onCategorySelected(null) },
            label = { Text("All Categories") },
            modifier = Modifier.padding(end = 8.dp, bottom = 8.dp)
        )

        // Category chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WorkoutCategory.rotationCategories().forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.displayName) },
                    leadingIcon = {
                        Icon(
                            category.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    // Simple flow row implementation using layout
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
