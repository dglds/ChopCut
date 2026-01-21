# Implementation Plan - Timeline V2 Scrubbing Synchronization

This plan focuses on achieving frame-accurate, real-time scrubbing synchronization between the timeline and the video player.

## Phase 1: Frame-Accurate Logic (TDD)
- [x] Task: Create `TimelineScrubbingTest.kt` to define expected behavior for frame-snapping and precise time mapping. [db0cfa0]
    - [x] Test that scroll offset translates to the correct video time. [db0cfa0]
    - [x] Test that the calculated time snaps to the nearest frame (e.g., multiples of 33.33ms for 30fps). [db0cfa0]
- [x] Task: Refactor `TimelineScrollController` and `TimelineCalculator` to satisfy the frame-snapping tests. [db0cfa0]
    - [x] Ensure the controller is aware of the video's frame rate. [db0cfa0]
- [x] Task: Conductor - User Manual Verification 'Frame-Accurate Logic' (Protocol in workflow.md) [checkpoint: 7ae6cfc]

## Phase 2: Integration and Performance
- [ ] Task: Optimize `VideoTimelineV2` interaction loop.
    - [ ] Ensure the `snapshotFlow` and `collect` loop in the timeline is optimized for high-frequency updates.
    - [ ] Verify that the UI timer updates in perfect lock-step with the scroll.
- [ ] Task: Verify `PreviewManager` Seek Performance.
    - [ ] Ensure `exoPlayer.seekTo` is called with appropriate parameters for fast scrubbing feedback.
- [ ] Task: Conductor - User Manual Verification 'Integration and Performance' (Protocol in workflow.md)
