#!/bin/bash
# Simple proot setup - download pre-built binary or use Termux's proot
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a"

echo "=== Setting up proot for TCC ==="

mkdir -p "$OUTPUT_DIR"

# Option 1: Use Termux's proot (if available)
TERMUX_PROOT="/data/data/com.termux/files/usr/bin/proot"
if [ -x "$TERMUX_PROOT" ]; then
    echo "Using Termux's proot: $TERMUX_PROOT"
    cp "$TERMUX_PROOT" "$OUTPUT_DIR/proot-arm64"
    chmod 755 "$OUTPUT_DIR/proot-arm64"
    echo "Success: proot-arm64 copied from Termux"
    ls -lh "$OUTPUT_DIR/proot-arm64"
    exit 0
fi

# Option 2: Download pre-built proot from GitHub
echo "Termux proot not found, downloading pre-built..."
PROOT_URL="https://github.com/proot-me/proot/releases/download/v5.4.0/proot-v5.4.0-arm64"
if curl -fsSL -o "$OUTPUT_DIR/proot-arm64" "$PROOT_URL" 2>/dev/null; then
    chmod 755 "$OUTPUT_DIR/proot-arm64"
    echo "Success: proot-arm64 downloaded"
    ls -lh "$OUTPUT_DIR/proot-arm64"
    exit 0
fi

echo "Failed to get proot binary"
exit 1
