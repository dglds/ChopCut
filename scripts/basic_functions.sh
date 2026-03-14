#!/bin/bash

# ============================================================================
# CHOPCUT BASIC FUNCTIONS
# ============================================================================
# Funções básicas para uso diário no projeto ChopCut
# ============================================================================

# Cores
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ============================================================================
# BUILD FUNCTIONS
# ============================================================================

# Build debug APK
build_debug() {
    echo -e "${BLUE}📦 Building debug APK...${NC}"
    ./gradlew assembleDebug
}

# Install debug APK
install_debug() {
    echo -e "${BLUE}📱 Installing debug APK...${NC}"
    ./gradlew installDebug
}

# Build e install
build_install() {
    build_debug && install_debug
}

# ============================================================================
# CLEAN FUNCTIONS
# ============================================================================

# Clean local build
clean_local() {
    echo -e "${YELLOW}🧹 Cleaning local build...${NC}"
    ./gradlew clean cleanBuildCache
}

# Clean everything (virgin build)
clean_all() {
    echo -e "${RED}🧹 Cleaning EVERYTHING...${NC}"
    ./gradlew clean cleanBuildCache
    rm -rf ~/.gradle/caches
    rm -rf build
}

# ============================================================================
# DEVICE FUNCTIONS
# ============================================================================

# Check if device is connected
check_device() {
    echo -e "${BLUE}🔍 Checking device...${NC}"
    adb devices
}

# Clear app data
clear_app_data() {
    echo -e "${YELLOW}🗑️  Clearing app data...${NC}"
    adb shell pm clear com.chopcut
}

# Uninstall app
uninstall_app() {
    echo -e "${RED}❌ Uninstalling app...${NC}"
    adb uninstall com.chopcut
}

# Reinstall app
reinstall_app() {
    echo -e "${YELLOW}🔄 Reinstalling app...${NC}"
    uninstall_app
    install_debug
}

# ============================================================================
# LOG FUNCTIONS
# ============================================================================

# Show app logs
show_logs() {
    echo -e "${BLUE}📋 Showing app logs...${NC}"
    local pid=$(adb shell pidof -s com.chopcut)
    if [ -n "$pid" ]; then
        adb logcat --pid=$pid -v time
    else
        echo -e "${RED}❌ App not running${NC}"
    fi
}

# Clear logcat
clear_logs() {
    echo -e "${YELLOW}🧹 Clearing logcat...${NC}"
    adb logcat -c
}

# Show recent logs (100 lines)
show_recent_logs() {
    echo -e "${BLUE}📋 Recent logs (100 lines)...${NC}"
    local pid=$(adb shell pidof -s com.chopcut)
    if [ -n "$pid" ]; then
        adb logcat -d -t 100 --pid=$pid -v time
    else
        echo -e "${RED}❌ App not running${NC}"
    fi
}

# Show error logs
show_errors() {
    echo -e "${RED}🔥 Error logs...${NC}"
    local pid=$(adb shell pidof -s com.chopcut)
    if [ -n "$pid" ]; then
        adb logcat --pid=$pid -v time "*:E"
    else
        echo -e "${RED}❌ App not running${NC}"
    fi
}

# ============================================================================
# SCREENSHOT/RECORDER
# ============================================================================

# Take screenshot
screenshot() {
    local filename="screenshot_$(date +%Y%m%d_%H%M%S).png"
    echo -e "${BLUE}📸 Taking screenshot...${NC}"
    adb exec-out screencap -p > "$filename"
    echo -e "${GREEN}✅ Saved: $filename${NC}"
}

# Start screen recording
start_record() {
    local filename="screenrecord_$(date +%Y%m%d_%H%M%S).mp4"
    echo -e "${BLUE}🎬 Recording screen...${NC}"
    echo -e "${YELLOW}Press Ctrl+C to stop${NC}"
    adb shell screenrecord /sdcard/$filename --time-limit 180
    adb pull /sdcard/$filename
    adb shell rm /sdcard/$filename
    echo -e "${GREEN}✅ Saved: $filename${NC}"
}

# ============================================================================
# GIT FUNCTIONS
# ============================================================================

# Show git status
git_status() {
    git status
}

# Show git diff
git_diff() {
    git diff
}

# Quick commit
git_commit() {
    local message="$1"
    if [ -z "$message" ]; then
        echo -e "${RED}❌ Usage: git_commit \"message\"${NC}"
        return 1
    fi
    git add .
    git commit -m "$message"
}

# ============================================================================
# HELP
# ============================================================================

# Show all functions
help() {
    echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║       CHOPCUT BASIC FUNCTIONS                             ║${NC}"
    echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${BLUE}BUILD:${NC}"
    echo "  build_debug      - Build debug APK"
    echo "  install_debug    - Install debug APK"
    echo "  build_install    - Build + install"
    echo ""
    echo -e "${BLUE}CLEAN:${NC}"
    echo "  clean_local      - Clean local build"
    echo "  clean_all        - Clean everything (virgin build)"
    echo ""
    echo -e "${BLUE}DEVICE:${NC}"
    echo "  check_device     - Check device connection"
    echo "  clear_app_data   - Clear app data"
    echo "  uninstall_app    - Uninstall app"
    echo "  reinstall_app    - Reinstall app"
    echo ""
    echo -e "${BLUE}LOGS:${NC}"
    echo "  show_logs        - Show app logs"
    echo "  clear_logs       - Clear logcat"
    echo "  show_recent_logs - Show 100 recent logs"
    echo "  show_errors      - Show error logs"
    echo ""
    echo -e "${BLUE}SCREEN:${NC}"
    echo "  screenshot       - Take screenshot"
    echo "  start_record     - Record screen (max 3 min)"
    echo ""
    echo -e "${BLUE}GIT:${NC}"
    echo "  git_status       - Show git status"
    echo "  git_diff         - Show git diff"
    echo "  git_commit       - Quick commit (git_commit \"message\")"
    echo ""
    echo -e "${YELLOW}Usage: source scripts/basic_functions.sh${NC}"
}

# Run help if script is executed directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    help
fi
