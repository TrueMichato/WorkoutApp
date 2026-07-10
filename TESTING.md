# Testing Guide

## Current test lanes

### Fast local validation
```zsh
./gradlew --no-daemon --console=plain \
  :app:testDebugUnitTest \
  :app:compileDebugAndroidTestKotlin \
  :app:lintDebug \
  :app:assembleDebug
```

### Instrumentation smoke tests
These use `HiltTestRunner`, `HiltTestApplication`, Android Test Orchestrator, and an in-memory Room database so each test starts from a clean app state. They are intended to run on a stable API 35 Google APIs x86_64 emulator or equivalent hosted Android device with hardware acceleration.

Confirm the host can see one compatible device before running the connected lane:

```zsh
adb devices -l
```

Run all connected smoke tests:

```zsh
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest
```

Run an individual smoke test class while diagnosing a failure:

```zsh
./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.ExerciseLibraryFlowTest

./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.WorkoutPlanFlowTest

./gradlew --no-daemon --console=plain :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.workoutapp.WorkoutSessionRepositoryInstrumentedTest
```

If Gradle reports `0 compatible devices` while an emulator appears to be running, recreate the lane on the supported matrix used by CI: API 35, `google_apis`, `x86_64`, Pixel 2 profile. API preview images or locally wedged emulators can fail discovery before any project test code executes.

### CI validation

GitHub Actions runs `.github/workflows/android.yml` on pushes and pull requests to `main`:

- `host-validation`: `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`, and `:app:assembleDebug`.
- `connected-smoke`: starts an API 35 Google APIs x86_64 Pixel 2 emulator with `reactivecircus/android-emulator-runner` and runs `:app:connectedDebugAndroidTest`.

Both jobs use JDK 17 and Gradle caching. Failure reports are uploaded from `app/build/reports/**`, `app/build/test-results/**`, and `app/build/outputs/androidTest-results/**`.

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
- Add more device coverage if regressions are found on additional supported API levels.
