#!/bin/bash
# Build Alpine Linux rootfs for TCC
# Creates a minimal Alpine environment with Node.js, npm, git, and Claude Code
# Requires: proot (host), curl, gzip
set -eo pipefail

ALPINE_VERSION="3.19"
ALPINE_PATCH="7"
ALPINE_ARCH="aarch64"
CLAUDE_VERSION="2.1.153"
CLAUDE_PACKAGE="@anthropic-ai/claude-code"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
OUTPUT_DIR="$PROJECT_ROOT/assets/core"
PROOT_BIN="${PROOT_BIN:-proot}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

TMPDIR=""
cleanup() {
    if [ -n "${TMPDIR:-}" ] && [ -d "$TMPDIR" ]; then
        info "Cleaning up temporary files..."
        rm -rf "$TMPDIR"
    fi
}
trap cleanup EXIT

TMPDIR=$(mktemp -d)

# --- Check dependencies ---
info "Checking dependencies..."

if ! command -v proot &>/dev/null && [ ! -x "$PROOT_BIN" ]; then
    error "proot not found. Install proot first:"
    echo "  pkg install proot    (Termux)"
    echo "  apt install proot    (Debian/Ubuntu)"
    echo "  Or set PROOT_BIN=/path/to/proot"
    exit 1
fi

# Use host proot directly (ARM host can run aarch64 binaries natively)
HOST_PROOT="$(command -v proot 2>/dev/null || echo "$PROOT_BIN")"

if ! command -v curl &>/dev/null; then
    error "curl not found. Install curl first."
    exit 1
fi

if ! command -v gzip &>/dev/null; then
    error "gzip not found. Install gzip first."
    exit 1
fi

# Detect host architecture to determine if QEMU is needed
HOST_ARCH=$(uname -m)
NEED_QEMU=0
if [ "$HOST_ARCH" != "aarch64" ] && [ "$HOST_ARCH" != "arm64" ]; then
    warn "Host is $HOST_ARCH, QEMU required for aarch64 rootfs setup"
    if command -v qemu-aarch64-static &>/dev/null; then
        NEED_QEMU=1
        QEMU_BIN="$(command -v qemu-aarch64-static)"
    elif command -v qemu-aarch64 &>/dev/null; then
        NEED_QEMU=1
        QEMU_BIN="$(command -v qemu-aarch64)"
    else
        error "qemu-aarch64-static not found. Install qemu-user-static."
        exit 1
    fi
fi

# --- Download Alpine minirootfs ---
info "Downloading Alpine $ALPINE_VERSION ($ALPINE_ARCH) minirootfs..."
ROOTFS_TAR="$TMPDIR/alpine-minirootfs.tar.gz"
ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/releases/${ALPINE_ARCH}/alpine-minirootfs-${ALPINE_VERSION}.${ALPINE_PATCH}-${ALPINE_ARCH}.tar.gz"

if ! curl -fsSL --retry 3 --retry-delay 5 -o "$ROOTFS_TAR" "$ALPINE_URL"; then
    # Try without patch version
    ALPINE_URL="https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/releases/${ALPINE_ARCH}/alpine-minirootfs-${ALPINE_VERSION}.0-${ALPINE_ARCH}.tar.gz"
    if ! curl -fsSL --retry 3 --retry-delay 5 -o "$ROOTFS_TAR" "$ALPINE_URL"; then
        error "Failed to download Alpine minirootfs"
        exit 1
    fi
fi

info "Downloaded: $(ls -lh "$ROOTFS_TAR" | awk '{print $5}')"

# --- Extract rootfs ---
ROOTFS="$TMPDIR/rootfs"
mkdir -p "$ROOTFS"
info "Extracting minirootfs..."
tar xzf "$ROOTFS_TAR" -C "$ROOTFS"

# Copy QEMU binary into rootfs if needed
if [ "$NEED_QEMU" -eq 1 ]; then
    info "Copying QEMU binary into rootfs..."
    mkdir -p "$ROOTFS/usr/bin"
    cp "$QEMU_BIN" "$ROOTFS/usr/bin/qemu-aarch64-static"
fi

# --- Setup DNS configuration ---
info "Configuring DNS..."
mkdir -p "$ROOTFS/etc"

cat > "$ROOTFS/etc/resolv.conf" << 'EOF'
nameserver 8.8.8.8
nameserver 8.8.4.4
nameserver 1.1.1.1
EOF

cat > "$ROOTFS/etc/hosts" << 'EOF'
127.0.0.1 localhost
::1       localhost
127.0.1.1 alpine
EOF

# --- Setup proot bind mounts ---
PROOT_ARGS="-r $ROOTFS -b /dev -b /proc -b /sys -w /root -S"

run_in_rootfs() {
    # shellcheck disable=SC2086
    $HOST_PROOT $PROOT_ARGS /bin/sh -c "$1"
}

# --- Configure APK repositories ---
info "Configuring APK repositories..."
run_in_rootfs "echo 'https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/main' > /etc/apk/repositories"
run_in_rootfs "echo 'https://dl-cdn.alpinelinux.org/alpine/v${ALPINE_VERSION}/community' >> /etc/apk/repositories"

# --- Initialize APK and install base packages ---
info "Installing base packages (bash, nodejs, npm, git, curl)..."
run_in_rootfs "apk update"
run_in_rootfs "apk add --no-cache bash nodejs npm git curl ca-certificates"

# Verify installations
info "Verifying installations..."
run_in_rootfs "bash --version" | head -1 || warn "bash not installed"
run_in_rootfs "node --version" || warn "node not installed"
run_in_rootfs "npm --version" || warn "npm not installed"
run_in_rootfs "git --version" || warn "git not installed"

# --- Install Claude Code ---
info "Installing Claude Code $CLAUDE_VERSION..."
VERSION_DIR="/root/.tcc/versions/v${CLAUDE_VERSION}"
run_in_rootfs "mkdir -p $VERSION_DIR"
run_in_rootfs "npm install -g ${CLAUDE_PACKAGE}@${CLAUDE_VERSION} --prefix $VERSION_DIR 2>&1" || {
    warn "npm install failed, retrying with --unsafe-perm..."
    run_in_rootfs "npm install -g ${CLAUDE_PACKAGE}@${CLAUDE_VERSION} --prefix $VERSION_DIR --unsafe-perm 2>&1" || {
        error "Failed to install Claude Code $CLAUDE_VERSION"
        exit 1
    }
}

# Create current symlink
info "Setting up version symlink..."
run_in_rootfs "cd /root/.tcc/versions && ln -sf v${CLAUDE_VERSION} current"

# Verify Claude Code installation
run_in_rootfs "ls /root/.tcc/versions/v${CLAUDE_VERSION}/node_modules/${CLAUDE_PACKAGE}/package.json" || {
    warn "Claude Code package.json not found at expected path"
}

# --- Setup workspace directory ---
info "Setting up workspace..."
run_in_rootfs "mkdir -p /root/workspace"
run_in_rootfs "mkdir -p /root/.claude"

# --- Create proot launch helper ---
info "Creating proot launch script..."
cat > "$ROOTFS/usr/local/bin/tcc-proot" << 'PROOTEOF'
#!/bin/sh
# TCC proot launcher
PROOT_DIR="$(dirname "$(readlink -f "$0")")/../../.."
exec proot \
    -r / \
    -b /dev \
    -b /proc \
    -b /sys \
    -b /data \
    -b /sdcard \
    -w /root/workspace \
    "$@"
PROOTEOF
chmod 755 "$ROOTFS/usr/local/bin/tcc-proot"

# --- Cleanup unnecessary files to reduce size ---
info "Cleaning up rootfs to reduce size..."
# Remove package cache
run_in_rootfs "apk cache clean 2>/dev/null || true"
rm -rf "$ROOTFS/var/cache/apk/"*
rm -rf "$ROOTFS/tmp/"*
rm -rf "$ROOTFS/var/log/"*
# Remove QEMU binary from final rootfs
if [ "$NEED_QEMU" -eq 1 ]; then
    rm -f "$ROOTFS/usr/bin/qemu-aarch64-static"
fi

# Show rootfs size before compression
ROOTFS_SIZE=$(du -sh "$ROOTFS" | awk '{print $1}')
info "Rootfs size before compression: $ROOTFS_SIZE"

# --- Create tar archive ---
info "Creating rootfs archive..."
TAR_FILE="$TMPDIR/rootfs.tar"
cd "$ROOTFS"
tar --owner=0 --group=0 --numeric-owner \
    -cf "$TAR_FILE" .
cd -

# --- Compress to tgz ---
mkdir -p "$OUTPUT_DIR"
OUTPUT_FILE="$OUTPUT_DIR/rootfs.tgz"
info "Compressing to tgz..."
gzip -9 -f "$TAR_FILE"
mv "$TAR_FILE.gz" "$OUTPUT_FILE"

# --- Done ---
FINAL_SIZE=$(ls -lh "$OUTPUT_FILE" | awk '{print $5}')
info "============================================"
info "Rootfs built successfully!"
info "  Output: $OUTPUT_FILE"
info "  Size:   $FINAL_SIZE"
info "  Alpine: $ALPINE_VERSION ($ALPINE_ARCH)"
info "  Node:   $(run_in_rootfs 'node --version')"
info "  Claude: $CLAUDE_VERSION"
info "============================================"
