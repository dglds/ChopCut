# Specification: Timelinev4 Implementation

## Overview
Implement a new version of the project's timeline component, named **Timelinev4**. The primary goal is to provide a high-performance scrubbing experience with real-time feedback, ensuring both visual fluidity and frame precision within the ChopCut video editing ecosystem.

## Functional Requirements
- **High-Performance Scrubbing:** Support dragging the playhead with smooth, 60fps interaction.
- **Hybrid Rendering strategy:**
    - Use fast, low-resolution previews or pre-loaded thumbnails during rapid scrubbing to maintain performance.
    - Transition to absolute frame precision (rendering the exact high-quality frame) when the movement slows down or stops.
- **Visual Elements:**
    - **Timecode Display:** Real-time update of the current timestamp (00:00:00.00) at the playhead position.
    - **Filmstrip View:** Background visualization of video clips using a sequence of thumbnails.
- **Synchronization:** Integration via a **Single Source of Truth** (Shared ViewModel/State) to ensure the Timeline and Player are always perfectly aligned.

## Non-Functional Requirements
- **Performance:** Interaction must remain fluid without UI jank, even with long video clips.
- **Reliability:** Accurate frame seeking and synchronization between visual timeline markers and video frames.

## Acceptance Criteria
- [ ] User can scrub through the timeline with a 60fps experience.
- [ ] Timeline displays accurate timecode and filmstrip thumbnails.
- [ ] Scrubbing accurately updates the video player frame.
- [ ] Stopping the scrub renders the exact high-quality frame.
- [ ] Timeline and Player state are managed through a single source of truth.

## Out of Scope
- Multi-layer support (at this stage).
- Direct clip editing (trimming, dragging clips) – focus is on scrubbing and display.
- Audio waveform visualization.
