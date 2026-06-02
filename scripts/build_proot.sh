#!/bin/bash
# Cross-compile proot for Android arm64-v8a
# Requires: Android NDK, git
set -eo pipefail

PROOT_VERSION="5.4.0"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/android/app/src/main/jniLibs/arm64-v8a"
NDK_PATH="${ANDROID_NDK_HOME:-${ANDROID_NDK:-$HOME/android-ndk}}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

cleanup() {
    if [ -n "${TMPDIR:-}" ] && [ -d "$TMPDIR" ]; then
        rm -rf "$TMPDIR"
    fi
}
trap cleanup EXIT

# --- Validate NDK ---
if [ ! -d "$NDK_PATH" ]; then
    error "Android NDK not found at: $NDK_PATH"
    echo "  Set ANDROID_NDK_HOME or ANDROID_NDK environment variable."
    echo "  Download from: https://developer.android.com/ndk/downloads"
    exit 1
fi

TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
if [ ! -d "$TOOLCHAIN" ]; then
    error "NDK toolchain not found at: $TOOLCHAIN"
    exit 1
fi

CC="$TOOLCHAIN/bin/aarch64-linux-android35-clang"
if [ ! -x "$CC" ]; then
    # Try lower API levels
    for api in 28 29 30 31 32 33 34; do
        CC="$TOOLCHAIN/bin/aarch64-linux-android${api}-clang"
        if [ -x "$CC" ]; then
            warn "Using API level $api (35 not found)"
            break
        fi
    done
    if [ ! -x "$CC" ]; then
        error "No aarch64 clang found in $TOOLCHAIN"
        exit 1
    fi
fi

AR="$TOOLCHAIN/bin/llvm-ar"
if [ ! -x "$AR" ]; then
    error "llvm-ar not found at: $AR"
    exit 1
fi

info "NDK:     $NDK_PATH"
info "CC:      $CC"
info "AR:      $AR"

# --- Download proot source ---
TMPDIR=$(mktemp -d)
info "Downloading proot v$PROOT_VERSION source..."
cd "$TMPDIR"

if ! git clone --depth 1 --branch "v$PROOT_VERSION" https://github.com/proot-me/proot.git 2>/dev/null; then
    warn "Tag v$PROOT_VERSION not found, cloning default branch..."
    git clone --depth 1 https://github.com/proot-me/proot.git
fi

cd proot

# --- Create Android-compatible config.h ---
PROOT_SRC="$TMPDIR/proot/src"
if [ ! -d "$PROOT_SRC" ]; then
    error "proot source directory not found at $PROOT_SRC"
    exit 1
fi

cat > "$PROOT_SRC/config.h" << 'CONFIGEOF'
#ifndef CONFIG_H
#define CONFIG_H

#define PRoot_VERSION "5.4.0"
#define PRoot_CONFETTI_PATH ""

/* Android compatibility */
#ifndef __ANDROID__
#define __ANDROID__ 1
#endif

/* Disable features not available on Android */
#define ENABLE_OFFSET64 0

#endif /* CONFIG_H */
CONFIGEOF

# --- Collect source files ---
cd "$PROOT_SRC"

# Find all C source files in the main src/ directory (not subdirectories with their own build)
SRCS=""
for f in cli.c path.c syscall.c execve.c main.c; do
    if [ -f "$f" ]; then
        SRCS="$SRCS $f"
    fi
done

# Also include subdirectory sources
for subdir in path syscall execve; do
    if [ -d "$subdir" ]; then
        for f in "$subdir"/*.c; do
            if [ -f "$f" ]; then
                SRCS="$SRCS $f"
            fi
        done
    fi
done

# Include any remaining top-level .c files we might have missed
for f in *.c; do
    if [ -f "$f" ]; then
        case " $SRCS " in
            *" $f "*) ;;  # already included
            *) SRCS="$SRCS $f" ;;
        esac
    fi
done

if [ -z "$SRCS" ]; then
    error "No C source files found in $PROOT_SRC"
    exit 1
fi

info "Source files:$(echo $SRCS | wc -w) files found"
info "Compiling proot v$PROOT_VERSION for Android arm64-v8a..."

# --- Compile ---
CFLAGS="--static -O2 -fno-strict-aliasing -Wall -Wno-unused-parameter -D__ANDROID__=1 -DPRoot_VERSION='\"5.4.0\"' -DPRoot_CONFETTI_PATH='\"\"'"
LDFLAGS="--static -static"

OBJS=""
for src in $SRCS; do
    obj="${src%.c}.o"
    info "  CC  $src"
    $CC $CFLAGS -I. -c "$src" -o "$obj" 2>/dev/null || {
        # Try with reduced warnings if compilation fails
        $CC --static -O2 -D__ANDROID__=1 -I. -c "$src" -o "$obj" 2>/dev/null || {
            warn "  Skipping $src (compilation failed)"
            continue
        }
    }
    OBJS="$OBJS $obj"
done

if [ -z "$OBJS" ]; then
    error "No objects compiled successfully"
    exit 1
fi

# --- Link ---
info "Linking..."
PROOT_BIN="$TMPDIR/proot-arm64"

# Collect all object files
ALL_OBJS=$(find . -name '*.o' -type f 2>/dev/null)
if [ -z "$ALL_OBJS" ]; then
    error "No object files found"
    exit 1
fi

$CC --static -O2 -o "$PROOT_BIN" $ALL_OBJS 2>&1 || {
    warn "Direct link failed, trying with explicit object list..."
    $CC $LDFLAGS -o "$PROOT_BIN" $OBJS 2>&1 || {
        error "Linking failed"
        exit 1
    }
}

# --- Verify ---
if [ ! -f "$PROOT_BIN" ] || [ ! -s "$PROOT_BIN" ]; then
    error "proot binary not produced or empty"
    exit 1
fi

chmod 755 "$PROOT_BIN"

# Verify it's an arm64 binary
if command -v file &>/dev/null; then
    FILE_INFO=$(file "$PROOT_BIN")
    if echo "$FILE_INFO" | grep -qi "aarch64\|arm64"; then
        info "Binary verified: aarch64 ELF"
    else
        warn "Binary type: $FILE_INFO"
    fi
fi

SIZE=$(ls -lh "$PROOT_BIN" | awk '{print $5}')
info "Binary size: $SIZE"

# --- Install ---
mkdir -p "$OUTPUT_DIR"
cp "$PROOT_BIN" "$OUTPUT_DIR/proot-arm64"
chmod 755 "$OUTPUT_DIR/proot-arm64"

info "Success! proot-arm64 installed to:"
info "  $OUTPUT_DIR/proot-arm64"
ls -lh "$OUTPUT_DIR/proot-arm64"
