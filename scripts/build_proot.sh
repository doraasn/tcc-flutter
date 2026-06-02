#!/bin/bash
# Cross-compile proot for Android arm64-v8a
# Requires: NDK, clang
set -eo pipefail

PROOT_VERSION="5.4.0"
NDK_PATH="${ANDROID_NDK_HOME:-$HOME/android-ndk}"
OUTPUT_DIR="$(cd "$(dirname "$0")" && pwd)/../android/app/src/main/jniLibs/arm64-v8a"

echo "=== Building proot $PROOT_VERSION for Android arm64 ==="

# Check NDK
if [ ! -d "$NDK_PATH" ]; then
    echo "Error: Android NDK not found at $NDK_PATH"
    echo "Set ANDROID_NDK_HOME or download NDK"
    exit 1
fi

# Setup toolchain
TOOLCHAIN="$NDK_PATH/toolchains/llvm/prebuilt/linux-x86_64"
CC="$TOOLCHAIN/bin/aarch64-linux-android35-clang"
CFLAGS="--static -O2 -D__ANDROID__=1"

# Download proot source
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo "Downloading proot source..."
cd "$TMPDIR"
git clone --depth 1 --branch v$PROOT_VERSION https://github.com/proot-me/proot.git 2>/dev/null || \
    git clone --depth 1 https://github.com/proot-me/proot.git

cd proot/src

# Patch for Android static build
cat > Makefile.android << 'MAKEFILE'
# Android static build for arm64
CC ?= aarch64-linux-android35-clang
CFLAGS += --static -O2
LDFLAGS += --static

PROOT_CFLAGS = $(CFLAGS) \
    -DPRoot_VERSION=\"5.4.0\" \
    -DPRoot_CONFETTI_PATH=\"\" \
    -I../src \
    -include config.h

OBJS = \
    cli.o \
    path.o \
    syscall.o \
    execve.o \
    main.o

all: proot

proot: $(OBJS)
	$(CC) $(LDFLAGS) -o $@ $^

%.o: %.c
	$(CC) $(PROOT_CFLAGS) -c -o $@ $<

clean:
	rm -f proot $(OBJS)
MAKEFILE

# Create minimal config.h
cat > config.h << 'CONFIG'
#define PRoot_VERSION "5.4.0"
#define PRoot_CONFETTI_PATH ""
CONFIG

echo "Compiling..."
make -f Makefile.android CC="$CC" CFLAGS="$CFLAGS" LDFLAGS="--static"

# Check result
if [ -f proot ]; then
    mkdir -p "$OUTPUT_DIR"
    cp proot "$OUTPUT_DIR/proot-arm64"
    chmod 755 "$OUTPUT_DIR/proot-arm64"
    echo "Success: $OUTPUT_DIR/proot-arm64"
    file "$OUTPUT_DIR/proot-arm64" 2>/dev/null || echo "Binary created"
    ls -lh "$OUTPUT_DIR/proot-arm64"
else
    echo "Build failed"
    exit 1
fi
