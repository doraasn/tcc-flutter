#!/bin/bash
set -eo pipefail
PREFIX=/data/data/com.termux/files/usr
BASE="$(cd "$(dirname "$0")" && pwd)"
OUTPUT="$BASE/assets/termux-bundle.tar"

# 创建临时目录，复制文件并修复权限，确保打包后解压即可用
TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

echo "准备文件（修复权限）..."
mkdir -p "$TMPDIR"/{bin,lib,glibc/lib,tmp}

# 复制关键二进制并设置 755（-L 解引用符号链接）
for f in bash node npm npx env ln cp mv rm mkdir ls cat grep id whoami uname \
         clear sh apt apt-get apt-cache dpkg dpkg-deb patchelf; do
  [ -e "$PREFIX/bin/$f" ] && cp -aL "$PREFIX/bin/$f" "$TMPDIR/bin/" 2>/dev/null
  [ -f "$TMPDIR/bin/$f" ] && chmod 755 "$TMPDIR/bin/$f"
done

# 复制 node_modules
cp -a "$PREFIX/lib/node_modules" "$TMPDIR/lib/" 2>/dev/null || true
# 复制 so 文件
cp -a "$PREFIX/lib/"*.so* "$TMPDIR/lib/" 2>/dev/null || true

# 复制 etc
mkdir -p "$TMPDIR/etc"
for d in tls apt; do [ -d "$PREFIX/etc/$d" ] && cp -a "$PREFIX/etc/$d" "$TMPDIR/etc/"; done
for f in profile bash.bashrc resolv.conf hosts; do [ -f "$PREFIX/etc/$f" ] && cp -a "$PREFIX/etc/$f" "$TMPDIR/etc/"; done

# 复制 glibc 库并设置 755
for f in ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libpthread.so.0 librt.so.1 libdl.so.2; do
  [ -f "$PREFIX/glibc/lib/$f" ] && cp -a "$PREFIX/glibc/lib/$f" "$TMPDIR/glibc/lib/" && chmod 755 "$TMPDIR/glibc/lib/$f"
done

# 确保所有文件可读可执行
chmod -R 755 "$TMPDIR"

echo "打包 Termux 环境..."
(cd "$TMPDIR" && tar -cf "$OUTPUT" --owner=0 --group=0 \
  bin/bash bin/node bin/npm bin/npx bin/env bin/ln bin/cp bin/mv bin/rm \
  bin/mkdir bin/ls bin/cat bin/grep bin/id bin/whoami bin/uname \
  bin/clear bin/sh \
  bin/apt bin/apt-get bin/apt-cache bin/dpkg bin/dpkg-deb \
  bin/patchelf \
  lib/node_modules \
  lib/*.so* \
  etc/tls etc/apt etc/profile etc/bash.bashrc \
  etc/resolv.conf etc/hosts \
  glibc/lib/ld-linux-aarch64.so.1 glibc/lib/libc.so.6 glibc/lib/libm.so.6 \
  glibc/lib/libpthread.so.0 glibc/lib/librt.so.1 glibc/lib/libdl.so.2 \
  tmp)

echo "完成: $(ls -lh $OUTPUT | awk '{print $5}')"
echo "将在 build.py 中 gzip 压缩"
