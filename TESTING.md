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

- `ExerciseCsvTemplateDownloadInstrumentedTest`
  - uses Espresso-Intents to stub `ACTION_CREATE_DOCUMENT`
  - verifies the "Save CSV template" action in Exercise Library launches it with the
    `text/csv` MIME type and the expected suggested file name, without driving the real
    system file picker

## Exercise CSV import/template coverage

`ExerciseCsvSchema` (`app/src/main/java/com/example/workoutapp/data/csv/ExerciseCsvSchema.kt`) is
the single source of truth for every column `ExerciseCsvImporter` accepts. The in-app help text,
the generated example file (`ExerciseCsvTemplate`), and importer validation all derive from it, so
they cannot silently drift apart. Unit tests under
`app/src/test/java/com/example/workoutapp/data/csv/`:

- `ExerciseCsvSchemaTest` — header uniqueness/completeness, required-header detection, and that
  `ExerciseCsvTemplate`'s header row matches the schema exactly.
- `ExerciseCsvTemplateTest` — the generated example parses to exactly the two documented sample
  rows and round-trips as valid UTF-8.
- `ExerciseCsvTemplateExporterTest` — the template is written byte-for-byte to the chosen `Uri`,
  and a failure to open an output stream is surfaced rather than thrown.
- `ExerciseCsvImporterTest` — missing required headers (`name`/`categories`) are rejected before
  any equipment or exercise is created; unknown headers are reported as a non-fatal warning; the
  generated template imports successfully end-to-end under the same schema.
- `ExerciseCsvParserTest` — quoted/escaped values, multi-value delimiters, and a `writeLine`/
  `parseLine` round trip for cells containing commas, quotes, and newlines.

## Recommended next additions

- Add more test tags to `ActiveWorkoutScreen` for set logging and completion flows.
- Cover generator preview save-as-plan and history views.
- Add more device coverage if regressions are found on additional supported API levels.
