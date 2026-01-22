# Implementation Plan - Timelinev4

## Phase 1: Foundation & State Management
- [x] Task: Define `TimelineState` and `TimelineEvent` sealed classes.
    - [x] Create `TimelineState.kt` (position, zoomLevel, isScrubbing, clips).
    - [x] Create `TimelineEvent.kt` (Seek, Zoom, Scroll).
- [x] Task: Implement `TimelineViewModel` (Single Source of Truth).
    - [x] TDD: Write unit tests for ViewModel state updates (seek, play/pause sync).
    - [x] Implement `TimelineViewModel` to manage the shared state between Player and Timeline.
    - [x] Implement `updatePosition(long)` and `onScrubStart/End` logic.
- [ ] Task: Conductor - User Manual Verification 'Foundation & State Management' (Protocol in workflow.md)

## Phase 2: Visual Components (Core UI)
- [ ] Task: Implement `TimecodeView`.
    - [ ] TDD: Write tests for timestamp formatting (ms to 00:00:00.00).
    - [ ] Create Composable/View for displaying the timecode.
- [ ] Task: Implement `FilmstripView` (Background Thumbnails).
    - [ ] TDD: Write tests for thumbnail slot calculation based on zoom level.
    - [ ] Implement thumbnail loader logic (placeholder vs actual image).
    - [ ] Create `FilmstripView` component to render the sequence of images.
- [ ] Task: Implement `TimelineContainer` Layout.
    - [ ] Assemble the basic layout structure (Playhead centered, Filmstrip background).
- [ ] Task: Conductor - User Manual Verification 'Visual Components (Core UI)' (Protocol in workflow.md)

## Phase 3: Interactive Scrubbing & Performance
- [ ] Task: Implement Touch Gestures & Scrubbing Logic.
    - [ ] Detect drag gestures on the timeline.
    - [ ] Convert drag distance to time delta based on current zoom.
    - [ ] Dispatch `Seek` events to ViewModel.
- [ ] Task: Implement Hybrid Rendering Strategy.
    - [ ] TDD: Write tests for rendering mode switching (Fast/Preview vs. Accurate/High-Quality).
    - [ ] Implement logic to detect scroll velocity/state.
    - [ ] optimize: Trigger "Preview Mode" during Drag/Scroll.
    - [ ] optimize: Trigger "Precision Mode" on Idle/Stop.
- [ ] Task: Conductor - User Manual Verification 'Interactive Scrubbing & Performance' (Protocol in workflow.md)

## Phase 4: Integration & Polish
- [ ] Task: Integrate `Timelinev4` into Main Editor.
    - [ ] Replace existing timeline placeholder with `Timelinev4`.
    - [ ] Connect `TimelineViewModel` to the main Video Player instance.
- [ ] Task: Verify Synchronization.
    - [ ] Ensure playhead stays centered during playback.
    - [ ] Ensure video seeks immediately on scrub.
- [ ] Task: Conductor - User Manual Verification 'Integration & Polish' (Protocol in workflow.md)
