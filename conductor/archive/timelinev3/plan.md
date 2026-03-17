# Timeline v3 Implementation Plan - Minimal Viable Product (MVP)

## Phase 1: Core Design & Research

1.  **Define Minimal UI Architecture:**
    *   Design the interaction between the video player and the horizontal thumbnail scroll.
    *   Identify minimal components required.
    *   **Leverage Jetpack Compose:** Create a new Composable function for `timelinev3`.
    *   **Video Player:** Utilize `AndroidView` to inflate `player_view.xml` (using `androidx.media3.ui.PlayerView`).
    *   **Thumbnails:** Implement a `LazyRow` for horizontal scrolling of thumbnails.
    *   **Thumbnail Item:** For each thumbnail, either use `AndroidView` to inflate `item_timeline_thumbnail.xml` or create a dedicated simple Thumbnail Composable.
    *   **Data Flow:** Design data provision via a ViewModel.
2.  **Research & Design v3 Extraction/Decoding:**
    *   Investigate optimal techniques for fast, efficient frame/thumbnail extraction and decoding, considering:
        *   **Direct MediaCodec integration:** Explore leveraging `MediaCodec` for low-level frame extraction to potentially bypass `MediaMetadataRetriever` overhead for high-frequency access.
        *   **Hardware acceleration:** Investigate if existing `MediaCodec` usage fully exploits hardware decoding for thumbnails.
        *   **Bitmap pipeline optimization:** Design strategies to minimize `Bitmap` copies and ensure direct output to desired `Bitmap.Config` (e.g., `RGB_565`) or `HardwareBuffer`.
        *   **Alternative frame access patterns:** Evaluate processing streams of frames more efficiently than individual extractions.
    *   Design the API for these new methods, ensuring it's clean, abstracts low-level details, and integrates efficiently with caching mechanisms.
3.  **Define MAX_PERFORMANCE Strategy:**
    *   Outline what `MAX_PERFORMANCE` entails:
        *   **Aggressive Resource Management:** Prompt `Bitmap` recycling, minimal memory allocations, exploration of `HardwareBuffer` for direct GPU rendering.
        *   **Prioritize Critical Paths:** Ensure video playback and active thumbnail scrolling receive maximum CPU/GPU resources.
        *   **Minimize Non-Essential Work:** Reduce background tasks, animations, or unnecessary UI updates when `MAX_PERFORMANCE` is active.
        *   **Leverage Hardware Acceleration:** Ensure `MediaCodec` and `ExoPlayer` are configured for optimal hardware utilization.
        *   **Optimized I/O:** Implement efficient media data reading, potentially with pre-fetching strategies.
    *   Identify key performance bottlenecks to address within the existing (or new v3) architecture.
4.  **Establish MVP Success Criteria:**
    *   Define clear, measurable metrics for "extremely smooth" performance:
        *   **Frame Rate:** Achieve consistent 60 FPS for video playback and thumbnail scrolling (minimum acceptable: 30 FPS on target devices).
        *   **UI Responsiveness:** Input latency for scroll gestures < 100ms.
        *   **Jank Reduction:** Minimize skipped frames (e.g., using JankStats or similar tools, targeting < 1% jank rate).
        *   **Memory Footprint:** X% reduction in memory usage compared to current timeline (target to be defined after initial baseline).
        *   **CPU Utilization:** Y% reduction in CPU usage during playback and scrolling (target to be defined after initial baseline).
        *   **Extraction/Decoding Speed:** Z ms per batch for v3 extraction/decoding (target to be defined after initial POC).
        *   **MAX_PERFORMANCE Validation:** Demonstrable improvement in the above metrics when `MAX_PERFORMANCE` mode is active.

## Phase 2: Minimal Core Development

1.  **Setup Environment:**
    *   Create `timelinev3` development branch. (DONE)
2.  **Implement Simplified UI:** (DONE)
    *   Develop the basic video player component.
    *   Implement the horizontal thumbnail scroll with placeholder data.
3.  **Implement v3 Extraction/Decoding Proof-of-Concept (POC):** (DONE)
    *   Develop a basic, functional implementation of the new extraction/decoding methods.
4.  **Integrate MAX_PERFORMANCE Toggle:** (DONE)
    *   Implement the basic mechanism to enable/disable `MAX_PERFORMANCE` mode.
5.  **Basic Integration & Initial Performance Test:** (DONE)
    *   Connect the UI, player, and extraction POC.
    *   Perform initial tests to validate core functionality and identify immediate performance gains/issues.

## Phase 3: Refinement & Validation

1.  **Performance Optimization Loop:**
    *   Iteratively optimize the UI, extraction/decoding, and `MAX_PERFORMANCE` implementation based on performance tests.
    *   Focus on achieving "extremely smooth" operation.
2.  **Functional Testing:**
    *   Verify that the simplified UI, v3 extraction/decoding, and `MAX_PERFORMANCE` toggle function correctly.
3.  **Minimal Documentation:**
    *   Document how to use the `timelinev3` component and its `MAX_PERFORMANCE` mode.

## Phase 4: Release Preparation (MVP)

1.  **Code Review.**
2.  **Final Performance Validation.**
3.  **Prepare for integration into main project.**

