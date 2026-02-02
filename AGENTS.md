# AGENTS.md

Guidelines for AI agents working on the ChopCut Android project.

## Project Overview

ChopCut is a video editing Android app built with:
- **Language:** Kotlin 100%
- **UI:** Jetpack Compose + Material 3
- **Architecture:** MVVM with ViewModels and StateFlow
- **Build:** Gradle with Kotlin DSL
- **Package:** `com.chopcut`
- **Min SDK:** 24, Target SDK: 36

## Build Commands

```bash
# Build project
./gradlew build

# Quick syntax check (lint)
./gradlew quick

# Clean build artifacts
./gradlew clean

# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

## Test Commands

```bash
# Run all unit tests
./gradlew test

# Run single test class
./gradlew test --tests "com.chopcut.ExampleUnitTest"

# Run single test method
./gradlew test --tests "com.chopcut.ExampleUnitTest.testMethod"

# Run instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest

# Run all checks
./gradlew check
```

## Code Style Guidelines

### Language
- **Think and comment in Portuguese (pt-br)** per CLAUDE.md
- All user-facing strings in Portuguese

### Formatting
- 4 spaces indentation (no tabs)
- No trailing whitespace
- One blank line between class members
- Maximum line length: 120 characters

### Naming Conventions
- **Classes:** PascalCase (`VideoRepository`, `EditorViewModel`)
- **Functions:** camelCase (`loadVideo`, `processThumbnail`)
- **Variables:** camelCase (`videoUri`, `isLoading`)
- **Constants:** UPPER_SNAKE_CASE in companion objects
- **Private properties:** prefix with `_` only if necessary
- **Compose functions:** PascalCase (`EditorScreen`, `VideoPreview`)

### Imports
- Group: AndroidX → Kotlin stdlib → Third-party → Project
- No wildcard imports (except Compose imports)
- Remove unused imports
- Order alphabetically within groups

```kotlin
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import com.chopcut.data.model.VideoInfo
import com.chopcut.data.repository.VideoRepository
```

### Types
- Prefer `val` over `var`
- Use explicit types for public APIs, inference for locals
- Use `data class` for models
- Use `sealed class` for state hierarchies and errors
- Prefer `StateFlow` over `LiveData`
- Use `Result<T>` for operations that can fail

### Error Handling
- Use custom `ChopCutException` sealed class hierarchy
- Handle errors through `ErrorHandler` object
- Provide `RecoveryStrategy` for user actions
- Log with Timber before throwing

```kotlin
// Good
throw ChopCutException.Video.FileNotFound(uri)

// Good
try {
    processVideo()
} catch (e: IOException) {
    Timber.e(e, "Failed to process video")
    throw ChopCutException.Export.TranscodeFailed(e)
}
```

### Architecture
- **MVVM:** ViewModel exposes StateFlow, UI collects it
- **Repository:** Data access abstraction in `data/repository/`
- **Models:** Data classes in `data/model/`
- **UI:** Composables in `ui/screen/` and `ui/components/`
- **DI:** Constructor injection for ViewModels (pass Context/Application)

### Compose Guidelines
- Use Material 3 components
- Preview all UI components with `@Preview`
- Hoist state to ViewModel
- Use `remember` for local UI state only
- Follow unidirectional data flow

```kotlin
@Composable
fun VideoPreview(
    videoUri: Uri,
    modifier: Modifier = Modifier
) {
    // Implementation
}

@Preview
@Composable
private fun VideoPreviewPreview() {
    ChopCutTheme {
        VideoPreview(videoUri = Uri.EMPTY)
    }
}
```

### Logging
- Use Timber (not android.util.Log)
- Log errors with context: `Timber.e(e, "message")`
- Log debug info: `Timber.d("Processing video %s", uri)`

### Documentation
- KDoc for public APIs and complex logic
- Comments in Portuguese
- Explain "why", not "what"

### Testing
- Test files in `src/test/` (unit) or `src/androidTest/` (instrumentation)
- Use MockK for mocking
- Use Turbine for Flow testing
- Use `kotlinx-coroutines-test` for coroutine testing

### Dependencies
- Add to `gradle/libs.versions.toml`
- Use version catalog references: `implementation(libs.androidx.core.ktx)`

## Project Structure

```
app/src/main/java/com/chopcut/
├── data/
│   ├── model/          # Data classes
│   ├── repository/     # Repositories
│   ├── local/          # Room DAOs, Preferences
│   ├── pipeline/       # Video processing
│   └── audio/          # Audio processing
├── ui/
│   ├── screen/         # Full screens
│   ├── components/     # Reusable composables
│   ├── theme/          # Colors, Typography
│   └── timeline/       # Timeline feature
├── service/            # Foreground services
├── util/               # Utilities, error handling
└── MainActivity.kt
```
