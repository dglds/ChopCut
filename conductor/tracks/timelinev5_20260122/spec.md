# Specification: TimelineV5 Component

## Overview
Implement a high-performance, Material Design 3 Expressive timeline component (`TimelineV5`) for video trimming and navigation. This component will be inspired by the `VideoTrimmer` library but built natively using Jetpack Compose and integrated with the project's Media3-based architecture.

## Functional Requirements
- **Thumbnail Strip:** Generate and display a horizontal sequence of video frames representing the video content.
- **Range Selection:** Provide two handles (start and end) that allow the user to select a specific segment of the video.
- **Scrubber/Playhead:** A vertical line indicating the current playback position.
- **Time Indicators:** Real-time display of the start time, end time, and total duration of the selected clip.
- **Real-time Preview:** The video preview must update instantaneously while the user is dragging the handles or the playhead (scrubbing).

## Non-Functional Requirements
- **Performance:** Thumbnail generation and UI updates during scrubbing must be smooth (targeting 60fps).
- **Style:** Adhere to "Material Design 3 Expressive" principles, using bold shapes and the project's M3 color scheme.
- **Accessibility:** Ensure handles are easy to touch (minimum 48dp target) and provide visual feedback on interaction.

## Acceptance Criteria
- [ ] Users can drag start/end handles to define a trim range.
- [ ] Users can drag the playhead to seek through the video.
- [ ] The video player (Media3) stays perfectly synchronized with the playhead position during scrubbing.
- [ ] Thumbnails are correctly extracted and displayed without lagging the UI.
- [ ] UI reflects Material 3 Expressive aesthetics.

## Out of Scope
- Multi-track editing (this is a single-track trimmer).
- Advanced transitions or overlay effects within the timeline itself.
- Audio waveform visualization (to be considered for a future track).
