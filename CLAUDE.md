# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**ChopCut** is a modern Android application built with Jetpack Compose and Kotlin. This is a new project using the latest Android development stack.

- **Package:** `com.chopcut`
- **Language:** Kotlin (100%)
- **Build System:** Gradle with Kotlin DSL
- **UI Framework:** Jetpack Compose with Material 3
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 36 (Android 15)
- **Compile SDK:** 36 (Android 15)
- **JVM Target:** 11

## Common Commands

### Build Commands
```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build artifacts
./gradlew clean
```

### Testing Commands
```bash
# Run unit tests
./gradlew test

# Run instrumentation tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Run all tests
./gradlew check
```

### Installation Commands
```bash
# Install debug APK to connected device
./gradlew installDebug
```

### Gradle Wrapper
```bash
# Upgrade Gradle wrapper (if needed)
./gradlew wrapper --gradle-version=<version>
```

## Architecture

### Current State
This is a fresh project with basic structure. The app uses:
- **Jetpack Compose** for declarative UI
- **Material 3** design system
- **Edge-to-edge** display support
- Single Activity pattern with `MainActivity` as entry point

### Project Structure
```
app/src/main/java/com/chopcut/
├── ChopCutApplication.kt   # Application class
├── MainActivity.kt          # Main activity entry point
├── util/                    # Utilities
│   └── DispatcherProvider.kt
├── data/                    # Data layer
│   ├── model/              # Data models
│   │   ├── VideoInfo.kt
│   │   ├── VideoCodec.kt
│   │   └── TimeRange.kt
│   ├── codec/              # Codec detection
│   │   └── CodecCapabilities.kt
│   ├── repository/         # Repositories
│   │   └── VideoRepository.kt
│   └── pipeline/           # Video processing pipelines
│       └── CopyPipeline.kt
└── ui/theme/                # Compose theming
    ├── Theme.kt            # App theme configuration
    ├── Color.kt            # Color palette
    └── Type.kt             # Typography definitions
```

### Dependency Management
The project uses a Gradle version catalog (`gradle/libs.versions.toml`) for centralized dependency management. When adding new dependencies:
1. Add the version to `[versions]` section
2. Add the library to `[libraries]` section
3. Reference in `build.gradle.kts` using `libs.library.name`

## Key Dependencies

- **AndroidX Core KTX:** 1.17.0
- **Jetpack Compose BOM:** 2024.09.00
- **Material 3:** Latest via Compose BOM
- **Activity Compose:** 1.12.2
- **Lifecycle Runtime KTX:** 2.10.0
- **Timber:** 5.0.1 (Logging)
- **Kotlin:** 2.0.21
- **Android Gradle Plugin:** 8.13.2

## Build Configuration Notes

- **Kotlin Compose Compiler:** Plugin version 2.0.21 enabled for Compose compilation
- **R Class Generation:** Non-transitive (optimized)
- **Test Runner:** `AndroidJUnitRunner` for instrumentation tests
- **ProGuard:** Configured for release builds (rules in `app/proguard-rules.pro`)

## Development Guidelines

### Adding Features
When implementing new features, consider following modern Android architecture patterns:
- **MVVM** with ViewModels and StateFlow for state management
- **Jetpack Navigation Compose** for screen navigation
- **Room** for local data persistence
- **Retrofit** for network operations

### UI Development
- Use Jetpack Compose for all UI components
- Follow Material 3 design guidelines
- Create composable functions in feature-specific packages
- Use `@Preview` annotations for UI previews during development
- Leverage `ui/theme/` for consistent theming

### Package Organization
As the app grows, structure packages by feature rather than layer:
```
com.chopcut/
├── feature1/
│   ├── ui/
│   ├── data/
│   └── domain/
├── feature2/
│   └── ...
├── core/
│   ├── ui/
│   ├── data/
│   └── network/
└── MainActivity.kt
```

## Testing

### Unit Tests
Place in `app/src/test/`. Run with `./gradlew test`.

### Instrumentation Tests
Place in `app/src/androidTest/`. Run with `./gradlew connectedAndroidTest` (requires connected device).

### Compose UI Tests
Use `androidx.compose.ui:ui-test-junit4` for testing Compose components with `createComposeRule()`.

## Important Files

- `app/build.gradle.kts` - App module build configuration
- `build.gradle.kts` - Project-level build configuration
- `gradle/libs.versions.toml` - Dependency catalog
- `app/src/main/AndroidManifest.xml` - App manifest
- `gradle.properties` - Gradle configuration properties
