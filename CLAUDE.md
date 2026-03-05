# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ChopCut is an Android video editor app built with Kotlin, Jetpack Compose, and Media3/ExoPlayer. The primary implemented feature is video trimming. App package: `com.chopcut`, minSdk 24, compileSdk 36.

## Build & Run Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.chopcut.ui.components.TrimRangeDataTest"

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Logcat filtered to app tags
./gradlew logcatTimber

# Performance tests (requires connected device)
./gradlew performanceTest
./gradlew performanceTestThumbnails
./gradlew pullPerformanceReports
```

## Architecture

**Navigation** (defined in `MainActivity.kt`):
- `onboarding` → `home` → `editor?videoUri={uri}`
- Also: `preferences`, `audio_waveforms_test` (debug screen)

**ViewModel hierarchy** (all Activity-scoped except `TrimViewModel`):
- `PreloadViewModel` — orchestrates preloading; delegates to `ThumbnailViewModel` and `AudioViewModel`
- `ThumbnailViewModel` — loads thumbnail strips; exposes `strips: StateFlow<Map<Int, Bitmap>>`
- `AudioViewModel` — loads audio waveform amplitudes
- `TrimViewModel` — manages trim editor state (ranges, playhead position, waveform)
- `HomeViewModel` — manages video selection state

**Data layer** (`app/src/main/java/com/chopcut/data/`):
- `thumbnail/ThumbnailStripManager` — extracts and caches horizontal bitmap strips; uses `ThumbnailExtractorBatch` to reuse `MediaMetadataRetriever` per segment
- `thumbnail/ThumbnailCacheManager` — LRU memory cache + disk cache; initialized as singleton in `ChopCutApplication`
- `audio/AudioDataExtractor` + `WaveFormGenerator` — extracts PCM data and generates waveform bars
- `pipeline/TransformerPipeline` — uses Media3 Transformer for actual video trimming; emits `Flow<TrimProgress>`
- `repository/VideoRepository` — queries `ContentResolver` for video metadata and temp files

**Thumbnail strip system:**
- Videos are divided into time segments; each segment becomes one `Bitmap` strip
- Strips use `RGB_565` (50% less memory than ARGB_8888) and WEBP compression on disk
- Adaptive strips: early segments have fewer thumbnails (fast initial load), later segments have more
- Cache key includes `CACHE_VERSION` from `ThumbnailConstants` — increment to invalidate all cached strips

**Preload flow:**
1. User selects video on `HomeScreen`
2. `HomeScreen` calls `preloadViewModel.startPreload(uri, stripsToPreload = 6)`
3. Navigation to `TrimScreen` is unlocked when ≥ 6 strips (or all if video is short) are loaded
4. `TrimScreen` receives pre-loaded strips and audio from the Activity-scoped ViewModels

## Constants System

All magic numbers must use the centralized constants in `app/src/main/java/com/chopcut/config/constants/`. See `CONSTANTS_GUIDE.md` for full details.

| Category | File | Covers |
|----------|------|--------|
| Thumbnails | `ThumbnailConstants.kt` | dimensions, quality, cache, concurrency |
| Audio | `AudioConstants.kt` | sample rates, buffers, waveform |
| Timeline | `TimelineConstants.kt` | UI dimensions, trim constraints |
| Cache | `CacheConstants.kt` | size limits |
| Performance | `PerformanceConstants.kt` | thread counts, timeouts |
| Quality | `QualityConstants.kt` | compression, aspect ratios |
| File formats | `FileFormatConstants.kt` | extensions, MIME types |
| Animations | `AnimationConstants.kt` | durations, easing |

Never use hardcoded values in new code. Always use `const val` for primitive constants.

## Design System / Theme

- Colors: `ui/theme/DesignColor.kt` — semantic tokens (`Primary`, `OnSurface`, `TextSecondary`, etc.)
- Spacing: `ui/theme/Spacing.kt` — `ChopCutSpacing.xs/sm/md/lg` etc.
- Typography: `ui/theme/DesignType.kt`
- Use theme tokens in composables, not raw Color/dp literals

## Logging

Use `Timber` for all logging (not `Log`). In DEBUG builds, logs are printed to logcat and saved to a local file via `LocalFileLoggingTree`. All builds write to device storage via `FileLoggingTree`.

## Key Patterns

- ViewModels use `StateFlow` + `collectAsStateWithLifecycle()` in composables
- Coroutine dispatchers are provided via `DispatcherProvider` (for testability)
- Error types live in `util/error/` (`ChopCutException`, `ErrorResult`)
- `DebugToast` / `DebugViewModel` are only shown in `BuildConfig.DEBUG` builds
