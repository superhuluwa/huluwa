#!/usr/bin/env bash
# Start the enrolled Debian Docker builder without compiling an APK.
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_DIR"
if [ ! -f "${HULUWA_ANDROID_AGENT_CONFIG:-$PROJECT_DIR/data/debian-builder/config.json}" ]; then
  printf '%s\n' '尚未配置 Debian 打包节点，请先按 android-builder/README.debian.md 完成部署。' >&2
  exit 1
fi
docker compose -f android-builder/compose.debian.yaml up -d --no-build
