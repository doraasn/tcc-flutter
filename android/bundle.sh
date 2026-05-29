#!/bin/bash
set -eo pipefail
PREFIX=/data/data/com.termux/files/usr
OUTPUT="$(dirname "$0")/assets/termux-bundle.tar.gz"

echo "打包 Termux 环境..."
(cd "$PREFIX" && tar -ch \
  bin/bash bin/node bin/npm bin/npx bin/claude bin/env bin/ln bin/cp bin/mv bin/rm \
  bin/mkdir bin/ls bin/cat bin/grep bin/id bin/whoami bin/uname \
  bin/clear bin/sh \
  bin/apt bin/apt-get bin/apt-cache bin/dpkg bin/dpkg-deb \
  lib/node_modules \
  lib/*.so* \
  etc/tls etc/apt etc/profile etc/bash.bashrc \
  tmp) | gzip -9 > "$OUTPUT"

echo "完成: $(ls -lh $OUTPUT | awk '{print $5}')"
