# Implementation Plan: TimelineV5 Component

Implement a robust, Material 3 Expressive video timeline component for range selection and scrubbing, utilizing Media3 for frame extraction.

## Phase 1: Foundation & State Management
- [x] Task: Define `TimelineState` and `Thumbnail` data models to handle range (start/end) and playhead positions. [09bd213]
- [x] Task: Implement a ViewModel or State Holder to manage the interaction logic (e.g., preventing start handle from crossing end handle). [fa9e443]
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Foundation & State Management' (Protocol in workflow.md)

## Phase 2: Thumbnail Extraction Engine
- [x] Task: Create a `ThumbnailProvider` using Media3 `MediaMetadataRetriever` to extract frames at specific intervals. [6a857de]
- [x] Task: Implement an LRU cache for extracted thumbnails to ensure smooth scrolling. [1b35944]
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Thumbnail Extraction Engine' (Protocol in workflow.md)

## Phase 3: UI - Scrubber & Thumbnail Strip
- [x] Task: Build the `ThumbnailStrip` Compose component to display the sequence of extracted frames. [ff79018]
- [x] Task: Implement the `Playhead` (Scrubber) component with horizontal drag interaction. [55f41c7]
- [ ] Task: Conductor - User Manual Verification 'Phase 3: UI - Scrubber & Thumbnail Strip' (Protocol in workflow.md)

## Phase 4: UI - Range Selection & M3 Expressive Styling
- [x] Task: Implement draggable start and end handles with "Material 3 Expressive" aesthetics (bold shapes, haptic feedback). [dcd4064]
- [~] Task: Add time overlays that update dynamically during handle dragging.
- [ ] Task: Conductor - User Manual Verification 'Phase 4: UI - Range Selection & M3 Expressive Styling' (Protocol in workflow.md)

## Phase 5: Media3 Integration & Synchronization
- [ ] Task: Connect the `TimelineV5` component to the `ExoPlayer` instance for real-time scrubbing synchronization.
- [ ] Task: Optimize frame updates to ensure smooth visual feedback during high-frequency scrubbing.
- [ ] Task: Conductor - User Manual Verification 'Phase 5: Media3 Integration & Synchronization' (Protocol in workflow.md)