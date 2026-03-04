import os
import re

directory = "app/src/main/java/com/chopcut"

# Files to skip (Timber implementations)
skip_files = [
    "FileLoggingTree.kt",
    "LocalFileLoggingTree.kt"
]

def refactor_file(filepath):
    if any(filepath.endswith(skip) for skip in skip_files):
        return

    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Pattern for Log.x(TAG, msg, exception) or Log.x(TAG, msg)
    # Group 1: method (d, v, e, w, i)
    # Group 2: tag
    # Group 3: message (and possibly exception)
    
    # Needs a more robust regex to handle multiple lines, but let's try a simple approach first.
    # Actually, using a simple regex could break on complex strings.
    # Let's iterate line by line to add imports if needed.
    
    modified = False
    
    # 1. Replace `android.util.Log.x("Tag", ...)`
    def replace_log(match):
        method = match.group(1)
        tag = match.group(2)
        args = match.group(3)
        
        # Check if args contain an exception at the end (e.g. "msg", e)
        # This is a bit tricky with regex, let's do a simple split if possible, but args can have commas.
        # A simpler way: just use Timber.tag("Tag").x(...) but remember Timber.e(e, "msg") is preferred.
        # However, Timber.tag("Tag").e("msg", e) is NOT valid? Wait, Timber uses `e(Throwable t, String message, Object... args)`.
        # Wait! Timber.tag("Tag").e(e, "msg") is valid. Timber.tag("Tag").e("msg") is valid.
        
        # Let's check the args. If it ends with `, e` or `, exception`
        parts = args.rsplit(',', 1)
        if len(parts) == 2 and not '"' in parts[1] and not ')' in parts[1]:
            msg = parts[0].strip()
            exc = parts[1].strip()
            return f'Timber.tag("{tag}").{method}({exc}, {msg})'
        else:
            return f'Timber.tag("{tag}").{method}({args})'

    pattern = r'(?:android\.util\.)?Log\.([vdiwe])\(\s*"([^"].*?)"\s*,\s*(.*?)\s*\)'
    
    # Replaces
    new_content, count = re.subn(pattern, replace_log, content, flags=re.DOTALL)
    
    if count > 0:
        modified = True
        content = new_content
        
    # Check for imports
    if modified and 'import timber.log.Timber' not in content:
        # insert after package declaration
        content = re.sub(r'^(package .*?)$', r'\1\n\nimport timber.log.Timber', content, count=1, flags=re.MULTILINE)
    
    # Remove android.util.Log import
    if modified:
        content = re.sub(r'^import android\.util\.Log\n', '', content, flags=re.MULTILINE)

    if modified:
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Refactored: {filepath}")

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith(".kt"):
            refactor_file(os.path.join(root, file))