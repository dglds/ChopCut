# Implementation Plan - Trim Range Feature

## Phase 1: Data & State Management ✅ COMPLETED
- [x] Task: Update `TrimRangeData` Data Model [f244002]
    - [x] Add properties for validation (e.g., collision checks) if needed.
    - [x] Ensure it supports serialization/parcelling if necessary for state saving.
- [x] Task: Implement `TimelineViewModel` Logic
    - [x] Create `List<TrimRangeData>` in `UiState`.
    - [x] Implement `startRange()` / `endRange()` with collision validation.
    - [x] Implement `removeRange(rangeId: String)` logic.
    - [x] Implement `validateScrollPosition(newPosition: Long)` to support the blocking requirement.
    - [x] Implement `updateRangeEnd()` for dynamic range resizing.
- [x] Task: Unit Tests for ViewModel (13 tests passing)
    - [x] Test adding non-overlapping ranges.
    - [x] Test detecting collisions.
    - [x] Test deletion logic.
    - [x] Test endMs greater than startMs scenarios.
    - [x] Test validateScrollPosition blocking logic.
- [ ] Task: Conductor - User Manual Verification 'Data & State Management' (Protocol in workflow.md)

## Phase 2: UI Implementation (Visuals) 🚧 IN PROGRESS
- [x] Task: Create `TrimEditionScreen` Composable
    - [x] Replace EditorScreen route with TrimEditionScreen.
    - [x] Integrate TimelinePlayer with ExoPlayer.
    - [x] Load video from project when projectId is provided.
- [x] Task: Update `TimelinePlayer` & `TimelineRangesOverlay` Composable
    - [x] Render the list of `TrimRangeData` overlays on top of the timeline track.
    - [ ] Implement the "Defining State" visual feedback (dynamic overlay resizing with playhead).
    - [ ] Add timestamp labels (t1, t2) to the overlays.
    - [ ] Add the "Active Segment Indicator" (pulse/border animation).
- [x] Task: Implement FAB State Logic
    - [x] Create FAB states: Add (➕), Confirm (✓), Delete (🗑️).
    - [x] Logic to determine current state based on selection and defining mode.
    - [x] Display appropriate Icon based on state.
- [ ] Task: Conductor - User Manual Verification 'UI Implementation (Visuals)' (Protocol in workflow.md)

## Phase 3: Interaction & Constraint Logic 📋 PENDING
- [ ] Task: Implement Scroll Blocking
    - [ ] Modify the `TimelinePlayer` scrollable state or `onScroll` callback.
    - [ ] Inject the `validateScrollPosition` check from ViewModel.
    - [ ] Prevent scroll delta if it would enter a collision zone while defining a range.
- [x] Task: Integrate FAB Actions
    - [x] Wire FAB clicks to `startRange`, `endRange`, and `deleteRange` ViewModel events.
- [ ] Task: Integration Testing
    - [ ] Verify scrolling is blocked correctly near existing ranges.
    - [ ] Verify ranges are visually accurate compared to the playhead time.
- [ ] Task: Conductor - User Manual Verification 'Interaction & Constraint Logic' (Protocol in workflow.md)

## Phase 4: Deployment Automation ✅ COMPLETED
- [x] Task: Create Gradle deployment tasks
    - [x] Create `gradle/scripts/deploy.sh` - Build, install, kill, restart.
    - [x] Create `gradle/scripts/install.sh` - Install only.
    - [x] Create `gradle/scripts/wifi.sh` - WiFi connection.
    - [x] Task `:app:deploy` - Full deployment automation.
    - [x] Task `:app:install` - Quick install.
    - [x] Task `:app:wifi` - WiFi debugging connection.

## Next Steps
1. **Priority High**: Implement "Defining State" visual feedback (range follows playhead during definition)
2. **Priority High**: Integrate scroll blocking with TimelinePlayer
3. **Priority Medium**: Add timestamp labels (t1, t2) on ranges
4. **Priority Medium**: Add Active Segment Indicator animations
