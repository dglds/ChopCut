# Implementation Plan - Trim Range Feature

## Phase 1: Data & State Management
- [x] Task: Update `TrimRangeData` Data Model [f244002]
    - [x] Add properties for validation (e.g., collision checks) if needed.
    - [x] Ensure it supports serialization/parcelling if necessary for state saving.
- [~] Task: Implement `TimelineViewModel` Logic
    - [ ] Create `List<TrimRangeData>` in `UiState`.
    - [ ] Implement `addRange(range: TrimRangeData)` with collision validation.
    - [ ] Implement `removeRange(rangeId: String)` logic.
    - [ ] Implement `validateScrollPosition(newPosition: Long)` to support the blocking requirement.
- [ ] Task: Unit Tests for ViewModel
    - [ ] Test adding non-overlapping ranges.
    - [ ] Test detecting collisions.
    - [ ] Test deletion logic.
- [ ] Task: Conductor - User Manual Verification 'Data & State Management' (Protocol in workflow.md)

## Phase 2: UI Implementation (Visuals)
- [ ] Task: Update `TimelinePlayer` & `TimelineRangesOverlay` Composable
    - [ ] Render the list of `TrimRangeData` overlays on top of the timeline track.
    - [ ] Implement the "Defining State" visual feedback (dynamic overlay resizing).
    - [ ] Add timestamp labels (t1, t2) to the overlays.
    - [ ] Add the "Active Segment Indicator" (pulse/border).
- [ ] Task: Implement FAB State Logic
    - [ ] Create a `FabState` enum (Add, SetEnd, Delete).
    - [ ] Logic to determine current state based on Playhead position relative to existing ranges.
    - [ ] Display appropriate Icon and Tooltip based on state.
- [ ] Task: Conductor - User Manual Verification 'UI Implementation (Visuals)' (Protocol in workflow.md)

## Phase 3: Interaction & Constraint Logic
- [ ] Task: Implement Scroll Blocking
    - [ ] Modify the `TimelinePlayer` scrollable state or `onScroll` callback.
    - [ ] Inject the `validateScrollPosition` check from ViewModel.
    - [ ] Prevent scroll delta if it would enter a collision zone while defining a range.
- [ ] Task: Integrate FAB Actions
    - [ ] Wire FAB clicks to `startRange`, `endRange`, and `deleteRange` ViewModel events.
- [ ] Task: Integration Testing
    - [ ] Verify scrolling is blocked correctly near existing ranges.
    - [ ] Verify ranges are visually accurate compared to the playhead time.
- [ ] Task: Conductor - User Manual Verification 'Interaction & Constraint Logic' (Protocol in workflow.md)
