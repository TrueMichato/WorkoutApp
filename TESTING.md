# Testing Guide

## Current test lanes

### Fast local validation
```zsh
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
```

### Instrumentation smoke tests
These use Hilt with an in-memory Room database so each test starts from a clean app state.

```zsh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.ExerciseLibraryFlowTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.WorkoutPlanFlowTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.WorkoutSessionRepositoryInstrumentedTest
```

## What the new instrumentation tests cover

- `ExerciseLibraryFlowTest`
  - navigates into the exercise library
  - opens the add-exercise screen
  - saves a new exercise
  - verifies it is visible in the library

- `WorkoutPlanFlowTest`
  - seeds an exercise directly into the in-memory database
  - creates a reusable workout plan through the UI
  - saves and plays the plan
  - verifies the active workout shows the seeded rich prescription details

- `WorkoutSessionRepositoryInstrumentedTest`
  - verifies template playback preserves section, notes, and rich prescription JSON

## Recommended next additions

- Add more test tags to `ActiveWorkoutScreen` for set logging and completion flows.
- Cover generator preview save-as-plan and history views.
- Add CSV import seams via a fake importer or import abstraction.
- Add CI device coverage using a managed emulator or hosted Android runner.

