# Implementation Plan - Editor Split UI with Integrated Tool Controls

This plan outlines the refactoring of the `EditorScreen` to support a split UI where tool controls are integrated into a dynamic bottom panel, and the waveform is moved to the top timeline area.

## Phase 1: Layout & Core Scaffolding
- [ ] Task: Create a dedicated `ToolPanelContainer` component to house integrated tool controls.
- [ ] Task: Refactor `EditorScreen.kt` layout to use a weighted column (Top: 60%, Bottom: 40% aproximadamente) for split view.
- [ ] Task: Move `WaveForm` component from the main scrollable list to the fixed `Top Area` integrated with `VideoTimeline`.
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Layout & Core Scaffolding' (Protocol in workflow.md)

## Phase 2: Tool Integration & Navigation
- [ ] Task: Refactor `TrimContent` to remove standalone dialog logic and support the integrated panel.
- [ ] Task: Refactor `FilterContent` to support the integrated panel and ensure "Apply" logic remains functional with Undo/Redo.
- [ ] Task: Refactor `AudioControlContent` to support the integrated panel and remove redundant "Cancel" buttons.
- [ ] Task: Update `EditorControlsPanel` to manage navigation between tools using the persistent Bottom Bar buttons.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Tool Integration & Navigation' (Protocol in workflow.md)

## Phase 3: UX Refinement & Undo Integration
- [ ] Task: Verify that all "Apply" actions in tool panels correctly register operations in the `EditorViewModel` history for Undo/Redo.
- [ ] Task: Implement smooth transition animations between different tool panels in the dynamic area.
- [ ] Task: Add a "Clear Selection" state to the dynamic area when no tool is active (showing a placeholder or empty space).
- [ ] Task: Conductor - User Manual Verification 'Phase 3: UX Refinement & Undo Integration' (Protocol in workflow.md)
