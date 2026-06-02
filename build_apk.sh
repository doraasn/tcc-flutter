#!/bin/bash
# TCC APK Build - convenience wrapper
# Delegates to scripts/build_apk.sh
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$SCRIPT_DIR/scripts/build_apk.sh" "$@"
