import os
import re
import glob

BASE_DIR = "app/src/main/java/com/chopcut"

GROUPS = {
    "core/Models.kt": [
        "data/model/*.kt",
        "data/audio/model/*.kt"
    ],
    "core/Utils.kt": [
        "util/*.kt",
        "util/debug/*.kt",
        "util/logging/*.kt"
    ],
    "core/Theme.kt": [
        "ui/theme/*.kt"
    ],
    "core/Errors.kt": [
        "util/error/*.kt"
    ],
    "data/VideoEngine.kt": [
        "data/pipeline/*.kt",
        "data/repository/*.kt",
        "data/codec/*.kt"
    ],
    "data/ThumbnailEngine.kt": [
        "data/thumbnail/*.kt",
        "data/thumbnail/v3/*.kt"
    ],
    "data/AudioEngine.kt": [
        "data/audio/*.kt"
    ],
    "ui/SharedComponents.kt": [
        "ui/components/atoms/*.kt",
        "ui/components/buttons/*.kt",
        "ui/components/cards/*.kt",
        "ui/components/loading/*.kt",
        "ui/components/layout/*.kt"
    ],
    "ui/home/HomeFeature.kt": [
        "ui/screen/HomeScreen.kt",
        "ui/viewmodel/HomeViewModel.kt",
        "ui/viewmodel/HomeViewModelFactory.kt",
        "ui/components/gallery/*.kt",
        "ui/viewmodel/PreloadUiState.kt",
        "ui/viewmodel/PreloadViewModel.kt"
    ],
    "ui/editor/TimelineUI.kt": [
        "ui/components/timeline/*.kt",
        "ui/components/player/*.kt",
        "ui/viewmodel/VideoTimelineViewModel.kt"
    ],
    "ui/editor/TrimUI.kt": [
        "ui/components/trim/*.kt"
    ],
    "ui/editor/WaveformUI.kt": [
        "ui/components/waveform/*.kt"
    ],
    "ui/editor/EditorToolsUI.kt": [
        "ui/components/editor/tools/*.kt",
        "ui/components/tools/*.kt",
        "ui/state/*.kt"
    ],
    "ui/editor/EditorFeature.kt": [
        "ui/screen/EditorScreen.kt",
        "ui/viewmodel/EditorViewModel.kt",
        "ui/viewmodel/EditorState.kt",
        "ui/viewmodel/AudioViewModel.kt",
        "ui/viewmodel/ThumbnailViewModel.kt",
        "config/constants/*.kt"
    ],
}

def resolve_globs(patterns):
    files = []
    for pattern in patterns:
        full_pattern = os.path.join(BASE_DIR, pattern)
        # Using recursive glob if needed, though simple glob is enough here
        matched = glob.glob(full_pattern)
        for f in matched:
            if os.path.isfile(f) and f not in files:
                files.append(f)
    return files

def merge_group(target_rel_path, source_patterns):
    source_files = resolve_globs(source_patterns)
    if not source_files:
        return

    all_imports = set()
    all_body = []
    
    for file_path in source_files:
        with open(file_path, "r", encoding="utf-8") as f:
            lines = f.readlines()
        
        body_lines = []
        for line in lines:
            line_stripped = line.strip()
            # Skip package declarations
            if line_stripped.startswith("package "):
                continue
            # Handle imports
            elif line_stripped.startswith("import "):
                # Skip internal imports since everything will be in com.chopcut
                if not line_stripped.startswith("import com.chopcut."):
                    all_imports.add(line_stripped)
            else:
                body_lines.append(line)
                
        all_body.append("\n// --- Merged from {} ---\n".format(os.path.basename(file_path)))
        all_body.append("".join(body_lines))

    # Create target directory
    target_abs_path = os.path.join(BASE_DIR, target_rel_path)
    os.makedirs(os.path.dirname(target_abs_path), exist_ok=True)
    
    # Write merged file
    with open(target_abs_path, "w", encoding="utf-8") as f:
        f.write("package com.chopcut\n\n")
        # Sort imports for neatness
        for imp in sorted(all_imports):
            f.write(imp + "\n")
        f.write("\n")
        f.write("".join(all_body))

    # Delete source files
    for file_path in source_files:
        if file_path != target_abs_path: # safety check
            os.remove(file_path)

def refactor():
    for target, patterns in GROUPS.items():
        print(f"Merging into {target}...")
        merge_group(target, patterns)

    # Note: ChopCutApplication.kt, MainActivity.kt, ChopCutNavGraph.kt remain in BASE_DIR.
    # We must update their packages and imports as well.
    standalone_files = [
        "ChopCutApplication.kt",
        "MainActivity.kt",
        "graphics/gl/GLRenderer.kt",
        "graphics/egl/SurfaceBridge.kt",
        "ui/navigation/ChopCutNavGraph.kt"
    ]
    
    for sf in standalone_files:
        path = os.path.join(BASE_DIR, sf)
        if os.path.exists(path):
            with open(path, "r", encoding="utf-8") as f:
                content = f.read()
            # Change package to com.chopcut
            content = re.sub(r'package com\.chopcut\..*', 'package com.chopcut', content)
            # Remove internal imports
            content = re.sub(r'import com\.chopcut\.[a-zA-Z0-9_.]+\n', '', content)
            with open(path, "w", encoding="utf-8") as f:
                f.write(content)
                
    print("Refactoring complete.")

if __name__ == "__main__":
    refactor()
