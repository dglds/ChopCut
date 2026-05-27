import os
import sys
import json
from datetime import datetime

def log_error(task_name):
    root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    log_path = os.path.join(root_dir, "output.log")
    json_path = os.path.join(root_dir, "errors.json")
    
    # 1. Parse clean error message from output.log
    message = "Unknown error"
    if os.path.exists(log_path):
        with open(log_path, "r", encoding="utf-8", errors="ignore") as f:
            lines = f.readlines()
        
        # Look for Kotlin compile errors first (highly descriptive)
        kotlin_errors = []
        for line in lines:
            if "e: file://" in line or "e: " in line:
                kotlin_errors.append(line.strip())
        
        if kotlin_errors:
            message = "\n".join(kotlin_errors[:3]) # Limit to top 3 errors to keep JSON clean
        else:
            # Fallback to general Gradle failure block
            failure_lines = []
            capture = False
            for line in lines:
                if line.startswith("FAILURE:") or line.startswith("* What went wrong:"):
                    capture = True
                if capture:
                    failure_lines.append(line.strip())
                if line.startswith("BUILD FAILED") or len(failure_lines) > 10:
                    break
            if failure_lines:
                message = "\n".join(failure_lines)
    
    # 2. Read or initialize errors.json
    data = {"count": 0, "errors": []}
    if os.path.exists(json_path):
        try:
            with open(json_path, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            pass # If JSON is corrupt/empty, start fresh
            
    # 3. Create new error record with localized ISO timestamp
    now = datetime.now()
    timezone_offset = datetime.now().astimezone().strftime('%z')
    if len(timezone_offset) == 5:
        timezone_offset = timezone_offset[:3] + ":" + timezone_offset[3:]
    timestamp = now.strftime("%Y-%m-%dT%H:%M:%S") + timezone_offset
    
    new_error = {
        "task": task_name,
        "message": message,
        "timestamp": timestamp
    }
    
    data["errors"].append(new_error)
    data["count"] = len(data["errors"])
    
    # 4. Write pretty-printed JSON back
    with open(json_path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=4, ensure_ascii=False)
        
    print(f"Recorded failure for task '{task_name}' in errors.json (Total failures: {data['count']})")

if __name__ == "__main__":
    task = sys.argv[1] if len(sys.argv) > 1 else "assembleDebug"
    log_error(task)
