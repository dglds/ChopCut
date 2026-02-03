# Specification: Trim Range Feature (Multi-segment Video Cutting)

## Overview
Implement the ability for users to mark multiple segments (ranges) of a video for removal. This feature enhances the `TimelinePlayer` component (in `app/src/main/java/com/chopcut/ui/components/`) by allowing non-linear editing through a simple "Start/End" marking system driven by a floating action button (FAB).

## Functional Requirements

### 1. Range Management
- **State Storage:** Trim ranges MUST be stored in `TimelineViewModel` as a `List<TrimRangeData>`.
- **Definition Workflow:** 
    - The FAB starts a range (Mark A) at the current playhead position.
    - A second click on the FAB sets the end point (Mark B) at the current playhead position.
    - The range is defined by [min(A, B), max(A, B)].
- **Collision Prevention:** 
    - Ranges MUST NOT overlap. 
    - While defining a range (Mark A set), scrolling is physically blocked if it would cause the new interval to collide with an existing range.
- **Deletion:**
    - If the playhead is inside an existing range, the FAB toggles to a "Delete" state (e.g., trash icon).
    - Clicking the FAB in this state removes the specific range.

### 2. UI/UX & Visuals
- **Range Overlay:** 
    - Semi-transparent colored overlay indicating the segments to be removed.
    - Displays timestamps (t1, t2) at the bottom edges in a small, monospaced font.
- **"Defining" State:**
    - While a range is being defined (Mark A set but not Mark B), the overlay dynamically follows the playhead.
    - Visual feedback includes an active segment indicator (e.g., animated pulse or border).
    - A tooltip near the FAB provides guidance: "Click to set End".
- **Dynamic FAB:**
    - Default: "Add Range" (Plus icon).
    - Defining: "Set End" (Check or Finish icon).
    - Inside existing range: "Delete Range" (Trash icon).

## Non-Functional Requirements
- **Performance:** Dynamic overlay resizing during scroll must be smooth (60fps).
- **Architecture:** Follow MVVM patterns and maintain the `TimelinePlayer`'s "Timeline Drives Player" core behavior.

## Acceptance Criteria
- [ ] Users can add multiple non-overlapping ranges.
- [ ] Scrolling is blocked to prevent range overlap during definition.
- [ ] Users can delete a range by positioning the playhead over it and clicking the FAB.
- [ ] Timestamps are visible and accurate on all ranges.
- [ ] The feature integrates with the existing `TimelineViewModel` and `TimelinePlayer` component without breaking current functionality.

## Out of Scope
- Actual video processing (transcoding/exporting) using these ranges (this is a UI/State management track).
- Undoing range deletions (future track).
