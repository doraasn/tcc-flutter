#!/bin/bash
# TCC APK Build Pipeline
# Builds the Flutter APK with all native dependencies
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()    { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
error()   { echo -e "${RED}[ERROR]${NC} $*" >&2; }
section() { echo -e "\n${CYAN}=== $* ===${NC}"; }

BUILD_START=$(date +%s)

# --- Pre-flight checks ---
section "Pre-flight Checks"

# Check Flutter SDK
if ! command -v flutter &>/dev/null; then
    error "Flutter SDK not found in PATH"
    echo "  Install Flutter: https://docs.flutter.dev/get-started/install"
    echo "  Or add flutter to PATH: export PATH=\"\$PATH:/path/to/flutter/bin\""
    exit 1
fi

FLUTTER_VERSION=$(flutter --version | head -1)
info "Flutter: $FLUTTER_VERSION"

# Check Java (required for Android builds)
if ! command -v java &>/dev/null; then
    error "Java not found. Install JDK 17+."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1)
info "Java: $JAVA_VERSION"

# Check Android SDK
if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]; then
    warn "ANDROID_HOME/ANDROID_SDK_ROOT not set"
fi

# Check proot binary exists
PROOT_BIN="$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a/proot-arm64"
if [ ! -f "$PROOT_BIN" ]; then
    warn "proot-arm64 not found at $PROOT_BIN"
    warn "Run scripts/build_proot.sh first, or the APK will lack proot"
fi

# Check rootfs exists
ROOTFS_TGZ="$PROJECT_ROOT/assets/core/rootfs.tgz"
if [ ! -f "$ROOTFS_TGZ" ]; then
    warn "rootfs.tgz not found at $ROOTFS_TGZ"
    warn "Run scripts/build_rootfs.sh first, or the APK will lack rootfs"
fi

# --- Flutter dependencies ---
section "Installing Dependencies"
info "Running flutter pub get..."
flutter pub get

# --- Code generation ---
section "Code Generation"
info "Running build_runner..."
flutter pub run build_runner build --delete-conflicting-outputs

# --- Build APK ---
section "Building Release APK"
info "Building APK (this may take several minutes)..."

FLUTTER_ARGS=(
    "build"
    "apk"
    "--release"
    "--tree-shake-icons"
)

# Add target platform filter for arm64 only
FLUTTER_ARGS+=("--target-platform=android-arm64")

flutter "${FLUTTER_ARGS[@]}"

# --- Locate APK ---
APK_PATH="$PROJECT_ROOT/build/app/outputs/flutter-apk/app-release.apk"
if [ ! -f "$APK_PATH" ]; then
    # Try alternative output location
    APK_PATH=$(find "$PROJECT_ROOT/build/app/outputs" -name "*.apk" -type f 2>/dev/null | head -1)
    if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
        error "APK not found after build"
        exit 1
    fi
fi

# --- Copy APK ---
section "APK Output"
TARGET_DIR="/sdcard/Download"
TARGET_PATH="$TARGET_DIR/TCC.apk"

# Ensure output filename is TCC.apk
FINAL_APK="$PROJECT_ROOT/build/app/outputs/flutter-apk/TCC.apk"
if [ "$APK_PATH" != "$FINAL_APK" ]; then
    cp "$APK_PATH" "$FINAL_APK"
    APK_PATH="$FINAL_APK"
fi

# Copy to Download directory if accessible
if [ -d "$TARGET_DIR" ]; then
    cp "$APK_PATH" "$TARGET_PATH"
    info "Copied to: $TARGET_PATH"
else
    warn "Download directory not accessible, skipping copy"
fi

# --- Report ---
BUILD_END=$(date +%s)
BUILD_TIME=$((BUILD_END - BUILD_START))
APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  Build Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo -e "  APK:    $APK_PATH"
if [ -d "$TARGET_DIR" ]; then
    echo -e "  Copy:   $TARGET_PATH"
fi
echo -e "  Size:   $APK_SIZE"
echo -e "  Time:   ${BUILD_TIME}s"
echo -e "${GREEN}============================================${NC}"

# --- Verify APK contents ---
if command -v unzip &>/dev/null; then
    echo ""
    info "APK contents verification:"
    echo "  Native libs:"
    unzip -l "$APK_PATH" 2>/dev/null | grep "\.so" | awk '{print "    " $NF}' || true
    echo "  Assets:"
    unzip -l "$APK_PATH" 2>/dev/null | grep "assets/" | awk '{print "    " $NF}' || true
fi
