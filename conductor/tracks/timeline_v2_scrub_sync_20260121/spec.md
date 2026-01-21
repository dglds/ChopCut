# Track Specification: Timeline V2 Scrubbing Synchronization

#### Overview
This track focuses on fixing the synchronization between the `VideoTimelineV2` and the video preview player. The goal is to ensure that scrubbing (dragging) the timeline provides immediate, frame-accurate visual feedback in the video player, resolving the drift between scroll position and video time.

#### Functional Requirements
1.  **Real-time Scrubbing:** The video preview must update its frame continuously as the user drags the timeline.
2.  **Accurate Time Mapping:** The `TimelineScrollController` must accurately convert the scroll offset to video milliseconds, ensuring the center playhead always points to the correct timestamp.
3.  **Snap-to-Frame Logic:** Calculated seek times during scrubbing must be rounded to the nearest frame based on the video's frame rate (e.g., 30fps = 33.33ms increments) to prevent visual artifacts and ensure the player lands on a valid frame.
4.  **UI Feedback:** The central timer below the timeline must update in perfect sync with the scrubbing position.

#### Non-Functional Requirements
*   **Performance:** The scrubbing interaction must be fluid (aiming for 60fps UI updates) with minimal latency in the video preview update.

#### Acceptance Criteria
*   [ ] Dragging the timeline updates the video preview in real-time.
*   [ ] The video frame shown at the playhead matches the calculated timestamp exactly.
*   [ ] Releasing the scrub interaction leaves the video at the correct, frame-aligned position.
*   [ ] No "drift" occurs even after long scrubs or multiple interactions.

#### Out of Scope
*   Adding back thumbnails (maintaining the simplified visual style).
*   Changes to the bottom control panel tools.
