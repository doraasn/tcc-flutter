#!/bin/bash
set -e

echo "=== TCC APK Build Script ==="

# Check Flutter
if ! command -v flutter &> /dev/null; then
    echo "Error: Flutter not found"
    echo "Install Flutter SDK first"
    exit 1
fi

# Get dependencies
echo "Getting dependencies..."
flutter pub get

# Run code generation
echo "Running code generation..."
flutter pub run build_runner build --delete-conflicting-outputs

# Build APK
echo "Building APK..."
flutter build apk --release

# Copy APK
APK_PATH="build/app/outputs/flutter-apk/app-release.apk"
TARGET_PATH="/sdcard/Download/TCC-Flutter.apk"

if [ -f "$APK_PATH" ]; then
    cp "$APK_PATH" "$TARGET_PATH"
    echo "APK built successfully: $TARGET_PATH"
    echo "Size: $(ls -lh "$TARGET_PATH" | awk '{print $5}')"
else
    echo "Error: APK not found at $APK_PATH"
    exit 1
fi

echo "=== Build Complete ==="
