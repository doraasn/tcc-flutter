#!/bin/bash
# Build Alpine rootfs for TCC
# Creates a minimal Alpine Linux environment with Node.js and Claude Code
set -eo pipefail

ALPINE_VERSION="3.19"
ALPINE_ARCH="aarch64"
CLAUDE_VERSION="2.1.153"
OUTPUT_DIR="$(cd "$(dirname "$0")" && pwd)/../assets/core"

echo "=== Building Alpine rootfs for TCC ==="

TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

# Download Alpine minirootfs
echo "Downloading Alpine $ALPINE_VERSION rootfs..."
cd "$TMPDIR"
curl -sL "https://dl-cdn.alpinelinux.org/alpine/v$ALPINE_VERSION/releases/$ALPINE_ARCH/alpine-minirootfs-$ALPINE_VERSION.0-$ALPINE_ARCH.tar.gz" -o rootfs.tar.gz

# Extract
echo "Extracting rootfs..."
mkdir -p rootfs
tar xzf rootfs.tar.gz -C rootfs

# Setup DNS
mkdir -p rootfs/etc
cat > rootfs/etc/resolv.conf << 'EOF'
nameserver 8.8.8.8
nameserver 8.8.4.4
EOF

cat > rootfs/etc/hosts << 'EOF'
127.0.0.1 localhost
::1 localhost
EOF

# Setup proot environment
echo "Setting up proot environment..."
PROOT_CONF="$TMPDIR/proot.conf"
cat > "$PROOT_CONF" << EOF
# PRoot configuration for TCC
path /usr/bin /bin
path /usr/bin/env /bin/env
EOF

# Install packages via proot
echo "Installing Node.js and npm..."
proot -r rootfs -b /dev -b /proc -b /sys -w /root \
    /bin/sh -c "apk add --no-cache bash nodejs npm git curl"

# Install Claude Code
echo "Installing Claude Code $CLAUDE_VERSION..."
mkdir -p rootfs/root/.tcc/versions/v$CLAUDE_VERSION
proot -r rootfs -b /dev -b /proc -b /sys -w /root \
    /bin/sh -c "npm install -g @anthropic-ai/claude-code@$CLAUDE_VERSION --prefix /root/.tcc/versions/v$CLAUDE_VERSION"

# Create current symlink
cd rootfs/root/.tcc/versions
ln -sf v$CLAUDE_VERSION current
cd -

# Setup workspace
mkdir -p rootfs/root/workspace

# Create tar
echo "Creating rootfs archive..."
mkdir -p "$OUTPUT_DIR"
cd rootfs
tar -cf "$OUTPUT_DIR/rootfs.tar" --owner=0 --group=0 .
cd ..

# Compress
gzip -9 "$OUTPUT_DIR/rootfs.tar"
mv "$OUTPUT_DIR/rootfs.tar.gz" "$OUTPUT_DIR/rootfs.tgz"

echo "=== Rootfs built successfully ==="
echo "Output: $OUTPUT_DIR/rootfs.tgz"
ls -lh "$OUTPUT_DIR/rootfs.tgz"
